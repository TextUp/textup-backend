package org.textup.validator.action

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import org.textup.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*
import spock.lang.*

@Domain([CustomAccountDetails, Organization, Location])
@TestMixin(HibernateTestMixin)
class PhoneActionSpec extends Specification {

	static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

	void "test constraints for empty"() {
		when: "completely number"
		PhoneAction act1 = new PhoneAction()

		then:
		act1.validate() == false

		when: "empty for deactivating"
		act1.action = PhoneAction.DEACTIVATE

		then: "valid since deactivating requires no additional information"
		act1.validate() == true

		when: "empty for transferring"
		act1.action = PhoneAction.TRANSFER

		then:
		act1.validate() == false
		act1.errors.getFieldError("id").code == "requiredForTransfer"
		act1.errors.getFieldError("type").code == "requiredForTransfer"

		when: "empty for changing numbers via new number"
		act1.action = PhoneAction.NEW_NUM_BY_NUM

		then:
		act1.validate() == false
		act1.errors.getFieldError("number").code == "requiredForChangeToNewNumber"

		when: "empty for changing numbers via number id"
		act1.action = PhoneAction.NEW_NUM_BY_ID

		then:
		act1.validate() == false
		act1.errors.getFieldError("numberId").code == "requiredForChangeToExistingNumber"
	}

	void "test constraints and getters for transferring"() {
		when: "a valid transfer request"
		PhoneAction act1 = new PhoneAction(action: PhoneAction.TRANSFER,
			id: 88L,
			type: "inDiviDual")

		then: "can get type as an enum"
		act1.validate()
		act1.buildPhoneOwnershipType() == PhoneOwnershipType.INDIVIDUAL

		when: "do not specify id"
		act1.id = null

		then:
		act1.validate() == false
		act1.errors.getFieldError("id").code == "requiredForTransfer"

		when: "specify nonexistent id"
		act1.id = -88L

		then: "still ok because existence check delegated"
		act1.validate()

		when: "do not specify type to transfer to"
		act1.type = null

		then:
		act1.validate() == false
		act1.errors.getFieldError("type").code == "requiredForTransfer"

		when: "specify invalid type to transfer to"
		act1.type = "not a valid type"

		then:
		act1.validate() == false
		act1.errors.getFieldError("type").code == "invalid"
	}

	void "test constraints for changing number to another number"() {
		given: "an empty action"
		String num1 = TestUtils.randPhoneNumberString()
		PhoneAction act1 = new PhoneAction()

		when: "changing phone number via invalid new number with no digits"
		act1.action = PhoneAction.NEW_NUM_BY_NUM
		act1.number = "not a valid phone number"

		then:
		act1.validate() == false
		act1.errors.getFieldErrorCount("number") > 0
		act1.buildPhoneNumber().number == ""

		when: "changing phone number via valid new number"
		act1.number = num1

		then:
		act1.validate() == true
		act1.buildPhoneNumber().number == num1
	}

	void "test constraints for changing number via api id"() {
		given: "an empty action"
		PhoneAction act1 = new PhoneAction()

		when: "changing phone number via number id"
		act1.action = PhoneAction.NEW_NUM_BY_ID
		act1.numberId = TestUtils.randString()

		then:
		act1.validate()
	}
}
