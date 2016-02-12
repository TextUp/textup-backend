package org.textup.rest.marshallers

import grails.plugin.springsecurity.SpringSecurityService
import org.codehaus.groovy.grails.web.mapping.LinkGenerator
import org.codehaus.groovy.grails.web.util.WebUtils
import org.joda.time.DateTime
import org.textup.*
import org.textup.rest.*
import org.textup.validator.LocalInterval
import grails.compiler.GrailsCompileStatic

@GrailsCompileStatic
class StaffJsonMarshaller extends JsonNamedMarshaller {
    static final Closure marshalClosure = { String namespace,
        SpringSecurityService springSecurityService, AuthService authService,
        LinkGenerator linkGenerator, Staff s1 ->

        Map json = [:]
        json.with {
            id = s1.id
            username = s1.username
            name = s1.name
            email = s1.email
            org = s1.org.id
            orgName = s1.org.name
            status = s1.status.toString()
            if (s1.personalPhoneNumber) {
                personalPhoneNumber = s1.personalPhoneNumber.e164PhoneNumber
            }
            if (s1.phone) {
                phone = s1.phone.number.e164PhoneNumber
                awayMessage = s1.phone.awayMessage
            }
            // manual schedule fields
            manualSchedule = s1.manualSchedule
            if (s1.manualSchedule == true) {
                isAvailable = s1.isAvailable
            }
            isAvailableNow = s1.isAvailableNow()
        }
        json.tags = s1.phone ? s1.phone.getTags() : []
        json.teams = s1.getTeams()
        if (s1.schedule?.instanceOf(WeeklySchedule)) {
            WeeklySchedule wkSched = s1.schedule as WeeklySchedule
            String timezone = WebUtils.retrieveGrailsWebRequest().currentRequest
                .getAttribute("timezone") as String
            Map jsonSched = [:]
            wkSched.getAllAsLocalIntervals(timezone)
                .each { String day, List<LocalInterval> intervals ->
                    jsonSched["${day}"] = intervals.collect { LocalInterval localInt ->
                        Helpers.printLocalInterval(localInt)
                    }
                }
            if (s1.manualSchedule == false) {
                s1.nextAvailable(timezone).then({ DateTime dt ->
                    jsonSched.nextAvailable = dt
                })
                s1.nextUnavailable(timezone).then({ DateTime dt ->
                    jsonSched.nextUnavailable = dt
                })
            }
            json.schedule = jsonSched
        }

        json.links = [:] << [self:linkGenerator.link(namespace:namespace,
            resource:"staff", action:"show", id:s1.id, absolute:false)]
        json
    }

    StaffJsonMarshaller() {
        super(Staff, marshalClosure)
    }
}
