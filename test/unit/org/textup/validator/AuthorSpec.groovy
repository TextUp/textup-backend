package org.textup.validator

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
class AuthorSpec extends Specification {

	static doWithSpring = {
        resultFactory(ResultFactory)
    }

	void "test constraints"() {
		when: "we have all null fields"
		Author author1 = Author.create(null, null, null)

		then: "is invalid"
		author1.validate() == false
		author1.errors.errorCount == 3

		when: "we save an author with all fields"
		author1 = Author.create(12L, "hello", AuthorType.STAFF)

		then: "is valid"
		author1.validate() == true
	}

	void "creating from session"() {
		given:
		IncomingSession is1 = GroovyStub() {
			getId() >> TestUtils.randIntegerUpTo(10)
			getNumber() >> TestUtils.randPhoneNumber()
		}

		when:
		Author author1 = Author.create(is1)

		then:
		author1.id == is1.id
		author1.name == is1.number.prettyPhoneNumber
		author1.type == AuthorType.SESSION
	}

	void "creating from staff"() {
		given:
		Staff s1 = GroovyStub() {
			getId() >> TestUtils.randIntegerUpTo(10)
			getName() >> TestUtils.randString()
		}

		when:
		Author author1 = Author.create(s1)

		then:
		author1.id == s1.id
		author1.name == s1.name
		author1.type == AuthorType.STAFF
	}
}
