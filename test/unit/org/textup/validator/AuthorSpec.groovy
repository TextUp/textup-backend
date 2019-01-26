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
@Unroll
class AuthorSpec extends Specification {

	void "test constraints"() {
		when: "we have all null fields"
		Author author = new Author()

		then: "is invalid"
		author.validate() == false
		author.errors.errorCount == 3

		when: "we save an author with all fields"
		author = new Author(id:12L, name:"hello", type:AuthorType.STAFF)

		then: "is valid"
		author.validate() == true
	}
}
