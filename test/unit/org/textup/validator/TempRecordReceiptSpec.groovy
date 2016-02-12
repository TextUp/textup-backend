package org.textup.validator

import grails.test.mixin.support.GrailsUnitTestMixin
import grails.test.mixin.TestMixin
import spock.lang.Shared
import spock.lang.Specification

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
		receipt.receivedBy = new PhoneNumber(number:"invalid123")

		then:
		receipt.validate() == false
		receipt.errors.errorCount == 1

		when: "we set a valid phone number"
		receipt.receivedBy = new PhoneNumber(number:"222 333 4444")

		then:
		receipt.validate() == true
	}
}
