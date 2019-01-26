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
class TempRecordReceiptSpec extends Specification {

	void "test validation"() {
		when: "we have a TempRecordReceipt"
		TempRecordReceipt receipt = new TempRecordReceipt()

		then:
		receipt.validate() == false
		receipt.errors.errorCount == 2

		when: "we we fill in the required fields except for phone number"
		receipt.apiId = "testing"

		then:
		receipt.validate() == false
		receipt.errors.errorCount == 1

		when: "we set an invalid phone number"
		receipt.contactNumber = new PhoneNumber(number:"invalid123")

		then:
		receipt.validate() == false
		receipt.errors.errorCount == 1

		when: "we set a valid phone number"
		receipt.contactNumber = new PhoneNumber(number:"222 333 4444")

		then:
		receipt.validate() == true

		when: "negative number of segments"
		receipt.numSegments = -88

		then: "invalid"
		receipt.validate() == false
		receipt.errors.getFieldErrorCount("numSegments") == 1

		when: "null number of segments"
		receipt.numSegments = null

		then: "valid again"
		receipt.validate() == true
	}
}
