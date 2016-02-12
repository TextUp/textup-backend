package org.textup.validator

import grails.test.mixin.support.GrailsUnitTestMixin
import grails.test.mixin.TestMixin
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

@TestMixin(GrailsUnitTestMixin)
@Unroll
class EmailEntitySpec extends Specification {

	void "test constraints"() {
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
	}
}
