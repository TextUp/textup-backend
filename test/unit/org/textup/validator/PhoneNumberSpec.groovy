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

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test phone number validation for #phoneNum"() {
    	when: "we have a phone number"
    	PhoneNumber pNum = PhoneNumber.create(phoneNum)

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
    	PhoneNumber pNum = PhoneNumber.create("+12223334444")

        then:
    	pNum.number == "2223334444"
    	pNum.e164PhoneNumber == "+12223334444"
    	pNum.prettyPhoneNumber == "(222) 333-4444"
        pNum.toApiPhoneNumber().endpoint == pNum.number
    }

    void "test try url decoding"() {
        expect:
        PhoneNumber.tryUrlDecode(null).status == ResultStatus.UNPROCESSABLE_ENTITY
        PhoneNumber.tryUrlDecode("blah").status == ResultStatus.UNPROCESSABLE_ENTITY

        PhoneNumber.tryUrlDecode("+1 222 333 8888").status == ResultStatus.CREATED
        PhoneNumber.tryUrlDecode("+1 222 333 8888").payload.number == "2223338888"

        PhoneNumber.tryUrlDecode("%2B1%20222%20333%208888").status == ResultStatus.CREATED
        PhoneNumber.tryUrlDecode("%2B1%20222%20333%208888").payload.number == "2223338888"
    }

    void "test try creating"() {
        given:
        String validNum = TestUtils.randPhoneNumber()

        expect:
        PhoneNumber.tryCreate("invalid").status == ResultStatus.UNPROCESSABLE_ENTITY

        and:
        PhoneNumber.tryCreate(validNum).status == ResultStatus.CREATED
        PhoneNumber.tryCreate(validNum).payload instanceof PhoneNumber
    }

    void "test copying another phone number"() {
        given:
        BasePhoneNumber bNum = TestUtils.randPhoneNumber()
        BasePhoneNumber invalidNum = PhoneNumber.create("invalid number")

        expect:
        PhoneNumber.copy(bNum).validate()
        PhoneNumber.copy(bNum).number == bNum.number

        and:
        PhoneNumber.copy(null).validate() == false
        PhoneNumber.copy(invalidNum).validate() == false
    }

    void "test equality + hash code"() {
        given:
        PhoneNumber pNum1 = TestUtils.randPhoneNumber()
        PhoneNumber pNum2 = PhoneNumber.copy(pNum1)
        PhoneNumber pNum3 = TestUtils.randPhoneNumber()

        expect:
        pNum1 == pNum1
        pNum1.hashCode() == pNum1.hashCode()
        pNum2 == pNum2
        pNum2.hashCode() == pNum2.hashCode()
        pNum3 == pNum3
        pNum3.hashCode() == pNum3.hashCode()

        pNum1 == pNum2
        pNum1.hashCode() == pNum2.hashCode()
        pNum2 != pNum3
        pNum2.hashCode() != pNum3.hashCode()
        pNum1 != pNum3
        pNum1.hashCode() != pNum3.hashCode()
    }

    void "test getting area code"() {
        given:
        BasePhoneNumber bNum = TestUtils.randPhoneNumber()
        BasePhoneNumber invalidNum = PhoneNumber.create("invalid number")

        expect:
        bNum.areaCode == TestConstants.TEST_DEFAULT_AREA_CODE
        invalidNum.areaCode == ""
    }
}
