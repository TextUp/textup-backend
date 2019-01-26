package org.textup.validator

import com.twilio.type.PhoneNumber as TwilioPhoneNumber
import grails.test.mixin.*
import grails.test.mixin.gorm.*
import grails.test.mixin.hibernate.*
import grails.test.mixin.support.*
import grails.test.runtime.*
import grails.validation.*
import org.joda.time.*
import org.textup.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import spock.lang.*

@TestMixin(GrailsUnitTestMixin)
@Unroll
class PhoneNumberSpec extends Specification {

    void "test phone number validation for #phoneNum"() {
    	when: "we have a phone number"
    	PhoneNumber pNum = new PhoneNumber(number:phoneNum)

    	then:
    	pNum.validate() == isValid

    	where:
		phoneNum          | isValid
		"+2223334444"     | true
		"+12223334444"    | true
		"12223334444"     | true
		"2223334444"      | true
		"222333sdf4444"   | true
		"222 33 4444"     | false
		"222 3asdf3 4444" | false
    }

    void "test phone number formatting"() {
    	when: "we have a valid phone number"
    	PhoneNumber pNum = new PhoneNumber(number:"+12223334444")
        TwilioPhoneNumber tNum = pNum.toApiPhoneNumber()

        then:
    	pNum.number == "2223334444"
    	pNum.e164PhoneNumber == "+12223334444"
    	pNum.prettyPhoneNumber == "222 333 4444"
        tNum.endpoint == pNum.number
    }

    void "test url decoding"() {
        expect:
        PhoneNumber.urlDecode(null).validate() == false
        PhoneNumber.urlDecode("blah").validate() == false

        PhoneNumber.urlDecode("+1 222 333 8888").validate()
        PhoneNumber.urlDecode("+1 222 333 8888").number == "2223338888"

        PhoneNumber.urlDecode("%2B1%20222%20333%208888").validate()
        PhoneNumber.urlDecode("%2B1%20222%20333%208888").number == "2223338888"
    }
}
