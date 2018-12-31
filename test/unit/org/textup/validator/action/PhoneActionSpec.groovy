package org.textup.validator.action

import org.textup.test.*
import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import org.textup.*
import org.textup.type.PhoneOwnershipType
import org.textup.util.*
import org.textup.validator.PhoneNumber
import spock.lang.*

@Domain([CustomAccountDetails, Organization, Location])
@TestMixin(HibernateTestMixin)
class PhoneActionSpec extends Specification {

	static doWithSpring = {
		resultFactory(ResultFactory)
	}

	void "test constraints for empty"() {
		when: "completely number"
		PhoneAction act1 = new PhoneAction()

		then:
		act1.validate() == false
		act1.errors.errorCount == 1
		act1.errors.getFieldError("action").code == "nullable"

		when: "empty for deactivating"
		act1.action = Constants.PHONE_ACTION_DEACTIVATE

		then: "still okay since deactivating requires no additional information"
		act1.validate() == true

		when: "empty for transferring"
		act1.action = Constants.PHONE_ACTION_TRANSFER

		then:
		act1.validate() == false
		act1.errors.errorCount == 2
		act1.errors.getFieldError("id").code == "requiredForTransfer"
		act1.errors.getFieldError("type").code == "requiredForTransfer"

		when: "empty for changing numbers via new number"
		act1.action = Constants.PHONE_ACTION_NEW_NUM_BY_NUM

		then:
		act1.validate() == false
		act1.errors.errorCount == 1
		act1.errors.getFieldError("number").code == "requiredForChangeToNewNumber"

		when: "empty for changing numbers via number id"
		act1.action = Constants.PHONE_ACTION_NEW_NUM_BY_ID

		then:
		act1.validate() == false
		act1.errors.errorCount == 1
		act1.errors.getFieldError("numberId").code == "requiredForChangeToExistingNumber"
	}

	void "test constraints and getters for transferring"() {
		when: "a valid transfer request"
		PhoneAction act1 = new PhoneAction(action:Constants.PHONE_ACTION_TRANSFER,
			id:88L, type:"inDiviDual")
		assert act1.validate() == true

		then: "can get type as an enum"
		act1.typeAsEnum == PhoneOwnershipType.INDIVIDUAL

		when: "do not specify id"
		act1.id = null

		then:
		act1.validate() == false
		act1.errors.errorCount == 1
		act1.errors.getFieldError("id").code == "requiredForTransfer"

		when: "specify nonexistent id"
		act1.id = -88L

		then: "still ok because existence check delegated to Phone.transferTo"
		act1.validate() == true

		when: "do not specify type to transfer to"
		act1.type = null

		then:
		act1.validate() == false
		act1.errors.errorCount == 1
		act1.errors.getFieldError("type").code == "requiredForTransfer"

		when: "specify invalid type to transfer to"
		act1.type = "not a valid type"

		then:
		act1.validate() == false
		act1.errors.errorCount == 1
		act1.errors.getFieldError("type").code == "invalid"
	}

	void "test constraints for changing number"() {
		given: "an empty action"
		PhoneAction act1 = new PhoneAction()

		when: "changing phone number via invalid new number with no digits"
		act1.action = Constants.PHONE_ACTION_NEW_NUM_BY_NUM
		act1.number = "not a valid phone number"

		then: "number is pre-processed to strip letters so this is seen as empty"
		act1.validate() == false
		act1.errors.errorCount == 1
		act1.errors.getFieldError("number").code == "requiredForChangeToNewNumber"
		act1.phoneNumber instanceof PhoneNumber

		when: "changing phone number via invalid new number with some digits"
		act1.action = Constants.PHONE_ACTION_NEW_NUM_BY_NUM
		act1.number = "not a valid phone number 123"

		then: "invalid but can still get number wrapped in a PhoneNumber object"
		act1.validate() == false
		act1.errors.errorCount == 1
		act1.errors.getFieldError("number").code == "invalid"
		act1.phoneNumber instanceof PhoneNumber

		when: "changing phone number via valid new number"
		act1.number = "111sfasdfasdf82223333"

		then: "valid and can get number wrapped in a PhoneNumber object"
		act1.validate() == true
		act1.phoneNumber instanceof PhoneNumber

		when: "changing phone number via number id"
		act1.action = Constants.PHONE_ACTION_NEW_NUM_BY_ID
		act1.number = null
		act1.numberId = "any sort of string value"

		then:
		act1.validate() == true
	}
}
