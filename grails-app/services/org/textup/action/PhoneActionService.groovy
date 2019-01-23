package org.textup.action

import com.twilio.rest.api.v2010.account.IncomingPhoneNumber
import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional

@GrailsTypeChecked
@Transactional
class PhoneActionService implements HandlesActions<Phone, Phone> {

    NumberService numberService
    PhoneCache phoneCache

    @Override
    boolean hasActions(Map body) { !!body.doPhoneActions }

    @Override
    Result<Phone> tryHandleActions(Phone p1, Map body) {
        if (p1.customAccountId) {
            return IOCUtils.resultFactory.failWithCodeAndStatus(
                "phoneService.handlePhoneActions.disabledWhenDebugging",
                ResultStatus.FORBIDDEN, [p1.number.prettyPhoneNumber])
        }
        ActionContainer.tryProcess(PhoneAction, body.doPhoneActions)
            .then { List<PhoneAction> actions ->
                ResultGroup
                    .collect(actions) { PhoneAction a1 ->
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
            }
            .then { DomainUtils.trySave(p1) }
    }

    // Helpers
    // -------

    protected Result<Phone> tryDeactivatePhone(Phone p1) {
        p1.tryDeactivate()
            .then { String oldApiId -> tryCleanExistingNumber(p1, oldApiId) }
    }

    protected Result<Phone> tryExchangeOwners(Phone p1, Long ownerId, PhoneOwnershipType type) {
        Phone otherPhone = phoneCache.findPhone(ownerId, type, true)
        if (otherPhone) {
            phoneCache.tryUpdateOwner(otherPhone, p1.owner.ownerId, p1.owner.type)
                .then { phoneCache.tryUpdateOwner(p1, ownerId, type) }
        }
        else { phoneCache.tryUpdateOwner(p1, ownerId, type) }
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
