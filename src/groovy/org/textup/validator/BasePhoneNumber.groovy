package org.textup.validator

import com.twilio.type.PhoneNumber as TwilioPhoneNumber
import grails.compiler.GrailsTypeChecked
import org.textup.util.StringUtils

@GrailsTypeChecked
abstract class BasePhoneNumber {

    String number

    // Methods
    // -------

    BasePhoneNumber update(BasePhoneNumber otherNum) {
        number = otherNum?.number
        this
    }

    @Override
    String toString() {
        prettyPhoneNumber
    }

    // Property Access
    // ---------------

    String getPrettyPhoneNumber() {
        String n = number
        (n && n.size() > 6) ? "${n[0..2]} ${n[3..5]} ${n[6..-1]}" : ""
    }
    String getE164PhoneNumber() {
        String n = number
        n ? "+1${n}" : ""
    }
    void setNumber(String num) {
        number = StringUtils.cleanPhoneNumber(num)
    }

    TwilioPhoneNumber toApiPhoneNumber() {
        new TwilioPhoneNumber(number)
    }
}
