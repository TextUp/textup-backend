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

	static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

	void "test validation"() {
		given:
		String apiId = TestUtils.randString()
		PhoneNumber validNum = TestUtils.randPhoneNumber()
		PhoneNumber invalidNum = PhoneNumber.create("invalid num")

		when:
		Result res = TempRecordReceipt.tryCreate(null, null)

		then:
		res.status == ResultStatus.UNPROCESSABLE_ENTITY

		when:
		res = TempRecordReceipt.tryCreate(apiId, invalidNum)

		then:
		res.status == ResultStatus.UNPROCESSABLE_ENTITY

		when:
		res = TempRecordReceipt.tryCreate(apiId, validNum)

		then:
		res.status == ResultStatus.CREATED
		res.payload.contactNumber.number == validNum.number
		res.payload.apiId == apiId
		res.payload.numBillable == null
		res.payload.status == ReceiptStatus.PENDING // default

		when:
		res.payload.numBillable = TestUtils.randIntegerUpTo(88, true)
		res.payload.status = ReceiptStatus.SUCCESS

		then:
		res.payload.numBillable > 0
		res.payload.status == ReceiptStatus.SUCCESS
	}
}
