package org.textup.action

import com.twilio.rest.api.v2010.account.IncomingPhoneNumber
import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import org.springframework.transaction.annotation.Propagation
import org.textup.*
import org.textup.cache.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*
import org.textup.validator.action.*

@GrailsTypeChecked
@Transactional
class PhoneActionService implements HandlesActions<Long, Void> {

    NumberService numberService
    PhoneCache phoneCache

    @Override
    boolean hasActions(Map body) { !!body.doPhoneActions }

    // Because this method makes external number changes and modifies the phone ownership cache
    // we want to persist any db changes made in this method so that the external numbers, the phone
    // cache, and the db state all remain in sync. An example of what will happen if we don't
    // immediately persist the db changes here is: we exchange phone owners and queue up
    // the necessary db changes. These changes aren't flushed to the db yet so if we ask the
    // phone cache for owners BEFORE ths transaction flushes, it'll return the pre-switch values
    // and these incorrect values will persist in the cache. Conversely we also don't want to
    // pre-emptively update the cache in case the larger transaction fails
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Override
    Result<Void> tryHandleActions(Long pId, Map body) {
        Phones.mustFindForId(pId)
            .then { Phone p1 ->
                if (p1.customAccountId) {
                    return IOCUtils.resultFactory.failWithCodeAndStatus(
                        "phoneActionService.disabledWhenDebugging",
                        ResultStatus.FORBIDDEN, [p1.number.prettyPhoneNumber])
                }
                ActionContainer.tryProcess(PhoneAction, body.doPhoneActions).curry(p1)
            }
            .then { Phone p1, List<PhoneAction> actions ->
                ResultGroup
                    .<?>collect(actions) { PhoneAction a1 ->
                        switch (a1) {
                            case PhoneAction.DEACTIVATE:
                                tryDeactivatePhone(p1)
                                break
                            case PhoneAction.TRANSFER:
                                tryExchangeOwners(p1, a1.id, a1.buildPhoneOwnershipType())
                                break
                            case PhoneAction.NEW_NUM_BY_NUM:
                                tryUpdatePhoneForNumber(p1, a1.buildPhoneNumber())
                                break
                            default: // PhoneAction.NEW_NUM_BY_ID
                                tryUpdatePhoneForApiId(p1, a1.numberId)
                        }
                    }
                    .toEmptyResult(false)
                    .curry(p1)
            }
            .then { Phone p1 -> DomainUtils.trySave(p1) }
            .then { Result.void() }
    }

    // Helpers
    // -------

    protected Result<Phone> tryDeactivatePhone(Phone p1) {
        p1.tryDeactivate()
            .then { String oldApiId -> tryCleanExistingNumber(p1, oldApiId) }
    }

    // Exchange then validate so that, when we are updating the cache values, we can be sure
    // that the subsequent transaction flush will happen successfully
    protected Result<Void> tryExchangeOwners(Phone p1, Long ownerId, PhoneOwnershipType type) {
        Long originalOwnerId = p1.owner.ownerId
        PhoneOwnershipType originalType = p1.owner.type
        p1.owner.ownerId = ownerId
        p1.owner.type = type
        DomainUtils.trySave(p1)
            .then {
                Phone otherPhone = phoneCache.findPhone(ownerId, type, true)
                if (otherPhone) {
                    otherPhone.owner.ownerId = originalOwnerId
                    otherPhone.owner.type = originalType
                    DomainUtils.trySave(otherPhone)
                }
                else { Result.void() }
            }
            .then { Phone otherPhone = null ->
                phoneCache.updateOwner(ownerId, type, p1.id)
                if (otherPhone) {
                    phoneCache.updateOwner(originalOwnerId, originalType, otherPhone.id)
                }
                Result.void()
            }
    }

    protected Result<Phone> tryUpdatePhoneForNumber(Phone p1, PhoneNumber pNum) {
        if (pNum.number == p1.numberAsString) {
            IOCUtils.resultFactory.success(p1)
        }
        else {
            DomainUtils.tryValidate(pNum)
                .then {
                    p1.number = pNum
                    DomainUtils.trySave(p1) // uniqueness check for phone number
                }
                .then { numberService.changeForNumber(pNum) }
                .then { String apiId -> p1.tryActivate(pNum, apiId) }
                .then { String oldApiId -> tryCleanExistingNumber(p1, oldApiId) }
        }
    }

    protected Result<Phone> tryUpdatePhoneForApiId(Phone p1, String apiId) {
        if (apiId == p1.apiId) {
            IOCUtils.resultFactory.success(p1)
        }
        else {
            p1.apiId = apiId
            DomainUtils.trySave(p1) // uniqueness check for apiId
                .then { numberService.changeForApiId(apiId) }
                .then { PhoneNumber pNum -> p1.tryActivate(pNum, apiId) }
                .then { String oldApiId -> tryCleanExistingNumber(p1, oldApiId) }
        }
    }

    protected Result<Phone> tryCleanExistingNumber(Phone p1, String oldApiId) {
        if (oldApiId) {
            numberService
                .freeExistingNumberToInternalPool(oldApiId)
                .then { IOCUtils.resultFactory.success(p1) }
        }
        else { IOCUtils.resultFactory.success(p1) }
    }
}
