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

	void "test constraints and conversion"() {
		when: "all fields are null"
		EmailEntity emailEnt = new EmailEntity()

		then: "invalid"
		emailEnt.validate() == false
		emailEnt.errors.errorCount == 2

		when: "all fields are filled, but email is invalid format"
		emailEnt = new EmailEntity(name:"Kiki", email:"what is this?")

		then: "invalid"
		emailEnt.validate() == false
		emailEnt.errors.errorCount == 1

		when: "has name and valid email"
		emailEnt = new EmailEntity(name:"Kiki", email:"hello@kiki.com")

		then: "valid"
		emailEnt.validate() == true

		when: "converting a SendGrid email"
		Email email1 = emailEnt.toEmail()

		then:
		email1.name == emailEnt.name
		email1.email == emailEnt.email
	}
}
