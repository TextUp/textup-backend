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
class BasePhoneNumberJsonMarshaller extends JsonNamedMarshaller {

    static final Closure marshalClosure = { BasePhoneNumber bNum ->
        Map json = [:]
        json.with {
            e164Number     = bNum.e164PhoneNumber
            noFormatNumber = bNum.number
            number         = bNum.prettyPhoneNumber
        }

        if (bNum instanceof AvailablePhoneNumber) {
            json[bNum.infoType] = bNum.info
        }

        json
    }

    BasePhoneNumberJsonMarshaller() {
        super(BasePhoneNumber, marshalClosure)
    }
}
