package org.textup.validator.action

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import org.textup.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import spock.lang.*

@Domain([CustomAccountDetails, Organization, Location])
@TestMixin(HibernateTestMixin)
class ActionContainerSpec extends Specification {

	static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

	void "test constraints"() {
		when: "empty"
		ActionContainer ac1 = new ActionContainer(null, null)

		then:
		ac1.validate() == false

		when: "data is not a collection"
		ac1 = new ActionContainer(null, "not a collection")

		then:
		ac1.validate() == false
		ac1.errors.getFieldError("data").code == "emptyOrNotACollection"

		when: "data is one of the items is not a map"
		ac1 = new ActionContainer(null, [[:], "not a map"])

		then:
		ac1.validate() == false
		ac1.errors.getFieldError("data").code == "emptyOrNotAMap"

		when: "data is a collection of maps"
		ac1 = new ActionContainer(PhoneAction, [[action: PhoneAction.DEACTIVATE]])

		then:
		ac1.validate() == true
	}

	void "test building actions"() {
		given:
		Map invalidAction = [action: "not a valid action"]
		Map validAction = [
			action: ContactNumberAction.MERGE,
			number: TestUtils.randPhoneNumberString(),
			preference: "8" // this string will be cast to an integer
		]

		when: "invalid format"
		ActionContainer ac1 = new ActionContainer(ContactNumberAction, "invalid")

		then:
		ac1.validate() == false
		ac1.actions == []

		when: "valid format and some valid contents, some invalid contents"
		ac1 = new ActionContainer(ContactNumberAction, [invalidAction, validAction])

		then:
		ac1.validate() == false
		ac1.errors.getFieldErrorCount("actions.0.action") > 0
		ac1.actions.size() == 2
		ac1.actions.every { it instanceof ContactNumberAction }
		ac1.actions[0].validate() == false
		ac1.actions[1].validate()

		when:
		ac1 = new ActionContainer(ContactNumberAction, [validAction])

		then:
		ac1.validate()
		ac1.actions.size() == 1
		ac1.actions[0] instanceof ContactNumberAction
		ac1.actions[0].preference == validAction.preference.toLong()
		ac1.actions[0].number == validAction.number
		ac1.actions[0].action == validAction.action
		ac1.actions[0].validate()
	}

	void "test try processing"() {
		given:
		Map invalidAction = [action: "not a valid action"]
		Map validAction = [action: PhoneAction.DEACTIVATE]

		when:
		Result res = ActionContainer.tryProcess(null, null)

		then:
		res.status == ResultStatus.OK
		res.payload == []

		when:
		res = ActionContainer.tryProcess(PhoneAction, [validAction, invalidAction])

		then:
		res.status == ResultStatus.UNPROCESSABLE_ENTITY

		when:
		res = ActionContainer.tryProcess(PhoneAction, [validAction])

		then:
		res.status == ResultStatus.OK
		res.payload.size() == 1
		res.payload.every { it instanceof PhoneAction }
		res.payload[0].action == PhoneAction.DEACTIVATE
	}
}
