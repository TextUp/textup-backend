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

    static final Closure marshalClosure = { ReadOnlyStaff rs1 ->
        Map json = [:]
        json.with {
            id       = rs1.id
            links    = MarshallerUtils.buildLinks(RestUtils.RESOURCE_STAFF, rs1.id)
            name     = rs1.name
            phone    = IOCUtils.phoneCache.findPhone(rs1.id, PhoneOwnershipType.INDIVIDUAL, true)
            status   = rs1.status.toString()
            username = rs1.username
        }

        Staffs.isAllowed(rs1.id)
            .thenEnd {
                json.with {
                    channelName    = SocketUtils.channelName(rs1)
                    email          = rs1.email
                    org            = rs1.readOnlyOrg
                    personalNumber = rs1.hasPersonalNumber() ? rs1.personalNumber : null
                    teams          = Teams.buildActiveForStaffIds([rs1.id]).list()
                }
            }

        json
    }

    StaffJsonMarshaller() {
        super(ReadOnlyStaff, marshalClosure)
    }
}
