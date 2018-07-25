package org.textup.rest.marshaller

import grails.compiler.GrailsTypeChecked
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.codehaus.groovy.grails.web.mapping.LinkGenerator
import org.joda.time.DateTime
import org.textup.*
import org.textup.rest.*
import org.textup.validator.LocalInterval

@GrailsTypeChecked
class StaffJsonMarshaller extends JsonNamedMarshaller {
    static final Closure marshalClosure = { String namespace, GrailsApplication grailsApplication,
        LinkGenerator linkGenerator, Staff s1 ->

        Map json = [:]
        json.with {
            id = s1.id
            username = s1.username
            name = s1.name
            email = s1.email
            status = s1.status.toString()
            schedule  = s1.schedule
            phone = s1.phone
            hasInactivePhone = s1.hasInactivePhone
            // manual schedule fields
            manualSchedule = s1.manualSchedule
            if (s1.manualSchedule == true) {
                isAvailable = s1.isAvailable
            }
        }
        AuthService authService = grailsApplication.mainContext.getBean(AuthService)
        if (authService.isLoggedIn(s1.id) || authService.isAdminAtSameOrgAs(s1.id)) {
            json.with {
                org = s1.org
                if (s1.personalPhoneNumber) {
                    personalPhoneNumber = s1.personalPhoneNumber.e164PhoneNumber
                }
                teams = s1.getTeams()
            }
        }

        json.links = [:] << [self:linkGenerator.link(namespace:namespace,
            resource:"staff", action:"show", id:s1.id, absolute:false)]
        json
    }

    StaffJsonMarshaller() {
        super(Staff, marshalClosure)
    }
}
