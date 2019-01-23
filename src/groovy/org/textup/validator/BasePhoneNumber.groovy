package org.textup.validator

import com.twilio.type.PhoneNumber as TwilioPhoneNumber
import grails.compiler.GrailsTypeChecked
import org.textup.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*

@GrailsTypeChecked
abstract class BasePhoneNumber implements CanValidate {

    String number

    // Methods
    // -------

    @Override
    String toString() { prettyPhoneNumber }

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
