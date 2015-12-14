package org.textup.rest.marshallers

import org.textup.*
import org.textup.rest.*
import org.codehaus.groovy.grails.web.mapping.LinkGenerator
import grails.plugin.springsecurity.SpringSecurityService

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
            status = s1.status
            awayMessage = s1.awayMessage
            if (s1.personalPhoneNumber) personalPhoneNumber = s1.personalPhoneNumber.number
            if (s1.phone) phone = s1.phone.number.number
            
            manualSchedule = s1.manualSchedule
            if (manualSchedule == true) { isAvailable = s1.isAvailable }
            isAvailableNow = s1.isAvailableNow()
        }
        json.tags = s1.phone ? s1.phone.tags : []
        if (s1.schedule.instanceOf(WeeklySchedule)) {
            String timezone = WebUtils.retrieveGrailsWebRequest().currentRequest.timezone
            json.schedule = [:]
            s1.schedule.getAllAsLocalIntervals(timezone).each { String day, List<LocalInterval> intervals ->
                json.schedule."${day}" = intervals.collect { LocalInterval localInt ->
                    Helpers.printLocalInterval(localInt)
                }
            }
            if (s1.manualSchedule == false) {
                Result res = s1.nextAvailable(timezone)
                if (res.success) { json.schedule.nextAvailable = res.payload }
                res = s1.nextUnavailable(timezone)
                if (res.success) { json.schedule.nextUnavailable = res.payload }
            }
        }

        json.links = [:]
        json.links << [self:linkGenerator.link(namespace:namespace, resource:"staff", action:"show", id:s1.id, absolute:false)]
        json
    }

    StaffJsonMarshaller() {
        super(Staff, marshalClosure)
    }
}
