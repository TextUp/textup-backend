package org.textup.validator.action

import grails.test.mixin.support.GrailsUnitTestMixin
import grails.test.mixin.TestMixin
import spock.lang.Specification
import org.textup.Constants

@TestMixin(GrailsUnitTestMixin)
class ContactNumberActionSpec extends Specification {

	void "test constraints"() {
		when: "empty"
		ContactNumberAction act1 = new ContactNumberAction()

		then:
		act1.validate() == false
		act1.errors.errorCount == 2 // preference is 0 (valid) by default

		when: "invalid action"
		act1.action = "invalid"

		then:
		act1.validate() == false
		act1.errors.errorCount == 2
		act1.errors.getFieldError("action").code == "invalid"

		when: "negative preference"
		act1.preference = -8

		then:
		act1.validate() == false
		act1.errors.errorCount == 3
		act1.errors.getFieldError("preference").code == "min.notmet"

		when: "invalid phone number"
		act1.number = "i am not a valid phone number"

		then:
		act1.validate() == false
		act1.errors.errorCount == 3
		act1.errors.getFieldError("number").code == "format"

		when: "all valid"
		act1.with {
			action = Constants.NUMBER_ACTION_MERGE
			preference = 2
			number = "1abcdsd2112223333"
		}

		then:
		act1.validate() == true
	}
}