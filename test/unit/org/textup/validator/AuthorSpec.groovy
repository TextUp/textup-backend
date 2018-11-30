package org.textup.validator

import org.textup.test.*
import grails.test.mixin.support.GrailsUnitTestMixin
import grails.test.mixin.TestMixin
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll
import org.textup.type.AuthorType

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
