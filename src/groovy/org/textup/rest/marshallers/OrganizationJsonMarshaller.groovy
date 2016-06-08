package org.textup.rest.marshallers

import grails.compiler.GrailsCompileStatic
import grails.plugin.springsecurity.SpringSecurityService
import org.codehaus.groovy.grails.web.mapping.LinkGenerator
import org.textup.*
import org.textup.rest.*
import org.textup.types.StaffStatus

@GrailsCompileStatic
class OrganizationJsonMarshaller extends JsonNamedMarshaller {
    static final Closure marshalClosure = { String namespace,
        SpringSecurityService springSecurityService, AuthService authService,
        LinkGenerator linkGenerator, Organization org ->

        Map json = [:]
        json.with {
            id = org.id
            name = org.name
            status = org.status.toString()
            location = org.location
            teams = org.getTeams()
            numAdmins = org.countPeople(statuses:[StaffStatus.ADMIN])
        }
        json.links = [:] << [self:linkGenerator.link(namespace:namespace,
            resource:"organization", action:"show", id:org.id, absolute:false)]
        json
    }

    OrganizationJsonMarshaller() {
        super(Organization, marshalClosure)
    }
}
