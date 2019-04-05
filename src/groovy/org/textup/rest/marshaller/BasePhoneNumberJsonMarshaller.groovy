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
        if (bNum instanceof AvailablePhoneNumber) {
            [(bNum.infoType): bNum.info, number: bNum.prettyPhoneNumber]
        }
        else if (bNum instanceof ContactNumber) { // TODO test
            [number: bNum.prettyPhoneNumber]
        }
        else { bNum.prettyPhoneNumber }
    }

    BasePhoneNumberJsonMarshaller() {
        super(BasePhoneNumber, marshalClosure)
    }
}
