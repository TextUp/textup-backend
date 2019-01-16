package org.textup.action

import com.twilio.rest.api.v2010.account.IncomingPhoneNumber
import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional

@GrailsTypeChecked
@Transactional
class PhoneActionService implements HandlesActions<Phone, Phone> {

    NumberService numberService

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
                ResultGroup<?> resGroup = new ResultGroup<>()
                actions.each { PhoneAction a1 ->
                    switch (a1) {
                        case PhoneAction.DEACTIVATE:
                            resGroup << deactivatePhone(p1)
                            break
                        case PhoneAction.TRANSFER:
                            resGroup << transferPhone(p1, a1.id, a1.buildPhoneOwnershipType())
                            break
                        case PhoneAction.NEW_NUM_BY_NUM:
                            resGroup << updatePhoneForNumber(p1, a1.buildPhoneNumber())
                            break
                        default: // PhoneAction.NEW_NUM_BY_ID
                            resGroup << updatePhoneForApiId(p1, a1.numberId)
                    }
                }
                resGroup.toEmptyResult(false)
            }
            .then { DomainUtils.trySave(p1) }
    }

    // Helpers
    // -------

    protected Result<Phone> deactivatePhone(Phone p1) {
        String oldApiId = p1.apiId
        p1.deactivate()
        if (!p1.validate()) {
            return IOCUtils.resultFactory.failWithValidationErrors(p1.errors)
        }
        if (oldApiId) {
            numberService
                .freeExistingNumberToInternalPool(oldApiId)
                .then { IOCUtils.resultFactory.success(p1) }
        }
        else { IOCUtils.resultFactory.success(p1) }
    }

    protected Result<Phone> transferPhone(Phone p1, Long id, PhoneOwnershipType type) {

        // TODO use phoneCache.updateOwner()
        // p1
        //     .transferTo(id, type)
        //     .then { IOCUtils.resultFactory.success(p1) }
        // // From phone
        // PhoneOwnership own = this.owner
        // Phone otherPhone = (type == PhoneOwnershipType.INDIVIDUAL) ?
        //     Staff.get(id)?.phoneWithAnyStatus : Team.get(id)?.phoneWithAnyStatus
        // // if other phone is present, copy this owner over
        // if (otherPhone?.owner) {
        //     PhoneOwnership otherOwn = otherPhone.owner
        //     otherOwn.type = own.type
        //     otherOwn.ownerId = own.ownerId
        //     if (!otherOwn.save()) {
        //         return IOCUtils.resultFactory.failWithValidationErrors(otherOwn.errors)
        //     }
        // }
        // // then associate this phone with new owner
        // own.type = type
        // own.ownerId = id
        // if (own.save()) {
        //     IOCUtils.resultFactory.success(own)
        // }
        // else { IOCUtils.resultFactory.failWithValidationErrors(own.errors) }
    }

    protected Result<Phone> updatePhoneForNumber(Phone p1, PhoneNumber pNum) {
        if (!pNum.validate()) {
            return IOCUtils.resultFactory.failWithValidationErrors(pNum.errors)
        }
        if (pNum.number == p1.numberAsString) {
            return IOCUtils.resultFactory.success(p1)
        }
        if (Utils.<Boolean>doWithoutFlush({ Phone.countByNumberAsString(pNum.number) > 0 })) {
            return IOCUtils.resultFactory.failWithCodeAndStatus("phoneService.changeNumber.duplicate",
                ResultStatus.UNPROCESSABLE_ENTITY)
        }
        numberService.changeForNumber(pNum)
            .then({ IncomingPhoneNumber iNum -> numberService.updatePhoneWithNewNumber(iNum, p1) })
    }

    protected Result<Phone> updatePhoneForApiId(Phone p1, String apiId) {
        if (apiId == p1.apiId) {
            return IOCUtils.resultFactory.success(p1)
        }
        if (Utils.<Boolean>doWithoutFlush({ Phone.countByApiId(apiId) > 0 })) {
            return IOCUtils.resultFactory.failWithCodeAndStatus("phoneService.changeNumber.duplicate",
                ResultStatus.UNPROCESSABLE_ENTITY)
        }
        numberService.changeForApiId(apiId)
            .then({ IncomingPhoneNumber iNum -> numberService.updatePhoneWithNewNumber(iNum, p1) })
    }
}
