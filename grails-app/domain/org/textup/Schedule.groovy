package org.textup

import groovy.transform.EqualsAndHashCode
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.textup.type.ScheduleStatus
import org.textup.util.*
import org.textup.validator.ScheduleChange
import grails.compiler.GrailsTypeChecked

@GrailsTypeChecked
@EqualsAndHashCode
class Schedule implements WithId, Saveable {

    static constraints = {
    }

    // Availability
    // ------------

    boolean isAvailableAt(DateTime dt) {
        IOCUtils.resultFactory.success(false)
    }
    boolean isAvailableNow() {
        isAvailableAt(DateTime.now(DateTimeZone.UTC))
    }

    // Status changes
    // --------------

    Result<ScheduleChange> nextChange(String timezone=null) {
        IOCUtils.resultFactory.success(new ScheduleChange(type:ScheduleStatus.AVAILABLE,
            when:DateTime.now(DateTimeZone.UTC).minusDays(1)))
    }
    Result<DateTime> nextAvailable(String timezone=null) {
        IOCUtils.resultFactory.success(DateTime.now(DateTimeZone.UTC).minusDays(1))
    }
    Result<DateTime> nextUnavailable(String timezone=null) {
        IOCUtils.resultFactory.success(DateTime.now(DateTimeZone.UTC).minusDays(1))
    }

    // Modify the Schedule
    // -------------------

    Result<Schedule> update(Map params) {
        IOCUtils.resultFactory.success(this)
    }
}
