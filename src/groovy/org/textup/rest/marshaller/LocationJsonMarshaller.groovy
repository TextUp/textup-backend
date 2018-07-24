package org.textup.rest.marshaller

import grails.compiler.GrailsCompileStatic
import org.codehaus.groovy.grails.web.mapping.LinkGenerator
import org.textup.*
import org.textup.rest.*

@GrailsCompileStatic
class LocationJsonMarshaller extends JsonNamedMarshaller {
    static final Closure marshalClosure = { ReadOnlyLocation loc ->

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
        super(ReadOnlyLocation, marshalClosure)
    }
}
