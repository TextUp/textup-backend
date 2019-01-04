package org.textup

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*
import org.textup.validator.action.*

@GrailsTypeChecked
@Transactional
class NotificationSettingsService {

    Result<Void> handleActions(Phone p1, Long recordId, Object rawActions) {
        ActionContainer ac1 = new ActionContainer<>(NotificationPolicyAction, rawActions)
        if (!ac1.validate()) {
            return IOCUtils.resultFactory.failWithValidationErrors(ac1.errors)
        }
        ResultGroup<?> resGroup = new ResultGroup<>()
        ac1.actions.each { NotificationPolicyAction a1 ->
            NotificationPolicy np1 = p1.owner.getOrCreatePolicyForStaff(a1.id)
            switch (a1) {
                case Constants.NOTIFICATION_ACTION_DEFAULT:
                    np1.level = a1.levelAsEnum
                    break
                case Constants.NOTIFICATION_ACTION_ENABLE:
                    np1.enable(recordId)
                    break
                default: // Constants.NOTIFICATION_ACTION_DISABLE
                    np1.disable(recordId)
            }
            if (!np1.save()) {
                resGroup << IOCUtils.resultFactory.failWithValidationErrors(np1.errors)
            }
        }
        if (resGroup.anyFailures) {
            IOCUtils.resultFactory.failWithGroup(resGroup)
        }
        else { IOCUtils.resultFactory.success() }
    }

    Result<NotificationPolicy> update(NotificationPolicy np1, Map body, String timezone) {
        if (TypeConversionUtils.to(Boolean, body.useStaffAvailability) != null) {
            np1.useStaffAvailability = TypeConversionUtils.to(Boolean, body.useStaffAvailability)
        }
        if (TypeConversionUtils.to(Boolean, body.manualSchedule) != null) {
            np1.manualSchedule = TypeConversionUtils.to(Boolean, body.manualSchedule)
        }
        if (TypeConversionUtils.to(Boolean, body.isAvailable) != null) {
            np1.isAvailable = TypeConversionUtils.to(Boolean, body.isAvailable)
        }
        if (body.schedule instanceof Map) {
            Result<Schedule> res = np1.updateSchedule(body.schedule as Map, timezone)
            if (!res.success) { return res }
        }
        if (np1.save()) {
            IOCUtils.resultFactory.success(np1)
        }
        else { IOCUtils.resultFactory.failWithValidationErrors(np1.errors) }
    }
}
