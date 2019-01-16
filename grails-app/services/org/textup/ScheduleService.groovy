package org.textup

import grails.transaction.Transactional
import grails.compiler.GrailsTypeChecked

@GrailsTypeChecked
@Transactional
class ScheduleService {

    Result<Schedulable> update(Schedulable sched1, TypeMap body, String timezone) {


        // TODO
        // if (body.schedule instanceof Map && s1.schedule.instanceOf(WeeklySchedule)) {
        //     WeeklySchedule wSched = WeeklySchedule.get(s1.schedule.id)
        //     if (!wSched) {
        //         return IOCUtils.resultFactory.failWithCodeAndStatus("staffService.fillStaffInfo.scheduleNotFound",
        //             ResultStatus.UNPROCESSABLE_ENTITY, [s1.schedule.id, s1.id])
        //     }
        //     Result<Schedule> res = wSched.updateWithIntervalStrings(body.schedule as Map, timezone)
        //     if (!res.success) {
        //         return IOCUtils.resultFactory.failWithResultsAndStatus([res], res.status)
        //     }
        // }



    }

    // Helpers
    // -------

    // TODO
    protected def trySetFields(Schedulable sched1, TypeMap body) {
        sched1.with {
            if (body.manualSchedule != null) manualSchedule = body.manualSchedule
            if (body.isAvailable != null) isAvailable = body.isAvailable
        }

        DomainUtils.trySave(sched1)
    }
}
