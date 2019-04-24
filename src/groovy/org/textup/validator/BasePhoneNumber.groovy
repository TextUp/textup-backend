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
        (number && number.size() > 6) ? "(${number[0..2]}) ${number[3..5]}-${number[6..-1]}" : ""
    }

    String getAreaCode() {
        (number && number.size() > 3) ? "${number[0..2]}" : ""
    }

    String getE164PhoneNumber() { number ? "+1${number}" : "" }

    void setNumber(String num) { number = StringUtils.cleanPhoneNumber(num) }

    TwilioPhoneNumber toApiPhoneNumber() { new TwilioPhoneNumber(number) }
}
