package org.textup.rest.marshaller

import grails.compiler.GrailsCompileStatic
import javax.servlet.http.HttpServletRequest
import org.joda.time.DateTime
import org.textup.*
import org.textup.rest.*
import org.textup.type.LogLevel
import org.textup.validator.LocalInterval

@GrailsCompileStatic
class ScheduleJsonMarshaller extends JsonNamedMarshaller {
    static final Closure marshalClosure = { Schedule sched ->
        String timezone = null
        Helpers.<String>tryGetFromRequest(Constants.REQUEST_TIMEZONE)
            .logFail("ScheduleJsonMarshaller: no available request", LogLevel.DEBUG)
            .then { String tz -> timezone = tz }

        Map json = [:]
        json.with {
            id = sched.id
            isAvailableNow = sched.isAvailableNow()
        }
        sched.nextAvailable(timezone).thenEnd({ DateTime dt -> json.nextAvailable = dt })
        sched.nextUnavailable(timezone).thenEnd({ DateTime dt -> json.nextUnavailable = dt })
        // also return local intervals if a weekly schedule
        if (sched.instanceOf(WeeklySchedule)) {
            WeeklySchedule.get(sched.id)?.getAllAsLocalIntervals(timezone).each {
                String day, List<LocalInterval> intervals ->
                json["${day}"] = intervals.collect(Helpers.&printLocalInterval)
            }
        }
        json
    }

    ScheduleJsonMarshaller() {
        super(Schedule, marshalClosure)
    }
}
