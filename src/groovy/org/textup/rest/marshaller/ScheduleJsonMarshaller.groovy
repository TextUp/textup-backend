package org.textup.rest.marshaller

import grails.compiler.GrailsTypeChecked
import javax.servlet.http.HttpServletRequest
import org.joda.time.DateTime
import org.textup.*
import org.textup.rest.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*

@GrailsTypeChecked
class ScheduleJsonMarshaller extends JsonNamedMarshaller {

    static final Closure marshalClosure = { ReadOnlySchedule sched1 ->
        Map json = [:]
        json.with {
            id                = sched1.id
            isAvailableNow    = sched1.isAvailableNow()
            manual            = sched1.manual
            manualIsAvailable = sched1.manualIsAvailable
        }

        RequestUtils.tryGet(RequestUtils.TIMEZONE)
            .then { String tz ->
                json.with {
                    nextAvailable   = sched1.nextAvailable(tz)
                    nextUnavailable = sched1.nextUnavailable(tz)
                    timezone        = tz
                }
                json.putAll(sched1.getAllAsLocalIntervals(tz))
            }
            .ifFail {
                json.with {
                    nextAvailable   = sched1.nextAvailable()
                    nextUnavailable = sched1.nextUnavailable()
                }
            }

        json
    }

    ScheduleJsonMarshaller() {
        super(ReadOnlySchedule, marshalClosure)
    }
}
