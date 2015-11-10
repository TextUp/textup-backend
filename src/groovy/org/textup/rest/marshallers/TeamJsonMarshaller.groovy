package org.textup.rest.marshallers

import org.textup.*
import org.textup.rest.*
import org.codehaus.groovy.grails.web.mapping.LinkGenerator
import grails.plugin.springsecurity.SpringSecurityService

class TeamJsonMarshaller extends JsonNamedMarshaller {
    static final Closure marshalClosure = { String namespace,
        SpringSecurityService springSecurityService, AuthService authService,
        LinkGenerator linkGenerator, Team t1 ->

        Map json = [:]
        json.with {
            id = t1.id
            name = t1.name
            if (t1.phone) phone = t1.phone.number.number
            org = t1.org.id
        }
        json.location = [:]
        json.location.with {
            address = t1.location.address
            lat = t1.location.lat
            lon = t1.location.lon
        }

        json.links = [:]
        json.links << [self:linkGenerator.link(namespace:namespace, resource:"team", action:"show", id:t1.id, absolute:false)]
        json
    }

    TeamJsonMarshaller() {
        super(Team, marshalClosure)
    }
}
