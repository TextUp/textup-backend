package org.textup.rest.marshaller

import grails.compiler.GrailsCompileStatic
import org.textup.*
import org.textup.rest.*
import org.textup.validator.AvailablePhoneNumber

@GrailsCompileStatic
class AvailablePhoneNumberJsonMarshaller extends JsonNamedMarshaller {

    static final Closure marshalClosure = { AvailablePhoneNumber aNum ->

        Map json = [phoneNumber:aNum.e164PhoneNumber]
        json[aNum.infoType] = aNum.info
        json
    }

    AvailablePhoneNumberJsonMarshaller() {
        super(AvailablePhoneNumber, marshalClosure)
    }
}
