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
class StaffJsonMarshaller extends JsonNamedMarshaller {

    static final Closure marshalClosure = { Staff s1 ->
        Map json = [:]
        json.with {
            id       = s1.id
            links    = MarshallerUtils.buildLinks(RestUtils.RESOURCE_STAFF, s1.id)
            name     = s1.name
            phone    = s1.phone
            status   = s1.status.toString()
            username = s1.username
        }

        Staffs.isAllowed(s1.id)
            .then {
                json.with {
                    channelName    = SocketUtils.channelName(s1)
                    email          = s1.email
                    org            = s1.org
                    personalNumber = s1.personalNumber
                    teams          = Teams.buildForStaffIds([s1.id])
                }
            }

        json
    }

    StaffJsonMarshaller() {
        super(Staff, marshalClosure)
    }
}
