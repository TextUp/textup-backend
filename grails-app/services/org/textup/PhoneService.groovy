package org.textup

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import java.util.concurrent.Future
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Propagation
import org.textup.action.*
import org.textup.annotation.*
import org.textup.rest.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*

@GrailsTypeChecked
@Transactional
class PhoneService {

    CallService callService
    MediaService mediaService
    OwnerPolicyService ownerPolicyService
    PhoneActionService phoneActionService

    // `PhoneActionService` called in this service requires that even newly-created phones are
    // immediately findable via id. Therefore, this method requires a new transaction such that
    // after returning the newly-created phone will already have been persisted to the db.
    // The downside of this method is that we'll have a few more orphan rows in the phone id for
    // empty phones pointing to nonexistent staff members.
    // [NOTE] only external method calls heed annotations
    // [NOTE] must NOT return a domain object because the session for this new transaction will be
    // closed and the returned object will be detached. Return an ID instead to allow re-fetching
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    Result<Long> tryFindAnyIdOrCreateImmediatelyForOwner(Long ownerId, PhoneOwnershipType type) {
        Phone p1 = IOCUtils.phoneCache.findPhone(ownerId, type, true)
        if (p1) {
            IOCUtils.resultFactory.success(p1.id)
        }
        else {
            Phone.tryCreate(ownerId, type)
                .then { Phone p2 -> IndividualPhoneRecord.tryCreate(p2).curry(p2) }
                .then { Phone p2, IndividualPhoneRecord ipr1 ->
                    ipr1.name = PhoneUtils.CUSTOMER_SUPPORT_NAME
                    ipr1.mergeNumber(PhoneUtils.CUSTOMER_SUPPORT_NUMBER, 0)
                    DomainUtils.trySave(ipr1).curry(p2)
                }
                .then { Phone p2 ->
                    // associate owner with newly-created phone in the cache
                    IOCUtils.phoneCache.updateOwner(ownerId, type, p2.id)
                    IOCUtils.resultFactory.success(p2.id, ResultStatus.CREATED)
                }
        }
    }

    // [NOTE] the isolation level set on the very top-most `@Transactional` declaration is what matters
    // That being said, we also set the isolation level here in case we call this service on its own
    // not in the context of a controller (with its controller-started transaction) to ensure that
    // the new `Phone` creation works in both scenarios
    @Transactional(isolation = Isolation.READ_COMMITTED)
    Result<Phone> tryUpdate(Phone p1, TypeMap body, Long staffId, String timezone) {
        Future<?> future
        tryHandlePhoneActionsImmediatelyAndRefresh(p1, body)
            .then { mediaService.tryCreateOrUpdate(p1, body, true) }
            .then { Future<?> fut1 ->
                future = fut1
                tryUpdateOwnerPolicy(p1, staffId, body.typedList(Map, "policies"), timezone)
            }
            .then { trySetFields(p1, body) }
            .then {
                tryRequestVoicemailGreetingCall(p1, body.string("requestVoicemailGreetingCall"))
            }
            .then { DomainUtils.trySave(p1) }
            .ifFailAndPreserveError { future?.cancel(true) }
    }

    // Helpers
    // -------

    // Must be an admin to perform phone actions
    protected Result<Void> tryHandlePhoneActionsImmediatelyAndRefresh(Phone p1, TypeMap body) {
        if (phoneActionService.hasActions(body)) {
            AuthUtils.tryGetActiveAuthUser()
                .then { Staff authUser -> AuthUtils.isAllowed(authUser.status == StaffStatus.ADMIN) }
                .then { phoneActionService.tryHandleActions(p1.id, body) }
                .then {
                    // refresh in case we made db updates in the phoneActionService
                    // [NOTE] this means that this helper method needs to be called first
                    // because all changes to the phone object in this session will be discarded
                    // as the phone object is effectively refetched from the db
                    // [NOTE] to make the new changes getable means that that the isolation level
                    // for THIS transaction (not the nested transaction) needs to be `READ_COMMITTED`.
                    // Because transactions inherit by default, this means that the very top level
                    // transaction declaration in the CONTROLLER needs to be `READ_COMMITTED`
                    p1.refresh()
                    Result.void()
                }
        }
        else { Result.void() }
    }

    protected Result<Phone> trySetFields(Phone p1, TypeMap body) {
        p1.with {
            if (body.awayMessage) awayMessage = body.awayMessage
            if (body.voice) voice = body.enum(VoiceType, "voice")
            if (body.language) language = body.enum(VoiceLanguage, "language")
            if (body.boolean("useVoicemailRecordingIfPresent") != null) {
                p1.useVoicemailRecordingIfPresent = body.boolean("useVoicemailRecordingIfPresent")
            }
            if (body.boolean("allowSharingWithOtherTeams") != null) {
                p1.owner.allowSharingWithOtherTeams = body.boolean("allowSharingWithOtherTeams")
            }
        }
        DomainUtils.trySave(p1)
    }

    protected Result<Phone> tryUpdateOwnerPolicy(Phone p1, Long sId, Collection<Map> policies,
        String timezone) {

        Map oInfo = policies?.find { Map m -> TypeMap.create(m).long("staffId") == sId }
        if (oInfo) {
            OwnerPolicies.tryFindOrCreateForOwnerAndStaffId(p1.owner, sId)
                .then { OwnerPolicy op1 ->
                    ownerPolicyService.tryUpdate(op1, TypeMap.create(oInfo), timezone)
                }
                .then { DomainUtils.trySave(p1) }
        }
        else { DomainUtils.trySave(p1) }
    }

    protected Result<?> tryRequestVoicemailGreetingCall(Phone p1, String numToCall) {
        if (numToCall) {
            AuthUtils.tryGetActiveAuthUser()
                .then { Staff authUser ->
                    tryGetGreetingCallNum(numToCall, authUser.personalNumber)
                }
                .then { PhoneNumber toNum ->
                    callService.start(p1.number,
                        [toNum],
                        CallTwiml.infoForRecordVoicemailGreeting(),
                        p1.customAccountId)
                }
        }
        else { Result.void() }
    }

    protected Result<PhoneNumber> tryGetGreetingCallNum(String possibleNum, PhoneNumber authNum) {
        possibleNum == "true" ?
            IOCUtils.resultFactory.success(authNum) :
            PhoneNumber.tryCreate(possibleNum)
    }
}
