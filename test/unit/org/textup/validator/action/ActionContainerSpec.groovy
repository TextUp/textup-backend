package org.textup.validator.action

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import org.springframework.context.MessageSource
import org.springframework.context.MessageSourceResolvable
import org.textup.*
import org.textup.util.*

@Domain([Organization, Location])
@TestMixin(HibernateTestMixin)
class ActionContainerSpec extends CustomSpec {

	static doWithSpring = {
		resultFactory(ResultFactory)
	}

	// extend CustomSpec solely for access to helper methods, not for setting up test data
	def setup() {
		ResultFactory resultFactory = grailsApplication.mainContext.getBean("resultFactory")
		resultFactory.messageSource = TestHelpers.mockMessageSourceWithResolvable()
	}

	void "test constraints"() {
		when: "empty"
		ActionContainer ac1 = new ActionContainer()

		then:
		ac1.validate() == false
		ac1.errors.errorCount == 1

		when: "data is not a collection"
		ac1.data = "not a collection"

		then:
		ac1.validate() == false
		ac1.errors.errorCount == 1
		ac1.errors.getFieldError("data").code == "emptyOrNotACollection"

		when: "data is one of the items is not a map"
		ac1.data = [[:], "not a map"]

		then:
		ac1.validate() == false
		ac1.errors.errorCount == 1
		ac1.errors.getFieldError("data").code == "emptyOrNotAMap"

		when: "data is a collection of maps"
		ac1.data = [[:], [:]]

		then:
		ac1.validate() == true
	}

	void "test building actions"() {
		when: "invalid format"
		ActionContainer ac1 = new ActionContainer("not a collection")

		then:
		ac1.hasErrors() == false
		ac1.validateAndBuildActions() == [] // this will call validate too
		ac1.hasErrors() == true

		when: "valid format and some valid contents, some invalid contents"
		String num = "1112223333"
		ac1.data = [
			[
				action:"not a valid action"
			],
			[
				action:Constants.NUMBER_ACTION_MERGE,
				number:num,
				preference: "8" // this string will be cast to an integer
			]
		]

		List<ContactNumberAction> actions = ac1.validateAndBuildActions(ContactNumberAction)

		then:
		ac1.errors.globalErrorCount == 1
		ac1.errors.globalErrors[0].code == "actionContainer.invalidActions"
		actions.isEmpty() == false
		actions.size() == 1
		actions[0] instanceof ContactNumberAction
		actions[0].number == num
		actions[0].action == Constants.NUMBER_ACTION_MERGE
		actions[0].preference == 8
	}
}
