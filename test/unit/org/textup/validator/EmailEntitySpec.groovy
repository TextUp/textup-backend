package org.textup.validator

import com.sendgrid.Email
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
class EmailEntitySpec extends Specification {

	static doWithSpring = {
		resultFactory(ResultFactory)
	}

	def setup() {
		TestUtils.standardMockSetup()
	}

	void "test constraints and conversion"() {
		given:
		String name = TestUtils.randString()
		String email = "${name}@kiki.com"

		when: "all fields are null"
		Result res = EmailEntity.tryCreate(null, null)

		then: "invalid"
		res.status == ResultStatus.UNPROCESSABLE_ENTITY
		res.errorMessages.size() == 2

		when: "all fields are filled, but email is invalid format"
		res = EmailEntity.tryCreate("Kiki", "what is this?")

		then: "invalid"
		res.status == ResultStatus.UNPROCESSABLE_ENTITY
		res.errorMessages.size() == 1

		when: "has name and valid email"
		res = EmailEntity.tryCreate(name, email)

		then: "valid"
		res.status == ResultStatus.CREATED
		res.payload instanceof EmailEntity
		res.payload.name == name
		res.payload.email == email

		when: "converting a SendGrid email"
		Email email1 = res.payload.toSendGridEmail()

		then:
		email1.name == name
		email1.email == email
	}
}
