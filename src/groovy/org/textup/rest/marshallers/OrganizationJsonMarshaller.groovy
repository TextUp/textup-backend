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
        json.id = org.id
        json.name = org.name
        json.status = org.status.toString()
        json.location = [:] << [
            address:org.location.address,
            lat:org.location.lat,
            lon:org.location.lon
        ]
        json.teams = org.getTeams()
        json.numAdmins = org.countPeople(statuses:[StaffStatus.ADMIN])

        json.links = [:] << [self:linkGenerator.link(namespace:namespace,
            resource:"organization", action:"show", id:org.id, absolute:false)]
        json
    }

    OrganizationJsonMarshaller() {
        super(Organization, marshalClosure)
    }
}
