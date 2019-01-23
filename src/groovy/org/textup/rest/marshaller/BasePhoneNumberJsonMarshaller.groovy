package org.textup.rest.marshaller

import grails.compiler.GrailsTypeChecked
import org.textup.*
import org.textup.rest.*
import org.textup.validator.*

@GrailsTypeChecked
class BasePhoneNumberJsonMarshaller extends JsonNamedMarshaller {

    static final Closure marshalClosure = { BasePhoneNumber bNum ->
        Map json = [:]
        json.with {
            e164Number     = bNum.e164Number
            noFormatNumber = bNum.number
            number         = bNum.prettyPhoneNumber
        }

        if (bNum instanceof AvailablePhoneNumber) {
            json[bNum.infoType] = aNum.info
        }

        json
    }

    BasePhoneNumberJsonMarshaller() {
        super(BasePhoneNumber, marshalClosure)
    }
}
