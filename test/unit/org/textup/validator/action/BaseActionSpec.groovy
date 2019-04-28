package org.textup.validator.action

import org.textup.test.*
import grails.test.mixin.support.GrailsUnitTestMixin
import grails.test.mixin.TestMixin
import spock.lang.Specification
import org.textup.Constants

@TestMixin(GrailsUnitTestMixin)
class BaseActionSpec extends Specification {

	static final String ALLOWED_ACTION = "allowedaction"

	void "test constraints"() {
		when:
		BaseActionImpl bAction = new BaseActionImpl()

		then:
		bAction.validate() == false
		bAction.errors.getFieldErrorCount("action") > 0

		when:
		bAction.action = TestUtils.randString()

		then:
		bAction.validate() == false
		bAction.errors.getFieldErrorCount("action") > 0

		when:
		bAction.action = ALLOWED_ACTION

		then:
		bAction.validate()
	}

	void "test updating via map"() {
		given:
		Long height = TestUtils.randIntegerUpTo(88)
		Long weight = TestUtils.randIntegerUpTo(88)
		String name = TestUtils.randString()

		when:
		BaseActionImpl bAction = new BaseActionImpl()
		bAction.update(action: ALLOWED_ACTION,
			height: height,
			weight: weight,
			name: name,
			nonexistent: "hello")

		then: "nonexistent properties ignored without errors"
		bAction.action == ALLOWED_ACTION
		bAction.height == height
		bAction.weight == weight
		bAction.name == name
	}

	void "test use in switch statements"() {
		given:
		BaseActionImpl bAction = new BaseActionImpl(action: "aLlOwEdAcTiOn")

		int branchNum = -1
		switch (bAction) {
			case "aLlOwEdAcTiOn":
				branchNum = 0
				break
			case "allowedAction":
				branchNum = 1
				break
			case "allowedaction":
				branchNum = 2
				break
			case ALLOWED_ACTION:
				branchNum = 3
				break
			default:
				branchNum = 4
		}

		expect:
		branchNum == 2
	}

	protected class BaseActionImpl extends BaseAction {

		Long height
		long weight
		String name

		@Override
		Collection<String> getAllowedActions() { [ALLOWED_ACTION] }
	}
}
