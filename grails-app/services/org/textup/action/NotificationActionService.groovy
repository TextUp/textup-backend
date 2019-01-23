package org.textup.action

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import org.textup.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*
import org.textup.validator.action.*

@GrailsTypeChecked
@Transactional
class NotificationActionService implements HandlesActions<Tuple<Phone, Long>, Void>{

    @Override
    boolean hasActions(Map body) { !!body.doNotificationActions }

    @Override
    Result<Void> tryHandleActions(Tuple<Phone, Long> phoneAndRecordId, Map body) {
        phoneAndRecordId.checkBothPresent()
            .then { ActionContainer.tryProcess(NotificationAction, body.doNotificationActions) }
            .then { List<NotificationAction> actions ->
                Tuple.split(phoneAndRecordId) { Phone p1, Long recordId ->
                    ResultGroup
                        .collect(actions) { NotificationAction a1 ->
                            OwnerPolicies.tryFindOrCreateForOwnerAndStaffId(p1.owner, a1.id)
                                .then { OwnerPolicy np1 ->
                                    switch (a1) {
                                        case NotificationAction.ENABLE:
                                            np1.enable(recordId)
                                            break
                                        default: // NotificationAction.DISABLE
                                            np1.disable(recordId)
                                    }
                                    DomainUtils.trySave(np1)
                                }
                        }
                        .toEmptyResult(false)
                }
            }
    }
}
