package org.textup.action

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import org.textup.*
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*
import org.textup.validator.action.*

@GrailsTypeChecked
@Transactional
class NotificationActionService implements HandlesActions<Tuple<Phone, Long>, Void>{

    ScheduleService scheduleService

    @Override
    boolean hasActions(Map body) { !!body.doNotificationActions }

    @Override
    Result<Void> tryHandleActions(Tuple<Phone, Long> phoneAndRecordId, Map body) {
        phoneAndRecordId.checkBothPresent()
            .then {
                ActionContainer.tryProcess(NotificationPolicyAction, body.doNotificationActions)
            }
            .then { List<NotificationPolicyAction> actions ->
                Tuple.split(phoneAndRecordId) { Phone p1, Long recordId ->
                    ResultGroup<?> resGroup = new ResultGroup<>()
                    actions.each { NotificationPolicyAction a1 ->
                        NotificationPolicy np1 = p1.owner.getOrCreatePolicyForStaff(a1.id)
                        switch (a1) {
                            case NotificationPolicyAction.DEFAULT:
                                np1.level = a1.buildNotificationLevel()
                                break
                            case NotificationPolicyAction.ENABLE:
                                np1.enable(recordId)
                                break
                            default: // NotificationPolicyAction.DISABLE
                                np1.disable(recordId)
                        }
                        resGroup << DomainUtils.trySave(np1)
                    }
                    resGroup.toEmptyResult(false)
                }
            }
    }
}
