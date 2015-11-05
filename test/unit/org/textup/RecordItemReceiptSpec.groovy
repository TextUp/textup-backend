package org.textup

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import org.joda.time.DateTime
import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

@Domain([Record, RecordItem, RecordNote, RecordText, RecordCall, 
	RecordItemReceipt, PhoneNumber])
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
		receipt.receivedByAsString = "invalid123"

		then:
		receipt.validate() == false 
		receipt.errors.errorCount == 1

		when: "we set a valid phone number"
		receipt.receivedByAsString = "222 333 4444"

		then: 
		receipt.validate() == true 
	}

	private void addReceiptToItem(RecordItem rItem, String status) {
		RecordItemReceipt receipt = new RecordItemReceipt(apiId:"test", status:status)
		receipt.receivedByAsString = "222 333 4444"
		rItem.addToReceipts(receipt)
		receipt.save(flush:true)
	}
    void "test named queries for status and deletion"() {
    	when: "we have a record item with several RecordItemReceipts"
		Record rec = new Record()
		rec.save(flush:true)
		RecordItem rItem = new RecordItem(record:rec)
		rItem.save(flush:true)
		int numFailed = 3, numPending = 4, numSuccess = 5
		numFailed.times { addReceiptToItem(rItem, Constants.RECEIPT_FAILED) }
		numPending.times { addReceiptToItem(rItem, Constants.RECEIPT_PENDING) }
		numSuccess.times { addReceiptToItem(rItem, Constants.RECEIPT_SUCCESS) }

		then:
		RecordItemReceipt.countByItem(rItem) == numFailed + numPending + numSuccess 
		RecordItemReceipt.failed(rItem).count() == numFailed
		RecordItemReceipt.pending(rItem).count() == numPending
		RecordItemReceipt.success(rItem).count() == numSuccess

		when: "we delete all added RecordItemReceipts"
		//Cannot just call delete directly, doing so would re-save object on cascade
		//must first clear the receipts hasMany relationship in the RecordItem
		rItem.receipts.clear()
		RecordItemReceipt.findAllByItem(rItem)*.delete()
		rItem.save(flush:true)

		then: 
		RecordItemReceipt.countByItem(rItem) == 0
    }
}
