package org.textup

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

@Domain([Record, RecordItem, RecordNote, RecordText, RecordCall, 
	RecordItemReceipt, PhoneNumber])
@TestMixin(HibernateTestMixin)
@Unroll
class RecordItemSpec extends Specification {

    void "test constraints"() {
    	when: "we have a record item"
    	Record rec = new Record() 
    	RecordItem rItem = new RecordItem()

    	then: 
    	rItem.validate() == false 
    	rItem.errors.errorCount == 1

    	when: "we add all other fields"
    	rItem.record = rec 
    	rItem.authorName = "name"
    	rItem.authorId = 12L

    	then: 
    	rItem.validate()
    	rItem.outgoing == !rItem.incoming
    }

    private void addReceiptToItem(RecordItem rItem) {
		RecordItemReceipt receipt = new RecordItemReceipt(apiId:"test")
		receipt.receivedByAsString = "222 333 4444"
		rItem.addToReceipts(receipt)
		receipt.save(flush:true)
	}
    void "test deleting"() {
    	when: "we have a record item with some receipts"
    	Record rec = new Record() 
    	rec.save(flush:true)
    	RecordItem rItem = new RecordItem(record:rec)
    	rItem.save(flush:true)
    	int numReceipts = 10
    	numReceipts.times { addReceiptToItem(rItem) }

    	then: 
    	RecordItemReceipt.countByItem(rItem) == numReceipts
    	RecordItem.countByRecord(rec) == 1

    	when: 
    	int baseline = RecordItemReceipt.count()
    	rItem.delete(flush:true)

    	then:
    	RecordItemReceipt.countByItem(rItem) == baseline - numReceipts
    	RecordItem.countByRecord(rec) == 0
    }
}
