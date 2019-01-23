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

    static final Closure marshalClosure = { OwnerPolicy op1 ->
        Map json = [:]
        json.with {
            frequency             = op1.frequency.toString()
            level                 = op1.level.toString()
            method                = op1.method.toString()
            name                  = op1.staff.name
            schedule              = op1.schedule
            shouldSendPreviewLink = op1.shouldSendPreviewLink
            staff                 = op1.staff.id
        }
        json
    }

    OwnerPolicyJsonMarshaller() {
        super(OwnerPolicy, marshalClosure)
    }
}
