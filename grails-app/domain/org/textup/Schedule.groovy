package org.textup

import groovy.transform.EqualsAndHashCode
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.textup.types.ScheduleStatus
import org.textup.validator.ScheduleChange
import grails.compiler.GrailsCompileStatic

@GrailsCompileStatic
@EqualsAndHashCode
class Schedule {

    ResultFactory resultFactory

    static transients = ["resultFactory"]
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
        resultFactory.success(new ScheduleChange(type:ScheduleStatus.AVAILABLE,
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
