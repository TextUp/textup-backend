package org.textup.rest.marshallers

import org.textup.*
import org.textup.rest.*
import org.codehaus.groovy.grails.web.mapping.LinkGenerator
import grails.plugin.springsecurity.SpringSecurityService

class OrganizationJsonMarshaller extends JsonNamedMarshaller {
    static final Closure marshalClosure = { String namespace,
        SpringSecurityService springSecurityService, AuthService authService,
        LinkGenerator linkGenerator, Organization org ->

        Map json = [:]
        json.id = org.id
        json.name = org.name
        json.location = [:]
        json.location.with {
            address = org.location.address
            lat = org.location.lat
            lon = org.location.lon
        }
        json.teams = org.teams
        json.numAdmins = org.countPeople(status:[Constants.STATUS_ADMIN])

        json.links = [:]
        json.links << [self:linkGenerator.link(namespace:namespace, resource:"organization", action:"show", id:org.id, absolute:false)]
        json
    }

    OrganizationJsonMarshaller() {
        super(Organization, marshalClosure)
    }
}
