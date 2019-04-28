package org.textup

import grails.test.mixin.*
import grails.test.mixin.gorm.*
import grails.test.mixin.hibernate.*
import grails.test.mixin.support.*
import grails.test.runtime.*
import grails.validation.*
import org.joda.time.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*
import spock.lang.*

@Domain([AnnouncementReceipt, ContactNumber, CustomAccountDetails, FeaturedAnnouncement,
    FutureMessage, GroupPhoneRecord, IncomingSession, IndividualPhoneRecord, Location, MediaElement,
    MediaElementVersion, MediaInfo, Organization, OwnerPolicy, Phone, PhoneNumberHistory,
    PhoneOwnership, PhoneRecord, PhoneRecordMembers, Record, RecordCall, RecordItem,
    RecordItemReceipt, RecordNote, RecordNoteRevision, RecordText, Role, Schedule,
    SimpleFutureMessage, Staff, StaffRole, Team, Token])
@TestMixin(HibernateTestMixin)
@Unroll
class RecordItemReceiptSpec extends Specification {

	static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

	void "test validation"() {
		when: "we have a RecordItemReceipt"
		RecordItem rItem1 = TestUtils.buildRecordItem()
		RecordItemReceipt rpt1 = new RecordItemReceipt()

		then:
		rpt1.validate() == false

		when: "we we fill in the required fields except for phone number"
		rpt1.item = rItem1
		rpt1.apiId = "testing"
		rpt1.status = ReceiptStatus.PENDING

		then:
		rpt1.validate() == false

		when: "we set an invalid phone number"
		rpt1.contactNumber = PhoneNumber.create("invalid123")

		then:
		rpt1.validate() == false

		when: "we set a valid phone number"
		rpt1.contactNumber = TestUtils.randPhoneNumber()

		then:
		rpt1.validate() == true

		when: "negative number of segments"
		rpt1.numBillable = -88

		then: "invalid"
		rpt1.validate() == false
		rpt1.errors.getFieldErrorCount("numBillable") == 1

		when: "null number of segments"
		rpt1.numBillable = null

		then: "valid again"
		rpt1.validate() == true
	}

	void "test static creation"() {
		given:
		RecordItem rItem1 = TestUtils.buildRecordItem()
		String apiId = TestUtils.randString()
		ReceiptStatus stat = ReceiptStatus.values()[0]
		PhoneNumber pNum = TestUtils.randPhoneNumber()

		when:
		Result res = RecordItemReceipt.tryCreate(null, null, null, null)

		then:
		res.status == ResultStatus.UNPROCESSABLE_ENTITY

		when:
		res = RecordItemReceipt.tryCreate(rItem1, apiId, stat, pNum)

		then:
		res.status == ResultStatus.CREATED
		res.payload.item == rItem1
		res.payload.apiId == apiId
		res.payload.status == stat
		res.payload.numBillable == null
		res.payload.contactNumberAsString == pNum.number
	}
}
