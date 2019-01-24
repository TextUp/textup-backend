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
            phone      = Phones.mustFindActiveForOwner(t1.id, PhoneOwnershipType.GROUP, false)
        }
        json
    }

    TeamJsonMarshaller() {
        super(Team, marshalClosure)
    }
}
