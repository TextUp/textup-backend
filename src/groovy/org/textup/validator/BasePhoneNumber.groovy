package org.textup.validator

import com.twilio.type.PhoneNumber as TwilioPhoneNumber
import grails.compiler.GrailsTypeChecked
import groovy.transform.EqualsAndHashCode
import org.textup.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*

@EqualsAndHashCode
@GrailsTypeChecked
abstract class BasePhoneNumber implements CanValidate {

    String number

    // Methods
    // -------

    @Override
    String toString() { prettyPhoneNumber }

    @Override
    boolean equals(Object obj) {
        obj instanceof BasePhoneNumber ? number?.equals(obj.number) : false
    }

    @Override
    int hashCode() { number?.hashCode() ?: 0 }

    // Properties
    // ----------

    String getPrettyPhoneNumber() {
        String num1 = number
        (num1 && num1.size() > 6) ? "${num1[0..2]} ${num1[3..5]} ${num1[6..-1]}" : ""
    }

    String getE164PhoneNumber() { number ? "+1${number}" : "" }

    void setNumber(String num) { number = StringUtils.cleanPhoneNumber(num) }

    TwilioPhoneNumber toApiPhoneNumber() { new TwilioPhoneNumber(number) }
}
