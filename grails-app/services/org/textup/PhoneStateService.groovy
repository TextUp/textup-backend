package org.textup

import com.twilio.rest.api.v2010.account.IncomingPhoneNumber
import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional

@GrailsTypeChecked
@Transactional
class PhoneStateService {

    NumberService numberService

    Result<Phone> handleActions(Phone p1, Map body) {
        if (body.doPhoneActions) {
            if (p1.customAccountId) {
                return IOCUtils.resultFactory.failWithCodeAndStatus(
                    "phoneService.handlePhoneActions.disabledWhenDebugging",
                    ResultStatus.FORBIDDEN, [p1.number.prettyPhoneNumber])
            }
            ActionContainer ac1 = new ActionContainer<>(PhoneAction, body.doPhoneActions)
            if (!ac1.validate()) {
                return IOCUtils.resultFactory.failWithValidationErrors(ac1.errors)
            }
            ResultGroup<?> resGroup = new ResultGroup<>()
            ac1.actions.each { PhoneAction a1 ->
                switch (a1) {
                    case Constants.PHONE_ACTION_DEACTIVATE:
                        resGroup << deactivatePhone(p1)
                        break
                    case Constants.PHONE_ACTION_TRANSFER:
                        resGroup << transferPhone(p1, a1.id, a1.typeAsEnum)
                        break
                    case Constants.PHONE_ACTION_NEW_NUM_BY_NUM:
                        resGroup << updatePhoneForNumber(p1, a1.phoneNumber)
                        break
                    default: // Constants.PHONE_ACTION_NEW_NUM_BY_ID
                        resGroup << updatePhoneForApiId(p1, a1.numberId)
                }
            }
            if (resGroup.anyFailures) {
                return IOCUtils.resultFactory.failWithGroup(resGroup)
            }
        }
        IOCUtils.resultFactory.success(p1)
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
