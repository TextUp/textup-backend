package org.textup

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import java.util.concurrent.Future
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

    Result<Phone> update(Phone p1, TypeMap body, String timezone) {
        Future<?> future
        mediaService.tryCreateOrUpdate(p1, body, true)
            .then { Future<?> fut1 ->
                future = fut1
                phoneActionService.tryHandleActions(p1, body)
            }
            .then { tryUpdateOwnerPolicy(p1, body.typeMapNoNull("self"), timezone) }
            .then { trySetFields(p1, body) }
            .then {
                tryRequestVoicemailGreetingCall(p1, body.string("requestVoicemailGreetingCall"))
            }
            .then { DomainUtils.trySave(p1) }
            .ifFail { Result<?> failRes ->
                future?.cancel(true)
                failRes
            }
    }

    // Helpers
    // -------

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

    protected Result<Phone> tryUpdateOwnerPolicy(Phone p1, TypeMap oInfo, String timezone) {
        if (oInfo) {
            AuthUtils.tryGetAuthId()
                .then { Long authId ->
                    OwnerPolicies.tryFindOrCreateForOwnerAndStaffId(p1.owner, authId)
                }
                .then { OwnerPolicy op1 -> ownerPolicyService.update(op1, oInfo, timezone) }
                .then { DomainUtils.trySave(p1) }
        }
        else { DomainUtils.trySave(p1) }
    }

    protected Result<?> tryRequestVoicemailGreetingCall(Phone p1, String numToCall) {
        if (numToCall) {
            AuthUtils.tryGetAuthUser()
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
