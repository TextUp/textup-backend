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

	void "test validation"() {
		when: "we have a RecordItemReceipt"
		Record rec = new Record()
		RecordItem rItem = new RecordItem(record:rec)
		RecordItemReceipt receipt = new RecordItemReceipt()

		then:
		receipt.validate() == false
		receipt.errors.errorCount == 3

		when: "we we fill in the required fields except for phone number"
		receipt.item = rItem
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
		String numAsString = "2223334444"
		receipt.contactNumber = new PhoneNumber(number:numAsString)

		then:
		receipt.validate() == true
		receipt.contactNumberAsString == numAsString

		when: "negative number of segments"
		receipt.numBillable = -88

		then: "invalid"
		receipt.validate() == false
		receipt.errors.getFieldErrorCount("numBillable") == 1

		when: "null number of segments"
		receipt.numBillable = null

		then: "valid again"
		receipt.validate() == true
	}

    void "test named queries for status and deletion"() {
    	when: "we have a record item with several RecordItemReceipts"
		Record rec = new Record()
		rec.save(flush: true, failOnError: true)
		RecordItem rItem = new RecordItem(record:rec)
		rItem.save(flush: true, failOnError: true)
		int baseline = RecordItemReceipt.count(),
			numFailed = 3,
			numPending = 4,
			numSuccess = 5
		numFailed.times { rItem.addToReceipts(TestUtils.buildReceipt(ReceiptStatus.FAILED)) }
        numPending.times { rItem.addToReceipts(TestUtils.buildReceipt(ReceiptStatus.PENDING)) }
        numSuccess.times { rItem.addToReceipts(TestUtils.buildReceipt(ReceiptStatus.SUCCESS)) }
        rItem.save(flush: true, failOnError: true)

		then:
		RecordItemReceipt.countByItem(rItem) == numFailed + numPending + numSuccess
		RecordItemReceipt.countByItemAndStatus(rItem, ReceiptStatus.FAILED) == numFailed
		RecordItemReceipt.countByItemAndStatus(rItem, ReceiptStatus.PENDING) == numPending
		RecordItemReceipt.countByItemAndStatus(rItem, ReceiptStatus.SUCCESS) == numSuccess

		when: "we delete all added RecordItemReceipts"
		//Cannot just call delete directly, doing so would re-save object on cascade
		//must first clear the receipts hasMany relationship in the RecordItem
		rItem.receipts.clear()
		RecordItemReceipt.findAllByItem(rItem)*.delete()
		rItem.save(flush: true, failOnError: true)

		then:
		RecordItemReceipt.countByItem(rItem) == baseline
    }
}
