package org.textup.rest.marshaller

import grails.compiler.GrailsTypeChecked
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.codehaus.groovy.grails.web.mapping.LinkGenerator
import org.textup.*
import org.textup.rest.*
import org.textup.type.PhoneRecordStatus

@GrailsTypeChecked
class GroupPhoneRecordJsonMarshaller extends JsonNamedMarshaller {

    static final Closure marshalClosure = { GroupPhoneRecord gpr1 ->
        Map json = [:]
        json.with {
            futureMessages     = FutureMessages.buildForRecordIds([gpr1.record.id]).list()
            hexColor           = gpr1.hexColor
            id                 = gpr1.id
            language           = gpr1.record.language.toString()
            lastRecordActivity = gpr1.record.lastRecordActivity
            links              = MarshallerUtils.buildLinks(RestUtils.RESOURCE_TAG, gpr1.id)
            name               = gpr1.name
            numMembers         = gpr1.getMembersByStatus(PhoneRecordStatus.ACTIVE_STATUSES).size()
            phone              = gpr1.phone.id
        }
        json
    }

    GroupPhoneRecordJsonMarshaller() {
        super(GroupPhoneRecord, marshalClosure)
    }
}
