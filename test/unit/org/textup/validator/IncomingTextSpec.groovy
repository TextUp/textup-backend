package org.textup.validator

import grails.test.mixin.support.GrailsUnitTestMixin
import grails.test.mixin.TestMixin
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

@TestMixin(GrailsUnitTestMixin)
@Unroll
class IncomingTextSpec extends Specification {

	void "test constraints"() {
		when: "we have all null fields"
		IncomingText text = new IncomingText()

		then: "is invalid"
		text.validate() == false
		text.errors.errorCount == 3

		when: "we save an text with all fields"
		text = new IncomingText(apiId:"id", message:"hello", numSegments: 88)

		then: "is valid"
		text.validate() == true

		when: "negative number of segments"
		text.numSegments = -88

		then: "invalid"
		text.validate() == false
		text.errors.getFieldErrorCount("numSegments") == 1
	}

	void "test message cleaning"() {
		when: "we have a valid text"
		IncomingText text = new IncomingText(apiId:"id", message:"      hELLo  ", numSegments: 88)

		then: "no formatting except for trimming whitespace"
		text.validate() == true
		text.message == "hELLo"
	}
}
