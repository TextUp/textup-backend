package org.textup.validator

import com.twilio.type.PhoneNumber as TwilioPhoneNumber
import grails.test.mixin.support.GrailsUnitTestMixin
import grails.test.mixin.TestMixin
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

@TestMixin(GrailsUnitTestMixin)
class AvailablePhoneNumberSpec extends Specification {

	void "test constraints"() {
		when: "empty"
		AvailablePhoneNumber aNum = new AvailablePhoneNumber()

		then:
		aNum.validate() == false
		aNum.errors.errorCount == 3
		aNum.errors.getFieldError("number").code == "nullable"
		aNum.errors.getFieldError("info").code == "nullable"
		aNum.errors.getFieldError("infoType").code == "nullable"

		when: "invalid phone number"
		aNum.phoneNumber = "I am an 123123 *&^invalid phone number"

		then:
		aNum.validate() == false
		aNum.errors.errorCount == 3
		aNum.errors.getFieldError("number").code == "format"
		aNum.errors.getFieldError("info").code == "nullable"
		aNum.errors.getFieldError("infoType").code == "nullable"

		when: "invalid info type"
		aNum.infoType = "invalid info type not in list"

		then:
		aNum.validate() == false
		aNum.errors.errorCount == 3
		aNum.errors.getFieldError("number").code == "format"
		aNum.errors.getFieldError("infoType").code == "not.inList"
		aNum.errors.getFieldError("info").code == "nullable"

		when: "all valid"
		aNum.phoneNumber = "111 222 &*(&^%^ 3333"
		aNum.infoType = "sid"
		aNum.info = "a valid sid here"

		then:
		aNum.validate() == true
	}

	void "test setting info via provided setters"() {
		given: "a valid available phone number"
		AvailablePhoneNumber aNum = new AvailablePhoneNumber(phoneNumber:"111@222 3333",
			infoType:"region", info:"i am a valid region here")
		assert aNum.validate() == true

		String val = "i am a valid info value"

		when: "setting for sid"
		aNum.setSid(val)

		then:
		aNum.infoType == "sid"
		aNum.info == val

		when: "setting for region"
		aNum.setRegion(val)

		then:
		aNum.infoType == "region"
		aNum.info == val
	}
}