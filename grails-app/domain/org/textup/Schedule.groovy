package org.textup

import groovy.transform.EqualsAndHashCode
import org.joda.time.DateTime

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
    boolean isAvailableNow() { isAvailableAt(DateTime.now()) }

    /*
    Status changes
     */
    Result<ScheduleChange> nextChange() {
        Result.success(new ScheduleChange(type:Constants.SCHEDULE_AVAILABLE, when:DateTime.now().minusDays(1)))
    }
    Result<DateTime> nextAvailable() {
        Result.success(DateTime.now().minusDays(1))
    }
    Result<DateTime> nextUnavailable() { 
        Result.success(DateTime.now().minusDays(1))
    }

    /*
    Operations that modify the Schedule
     */
    Result<Schedule> update(Map params) { Result.success(this) }
    
    /////////////////////
    // Property Access //
    /////////////////////
    
}
