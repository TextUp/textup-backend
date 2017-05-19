package org.textup.rest.marshallers

import grails.plugin.springsecurity.SpringSecurityService
import org.codehaus.groovy.grails.web.mapping.LinkGenerator
import org.codehaus.groovy.grails.web.util.WebUtils
import org.joda.time.DateTime
import org.textup.*
import org.textup.rest.*
import org.textup.validator.LocalInterval
import grails.compiler.GrailsCompileStatic
import javax.servlet.http.HttpServletRequest

@GrailsCompileStatic
class ScheduleJsonMarshaller extends JsonNamedMarshaller {
    static final Closure marshalClosure = { String namespace,
        SpringSecurityService springSecurityService, AuthService authService,
        LinkGenerator linkGenerator, Schedule sched ->

        String timezone = null
        try {
            HttpServletRequest request = WebUtils.retrieveGrailsWebRequest().currentRequest
            timezone = (request.getAttribute("timezone") ?:
                request.getParameter('timezone'))  as String
        }
        catch (IllegalStateException e) {
            log.debug("ScheduleJsonMarshaller: no available request: $e")
        }

        Map json = [:]
        json.with {
            id = sched.id
            isAvailableNow =  sched.isAvailableNow()
        }
        sched.nextAvailable(timezone).then({ DateTime dt ->
            json.nextAvailable = dt
        })
        sched.nextUnavailable(timezone).then({ DateTime dt ->
            json.nextUnavailable = dt
        })
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
