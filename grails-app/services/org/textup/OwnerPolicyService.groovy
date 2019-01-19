package org.textup

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional

@GrailsTypeChecked
@Transactional
class OwnerPolicyService {

    ScheduleService scheduleService

    Result<OwnerPolicy> update(OwnerPolicy op1, TypeMap body, String timezone) {
        trySetFields(op1, body)
            .then { scheduleService.update(op1.schedule, body.typeMapNoNull("schedule"), timezone) }
            .then { DomainUtils.trySave(op1) }
    }

    // Helpers
    // -------

    protected Result<OwnerPolicy> trySetFields(OwnerPolicy op1, TypeMap body) {
        op1.with {
            if (body.level) level = body.enum(NotificationLevel, "level")
        }
        DomainUtils.trySave(op1)
    }
}
