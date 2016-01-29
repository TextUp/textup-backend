package org.textup

import groovy.transform.EqualsAndHashCode
import org.joda.time.DateTime
import org.joda.time.DateTimeZone

@EqualsAndHashCode
class Schedule {

    def resultFactory

    static constraints = {
    }

    // Availability
    // ------------

    boolean isAvailableAt(DateTime dt) {
        resultFactory.success(false)
    }
    boolean isAvailableNow() {
        isAvailableAt(DateTime.now(DateTimeZone.UTC))
    }

    // Status changes
    // --------------

    Result<ScheduleChange> nextChange(String timezone=null) {
        resultFactory.success(new ScheduleChange(type:ScheduleChange.AVAILABLE,
            when:DateTime.now(DateTimeZone.UTC).minusDays(1)))
    }
    Result<DateTime> nextAvailable(String timezone=null) {
        resultFactory.success(DateTime.now(DateTimeZone.UTC).minusDays(1))
    }
    Result<DateTime> nextUnavailable(String timezone=null) {
        resultFactory.success(DateTime.now(DateTimeZone.UTC).minusDays(1))
    }

    // Modify the Schedule
    // -------------------

    Result<Schedule> update(Map params) {
        resultFactory.success(this)
    }
}
