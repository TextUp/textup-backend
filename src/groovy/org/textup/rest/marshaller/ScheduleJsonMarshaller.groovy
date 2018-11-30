package org.textup.rest.marshaller

import grails.compiler.GrailsTypeChecked
import javax.servlet.http.HttpServletRequest
import org.joda.time.DateTime
import org.textup.*
import org.textup.rest.*
import org.textup.type.LogLevel
import org.textup.validator.LocalInterval

@GrailsTypeChecked
class ScheduleJsonMarshaller extends JsonNamedMarshaller {
    static final Closure marshalClosure = { Schedule sched ->
        Map json = [:]

        Result<String> res = Utils.tryGetFromRequest(Constants.REQUEST_TIMEZONE)
            .logFail("ScheduleJsonMarshaller: no available request", LogLevel.DEBUG)
        String tz = res.success ? res.payload : null

        json.with {
            id = sched.id
            isAvailableNow = sched.isAvailableNow()
            timezone = tz
        }
        sched.nextAvailable(tz).thenEnd({ DateTime dt -> json.nextAvailable = dt })
        sched.nextUnavailable(tz).thenEnd({ DateTime dt -> json.nextUnavailable = dt })
        // also return local intervals if a weekly schedule
        if (sched.instanceOf(WeeklySchedule)) {
            WeeklySchedule.get(sched.id)?.getAllAsLocalIntervals(tz).each {
                String day, List<LocalInterval> intervals ->
                json["${day}"] = intervals.collect(DateTimeUtils.&printLocalInterval)
            }
        }
        json
    }

    ScheduleJsonMarshaller() {
        super(Schedule, marshalClosure)
    }
}
