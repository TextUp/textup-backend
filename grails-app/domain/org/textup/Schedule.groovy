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
class Schedule implements WithId, Saveable<Schedule> {

    boolean manualSchedule = true
    boolean manualIsAvailable = true

    String sunday = ""
    String monday = ""
    String tuesday = ""
    String wednesday = ""
    String thursday = ""
    String friday = ""
    String saturday = ""

    static constraints = {
        sunday validator:{ String val ->
            if (!ScheduleUtils.validateIntervalsString(val)) { ["invalid"] }
        }
        monday validator:{ String val ->
            if (!ScheduleUtils.validateIntervalsString(val)) { ["invalid"] }
        }
        tuesday validator:{ String val ->
            if (!ScheduleUtils.validateIntervalsString(val)) { ["invalid"] }
        }
        wednesday validator:{ String val ->
            if (!ScheduleUtils.validateIntervalsString(val)) { ["invalid"] }
        }
        thursday validator:{ String val ->
            if (!ScheduleUtils.validateIntervalsString(val)) { ["invalid"] }
        }
        friday validator:{ String val ->
            if (!ScheduleUtils.validateIntervalsString(val)) { ["invalid"] }
        }
        saturday validator:{ String val ->
            if (!ScheduleUtils.validateIntervalsString(val)) { ["invalid"] }
        }
    }

    static Result<Schedule> tryCreate() {

    }

    // Methods
    // -------

    boolean isAvailableNow() {
        isAvailableAt(DateTimeUtils.now())
    }

    Result<DateTime> nextAvailable(String timezone=null) {
        IOCUtils.resultFactory.success(DateTimeUtils.now().minusDays(1))
    }

    Result<DateTime> nextUnavailable(String timezone=null) {
        IOCUtils.resultFactory.success(DateTimeUtils.now().minusDays(1))
    }
}
