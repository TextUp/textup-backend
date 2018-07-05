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
		String validAction = "mErGe"
		assert validAction.toLowerCase() == Constants.NUMBER_ACTION_MERGE.toLowerCase()
		act1.with {
			action = validAction
			preference = 2
			number = "1abcdsd2112223333"
		}

		then:
		act1.validate() == true
		// test that matching action in switch statements matches all lower case action names
		testMatchCaseInSwitch(act1)
	}

	void "test deleting numbers allows invalid numbers"() {
		when: "attempting to remove an invalid phone number"
		ContactNumberAction act1 = new ContactNumberAction(action: Constants.NUMBER_ACTION_DELETE,
			number: 'i am not a valid number')

		then: "should be allowed to do so even if the number is invalid"
		act1.validate() == true

		when: "attempting to merge an invalid phone number"
		act1.action = Constants.NUMBER_ACTION_MERGE

		then: "the action becomes invalid"
		act1.validate() == false
		act1.errors.errorCount == 1
		act1.errors.getFieldError("number").code == "format"
	}

	protected boolean testMatchCaseInSwitch(ContactNumberAction act1) {
		switch (act1) {
			case { "MeRgE".toLowerCase() }: return true
			default: return false
		}
	}
}
