package org.textup.rest.marshaller

import grails.compiler.GrailsTypeChecked
import org.textup.*
import org.textup.rest.*

@GrailsTypeChecked
class TeamJsonMarshaller extends JsonNamedMarshaller {

    static final Closure marshalClosure = { Team t1 ->
        Map json = [:]
        json.with {
            hexColor   = t1.hexColor
            id         = t1.id
            links      = MarshallerUtils.buildLinks(RestUtils.RESOURCE_TEAM, t1.id)
            location   = t1.location
            name       = t1.name
            numMembers = t1.activeMembers.size()
            org        = t1.org.id // MUST BE id or else you have circular reference in json
            phone      = t1.phone
        }
        json
    }

    TeamJsonMarshaller() {
        super(Team, marshalClosure)
    }
}
