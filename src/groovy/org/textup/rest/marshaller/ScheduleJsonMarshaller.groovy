package org.textup.rest.marshaller

import grails.compiler.GrailsTypeChecked
import javax.servlet.http.HttpServletRequest
import org.joda.time.DateTime
import org.textup.*
import org.textup.rest.*
import org.textup.type.LogLevel
import org.textup.util.*
import org.textup.validator.LocalInterval

@GrailsTypeChecked
class ScheduleJsonMarshaller extends JsonNamedMarshaller {
    static final Closure marshalClosure = { Schedule sched1 ->
        Map json = [:]
        RequestUtils.tryGetFromRequest(RequestUtils.TIMEZONE)
            .then { String tz = null ->
                json.with {
                    timezone = tz
                    nextAvailable = sched1.nextAvailable(tz)
                    nextUnavailable = sched1.nextUnavailable(tz)
                }
            }
            .ifFail("request", LogLevel.DEBUG) {
                json.with {
                    nextAvailable = sched1.nextAvailable()
                    nextUnavailable = sched1.nextUnavailable()
                }
            }
        json.with {
            id = sched1.id
            isAvailableNow = sched1.isAvailableNow()
            manual = sched1.manual
            manualIsAvailable = sched1.manualIsAvailable
        }
        json.putAll(sched1.getAllAsLocalIntervals(tz))
        json
    }

    ScheduleJsonMarshaller() {
        super(Schedule, marshalClosure)
    }
}
