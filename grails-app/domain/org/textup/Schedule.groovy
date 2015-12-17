package org.textup

import groovy.transform.EqualsAndHashCode
import org.joda.time.DateTime
import org.joda.time.DateTimeZone

@EqualsAndHashCode
class Schedule {

    static constraints = {
    }

    /*
	Has many:
	*/

    ////////////////////
    // Helper methods //
    ////////////////////

    /*
    Availability
     */
    boolean isAvailableAt(DateTime dt) { Result.success(false) }
    boolean isAvailableNow() { isAvailableAt(DateTime.now(DateTimeZone.UTC)) }

    /*
    Status changes
     */
    Result<ScheduleChange> nextChange(String timezone=null) {
        Result.success(new ScheduleChange(type:Constants.SCHEDULE_AVAILABLE,
            when:DateTime.now(DateTimeZone.UTC).minusDays(1)))
    }
    Result<DateTime> nextAvailable(String timezone=null) {
        Result.success(DateTime.now(DateTimeZone.UTC).minusDays(1))
    }
    Result<DateTime> nextUnavailable(String timezone=null) {
        Result.success(DateTime.now(DateTimeZone.UTC).minusDays(1))
    }

    /*
    Operations that modify the Schedule
     */
    Result<Schedule> update(Map params) { Result.success(this) }

    /////////////////////
    // Property Access //
    /////////////////////

}
