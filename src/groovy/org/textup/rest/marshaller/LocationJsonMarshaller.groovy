package org.textup.rest.marshaller

import grails.compiler.GrailsTypeChecked
import org.textup.*
import org.textup.rest.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*

@GrailsTypeChecked
class LocationJsonMarshaller extends JsonNamedMarshaller {

    static final Closure marshalClosure = { ReadOnlyLocation loc ->
        Map json = [:]
        json.with {
            address = loc.address
            id      = loc.id
            lat     = loc.lat
            lng     = loc.lng
        }
        json
    }

    LocationJsonMarshaller() {
        super(ReadOnlyLocation, marshalClosure)
    }
}
