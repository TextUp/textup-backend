package org.textup.validator.action

import org.textup.test.*
import grails.test.mixin.support.GrailsUnitTestMixin
import grails.test.mixin.TestMixin
import spock.lang.Specification
import org.textup.Constants

@TestMixin(GrailsUnitTestMixin)
class ContactNumberActionSpec extends Specification {

	void "test constraints"() {
		given:
		String num1 = TestUtils.randPhoneNumberString()

		when: "empty"
		ContactNumberAction act1 = new ContactNumberAction()

		then:
		act1.validate() == false

		when: "negative preference"
		act1.preference = -8

		then:
		act1.validate() == false
		act1.errors.getFieldError("preference").code == "min.notmet"

		when: "invalid phone number"
		act1.number = TestUtils.randString()

		then:
		act1.validate() == false
		act1.errors.getFieldErrorCount("number") > 0

		when: "all valid"
		act1.with {
			action = ContactNumberAction.MERGE
			preference = 2
			number = num1
		}

		then:
		act1.validate() == true
		act1.buildPhoneNumber().number == num1
	}
}
