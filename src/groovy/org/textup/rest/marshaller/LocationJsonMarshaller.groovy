package org.textup.rest.marshaller

import grails.compiler.GrailsTypeChecked
import org.codehaus.groovy.grails.web.mapping.LinkGenerator
import org.textup.*
import org.textup.rest.*

@GrailsTypeChecked
class LocationJsonMarshaller extends JsonNamedMarshaller {
    static final Closure marshalClosure = { ReadOnlyLocation loc ->

        Map json = [:]
        json.with {
            id = loc.id
            address = loc.address
            lat = loc.lat
            lng = loc.lng
        }
        json
    }

    LocationJsonMarshaller() {
        super(ReadOnlyLocation, marshalClosure)
    }
}
