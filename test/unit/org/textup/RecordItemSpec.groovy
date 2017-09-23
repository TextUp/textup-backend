package org.textup

import grails.gorm.DetachedCriteria
import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import java.util.UUID
import org.textup.type.AuthorType
import org.textup.type.ReceiptStatus
import org.textup.validator.Author
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

@Domain([Record, RecordItem, RecordText, RecordCall, RecordItemReceipt])
@TestMixin(HibernateTestMixin)
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

    	then:
    	rItem.validate() == true
    }

    void "test adding author"() {
        given: "a valid record item"
        Record rec = new Record()
        RecordItem rItem = new RecordItem(record:rec)
        assert rItem.validate()

        when: "we add an author"
        rItem.author = new Author(id:88L, name:"hello", type:AuthorType.STAFF)

        then: "fields are correctly populated"
        rItem.validate() == true
        rItem.authorName == "hello"
        rItem.authorId == 88L
        rItem.authorType == AuthorType.STAFF
    }

    void "test finding record items by api id"() {
        given: "many valid record items with receipts with same apiId"
        String apiId = UUID.randomUUID().toString()
        Record rec = new Record()
        rec.save(flush:true, failOnError:true)
        RecordItem rItem1 = new RecordItem(record:rec),
            rItem2 = new RecordItem(record:rec)
        [rItem1, rItem2].each { RecordItem rItem ->
            rItem.addToReceipts(new RecordItemReceipt(status:ReceiptStatus.SUCCESS,
                apiId:apiId, receivedByAsString:"1112223333"))
            rItem.save(flush:true, failOnError:true)
        }

        when: "we find record items by api id"
        List<RecordItem> rItems = RecordItem.findEveryByApiId(apiId)

        then: "should find all record items"
        rItems.size() == 2
        [rItem1, rItem2].every { it in rItems }
    }

    void "test building detached criteria for records"() {
        given: "valid record items"
        Record rec = new Record()
        rec.save(flush:true, failOnError:true)
        RecordItem rItem1 = new RecordItem(record:rec),
            rItem2 = new RecordItem(record:rec)
        [rItem1, rItem2]*.save(flush:true, failOnError:true)

        when: "build detached criteria for these items"
        DetachedCriteria<RecordItem> detachedCrit = RecordItem.buildForRecords([rec])
        List<RecordItem> itemList = detachedCrit.list()
        Collection<Long> targetIds = [rItem1, rItem2]*.id

        then: "we are able to fetch these items back from the db"
        itemList.size() == 2
        itemList.every { it.id in targetIds }
    }
}
