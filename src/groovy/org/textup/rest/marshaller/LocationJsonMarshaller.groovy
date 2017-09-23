package org.textup.rest.marshaller

import grails.compiler.GrailsCompileStatic
import grails.plugin.springsecurity.SpringSecurityService
import org.codehaus.groovy.grails.web.mapping.LinkGenerator
import org.textup.*
import org.textup.rest.*

@GrailsCompileStatic
class LocationJsonMarshaller extends JsonNamedMarshaller {
    static final Closure marshalClosure = { String namespace,
        SpringSecurityService springSecurityService, AuthService authService,
        LinkGenerator linkGenerator, Location loc ->

        Map json = [:]
        json.with {
            id = loc.id
            address = loc.address
            lat = loc.lat
            lon = loc.lon
        }
        json
    }

    LocationJsonMarshaller() {
        super(Location, marshalClosure)
    }
}
