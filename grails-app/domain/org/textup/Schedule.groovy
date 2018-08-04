package org.textup

import groovy.transform.EqualsAndHashCode
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.textup.type.ScheduleStatus
import org.textup.validator.ScheduleChange
import grails.compiler.GrailsCompileStatic

@GrailsCompileStatic
@EqualsAndHashCode
class Schedule {

    static constraints = {
    }

    // Availability
    // ------------

    boolean isAvailableAt(DateTime dt) {
        Helpers.resultFactory.success(false)
    }
    boolean isAvailableNow() {
        isAvailableAt(DateTime.now(DateTimeZone.UTC))
    }

    // Status changes
    // --------------

    Result<ScheduleChange> nextChange(String timezone=null) {
        Helpers.resultFactory.success(new ScheduleChange(type:ScheduleStatus.AVAILABLE,
            when:DateTime.now(DateTimeZone.UTC).minusDays(1)))
    }
    Result<DateTime> nextAvailable(String timezone=null) {
        Helpers.resultFactory.success(DateTime.now(DateTimeZone.UTC).minusDays(1))
    }
    Result<DateTime> nextUnavailable(String timezone=null) {
        Helpers.resultFactory.success(DateTime.now(DateTimeZone.UTC).minusDays(1))
    }

    // Modify the Schedule
    // -------------------

    Result<Schedule> update(Map params) {
        Helpers.resultFactory.success(this)
    }
}
