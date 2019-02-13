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
class OwnerPolicyJsonMarshaller extends JsonNamedMarshaller {

    static final Closure marshalClosure = { ReadOnlyOwnerPolicy rop1 ->
        Map json = [:]
        json.with {
            frequency             = rop1.frequency.toString()
            level                 = rop1.level.toString()
            method                = rop1.method.toString()
            name                  = rop1.readOnlyStaff.name
            schedule              = rop1.readOnlySchedule
            shouldSendPreviewLink = rop1.shouldSendPreviewLink
            staff                 = rop1.readOnlyStaff.id
        }
        json
    }

    OwnerPolicyJsonMarshaller() {
        super(ReadOnlyOwnerPolicy, marshalClosure)
    }
}
