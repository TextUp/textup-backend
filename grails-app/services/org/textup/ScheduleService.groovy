package org.textup

import grails.transaction.Transactional
import grails.compiler.GrailsTypeChecked

@GrailsTypeChecked
@Transactional
class ScheduleService {

    Result<Schedule> update(Schedule sched1, TypeMap body, String timezone) {
        if (body) {
            trySetFields(sched1, body).then {
                sched1.updateWithIntervalStrings(body, timezone)
            }
        }
        else { IOCUtils.resultFactory.success(sched1) }
    }

    // Helpers
    // -------

    protected Result<Schedule> trySetFields(Schedule sched1, TypeMap body) {
        sched1.with {
            if (body.manualSchedule != null) manualSchedule = body.manualSchedule
            if (body.isAvailable != null) isAvailable = body.isAvailable
        }
        DomainUtils.trySave(sched1)
    }
}
