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

// TODO

@Domain([AnnouncementReceipt, ContactNumber, CustomAccountDetails, FeaturedAnnouncement,
    FutureMessage, GroupPhoneRecord, IncomingSession, IndividualPhoneRecord, Location, MediaElement,
    MediaElementVersion, MediaInfo, Organization, OwnerPolicy, Phone, PhoneNumberHistory,
    PhoneOwnership, PhoneRecord, PhoneRecordMembers, Record, RecordCall, RecordItem,
    RecordItemReceipt, RecordNote, RecordNoteRevision, RecordText, Role, Schedule,
    SimpleFutureMessage, Staff, StaffRole, Team, Token])
@TestMixin(HibernateTestMixin)
class RecordItemSpec extends CustomSpec {

    // static doWithSpring = {
    //     resultFactory(ResultFactory)
    // }

    // def setup() {
    //     setupData()
    // }

    // def cleanup() {
    //     cleanupData()
    // }

    // void "test constraints"() {
    // 	when: "we have a record item"
    // 	Record rec = new Record()
    // 	RecordItem rItem = new RecordItem()

    // 	then:
    // 	rItem.validate() == false
    // 	rItem.errors.errorCount == 1

    // 	when: "we add all other fields"
    // 	rItem.record = rec

    // 	then:
    // 	rItem.validate() == true

    //     when: "add a note contents that is too long"
    //     rItem.noteContents = TestUtils.buildVeryLongString()

    //     then: "shared contraint on the noteContents field is executed"
    //     rItem.validate() == false
    //     rItem.errors.getFieldErrorCount("noteContents") == 1

    //     when: "number notified is negative"
    //     rItem.noteContents = "acceptable length string"
    //     rItem.numNotified = -88

    //     then: "shared contraint on the noteContents field is executed"
    //     rItem.validate() == false
    //     rItem.errors.getFieldErrorCount("numNotified") == 1
    // }

    // void "test adding receipt"() {
    //     given: "a valid record item"
    //     Record rec = new Record()
    //     RecordItem rItem = new RecordItem(record:rec)
    //     assert rItem.validate()
    //     TempRecordReceipt temp1 = TestUtils.buildTempReceipt()

    //     when:
    //     rItem.addReceipt(temp1)

    //     then:
    //     rItem.receipts.size() == 1
    //     rItem.receipts[0].status != null
    //     rItem.receipts[0].apiId != null
    //     rItem.receipts[0].contactNumberAsString != null
    //     rItem.receipts[0].numBillable != null
    //     rItem.receipts[0].status == temp1.status
    //     rItem.receipts[0].apiId == temp1.apiId
    //     rItem.receipts[0].contactNumberAsString == temp1.contactNumberAsString
    //     rItem.receipts[0].numBillable == temp1.numSegments
    // }

    // void "test cascading validation and saving to media object"() {
    //     given:
    //     MediaElement e1 = TestUtils.buildMediaElement()
    //     MediaInfo mInfo = new MediaInfo()
    //     mInfo.addToMediaElements(e1)
    //     assert mInfo.validate()
    //     Record rec = new Record()
    //     rec.save(flush:true, failOnError:true)
    //     RecordItem rItem = new RecordItem(record:rec)
    //     assert rItem.validate()
    //     int miBaseline = MediaInfo.count()
    //     int meBaseline = MediaElement.count()
    //     int riBaseline = RecordItem.count()

    //     when:
    //     rItem.media = mInfo

    //     then:
    //     rItem.validate() == true
    //     MediaInfo.count() == miBaseline
    //     MediaElement.count() == meBaseline
    //     RecordItem.count() == riBaseline

    //     when:
    //     e1.whenCreated = null

    //     then:
    //     rItem.validate() == false
    //     rItem.errors.getFieldErrorCount("media.mediaElements.0.whenCreated") == 1
    //     MediaInfo.count() == miBaseline
    //     MediaElement.count() == meBaseline
    //     RecordItem.count() == riBaseline

    //     when:
    //     e1.whenCreated = DateTime.now()
    //     assert rItem.save(flush: true, failOnError: true)

    //     then:
    //     MediaInfo.count() == miBaseline + 1
    //     MediaElement.count() == meBaseline + 1
    //     RecordItem.count() == riBaseline + 1
    // }

    // void "test adding author"() {
    //     given: "a valid record item"
    //     Record rec = new Record()
    //     RecordItem rItem = new RecordItem(record:rec)
    //     assert rItem.validate()

    //     when: "we add an author"
    //     rItem.author = new Author(id:88L, name:"hello", type:AuthorType.STAFF)

    //     then: "fields are correctly populated"
    //     rItem.validate() == true
    //     rItem.authorName == "hello"
    //     rItem.authorId == 88L
    //     rItem.authorType == AuthorType.STAFF
    // }

    // void "test finding record items by api id"() {
    //     given: "many valid record items with receipts with same apiId"
    //     String apiId = UUID.randomUUID().toString()
    //     Record rec = new Record()
    //     rec.save(flush:true, failOnError:true)
    //     RecordItem rItem1 = new RecordItem(record:rec),
    //         rItem2 = new RecordItem(record:rec)
    //     [rItem1, rItem2].each { RecordItem rItem ->
    //         rItem.addToReceipts(new RecordItemReceipt(status:ReceiptStatus.SUCCESS,
    //             apiId:apiId, contactNumberAsString:"1112223333"))
    //         rItem.save(flush:true, failOnError:true)
    //     }

    //     when: "we find record items by api id"
    //     List<RecordItem> rItems = RecordItem.findEveryByApiId(apiId)

    //     then: "should find all record items"
    //     rItems.size() == 2
    //     [rItem1, rItem2].every { it in rItems }
    // }

    // void "test building detached criteria for records"() {
    //     given: "valid record items"
    //     Record rec = new Record()
    //     rec.save(flush:true, failOnError:true)
    //     RecordItem rItem1 = new RecordItem(record:rec),
    //         rItem2 = new RecordItem(record:rec)
    //     [rItem1, rItem2]*.save(flush:true, failOnError:true)

    //     when: "build detached criteria for these items"
    //     DetachedCriteria<RecordItem> detachedCrit = RecordItem.forRecords([rec])
    //     List<RecordItem> itemList = detachedCrit.list()
    //     Collection<Long> targetIds = [rItem1, rItem2]*.id

    //     then: "we are able to fetch these items back from the db"
    //     itemList.size() == 2
    //     itemList.every { it.id in targetIds }
    // }

    // void "test retrieving items from a record"() {
    //     given: "a record with items of various ages"
    //     Record rec = new Record()
    //     rec.save(flush:true, failOnError:true)
    //     RecordItem nowItem = rec.add(new RecordItem(), null).payload,
    //         lWkItem = rec.add(new RecordItem(), null).payload,
    //         yestItem = rec.add(new RecordItem(), null).payload,
    //         twoDItem = rec.add(new RecordItem(), null).payload,
    //         thrDItem = rec.add(new RecordItem(), null).payload
    //     rec.save(flush:true, failOnError:true)
    //     assert RecordItem.countByRecord(rec) == 5
    //     //can't set the whenCreated in the constructor
    //     lWkItem.whenCreated = DateTime.now().minusWeeks(1)
    //     yestItem.whenCreated = DateTime.now().minusDays(1)
    //     twoDItem.whenCreated = DateTime.now().minusDays(2)
    //     thrDItem.whenCreated = DateTime.now().minusDays(3)
    //     thrDItem.save(flush:true, failOnError:true)

    //     when: "we get items between a date range"
    //     List<RecordItem> items = RecordItem
    //         .forRecordIdsWithOptions([rec.id], DateTime.now().minusDays(4), DateTime.now().minusHours(22))
    //         .build(RecordItem.buildForSort())
    //         .list(max:2, offset:1)

    //     then:
    //     items.size() == 2 // notice offset 1
    //     items[0] == twoDItem // newer item
    //     items[1] == thrDItem // older item

    //     when: "we get items since a certain date"
    //     items = RecordItem
    //         .forRecordIdsWithOptions([rec.id], DateTime.now().minusWeeks(4))
    //         .build(RecordItem.buildForSort())
    //         .list(max:3, offset:1)

    //     then:
    //     items.size() == 3 // notice offset 1
    //     items[0] == yestItem // newer item
    //     items[1] == twoDItem
    //     items[2] == thrDItem // older item
    // }

    // void "test getting items a record by type"() {
    //     given: "record with items of all types"
    //     Record rec1 = new Record()
    //     rec1.save(flush:true, failOnError:true)

    //     RecordText rText1 = rec1.storeOutgoingText("hello").payload
    //     RecordCall rCall1 = rec1.storeOutgoingCall().payload,
    //         rCall2 = rec1.storeOutgoingCall().payload
    //     rCall2.voicemailInSeconds = 22
    //     rCall2.hasAwayMessage = true
    //     RecordNote rNote1 = new RecordNote(record:rec1)
    //     [rText1, rCall1, rCall2, rNote1]*.save(flush:true, failOnError:true)

    //     DateTime afterDt = DateTime.now().minusWeeks(3)
    //     DateTime beforeDt = DateTime.now().plusWeeks(3)

    //     expect:
    //     RecordItem.forRecordIdsWithOptions([rec1.id])
    //         .build(RecordItem.buildForSort())
    //         .list()
    //         *.id.every { it in [rText1, rCall1, rCall2, rNote1]*.id }

    //     RecordItem.forRecordIdsWithOptions([rec1.id], null, null, [RecordCall])
    //         .build(RecordItem.buildForSort())
    //         .list()
    //         *.id.every { it in [rCall1, rCall2]*.id }
    //     RecordItem.forRecordIdsWithOptions([rec1.id], null, null, [RecordText])
    //         .build(RecordItem.buildForSort())
    //         .list()
    //         *.id.every { it in [rText1]*.id }
    //     RecordItem.forRecordIdsWithOptions([rec1.id], null, null, [RecordNote])
    //         .build(RecordItem.buildForSort())
    //         .list()
    //         *.id.every { it in [rNote1]*.id }
    //     RecordItem.forRecordIdsWithOptions([rec1.id], null, null, [RecordCall, RecordText])
    //         .build(RecordItem.buildForSort())
    //         .list()
    //         *.id.every { it in [rText1, rCall1, rCall2]*.id }
    //     RecordItem.forRecordIdsWithOptions([rec1.id], null, null, [RecordText, RecordNote])
    //         .build(RecordItem.buildForSort())
    //         .list()
    //         *.id.every { it in [rText1, rNote1]*.id }
    //     RecordItem.forRecordIdsWithOptions([rec1.id], null, null, [RecordCall, RecordNote])
    //         .build(RecordItem.buildForSort())
    //         .list()
    //         *.id.every { it in [rCall1, rNote1, rCall2]*.id }
    //     RecordItem.forRecordIdsWithOptions([rec1.id], null, null, [RecordCall, RecordText, RecordNote])
    //         .build(RecordItem.buildForSort())
    //         .list()
    //         *.id.every { it in [rText1, rCall1, rCall2, rNote1]*.id }

    //     RecordItem.forRecordIdsWithOptions([rec1.id], afterDt, null, [RecordCall])
    //         .build(RecordItem.buildForSort())
    //         .list()
    //         *.id.every { it in [rCall1, rCall2]*.id }
    //     RecordItem.forRecordIdsWithOptions([rec1.id], afterDt, null, [RecordText])
    //         .build(RecordItem.buildForSort())
    //         .list()
    //         *.id.every { it in [rText1]*.id }
    //     RecordItem.forRecordIdsWithOptions([rec1.id], afterDt, null, [RecordNote])
    //         .build(RecordItem.buildForSort())
    //         .list()
    //         *.id.every { it in [rNote1]*.id }
    //     RecordItem.forRecordIdsWithOptions([rec1.id], afterDt, null, [RecordCall, RecordText])
    //         .build(RecordItem.buildForSort())
    //         .list()
    //         *.id.every { it in [rText1, rCall1, rCall2]*.id }
    //     RecordItem.forRecordIdsWithOptions([rec1.id], afterDt, null, [RecordText, RecordNote])
    //         .build(RecordItem.buildForSort())
    //         .list()
    //         *.id.every { it in [rText1, rNote1]*.id }
    //     RecordItem.forRecordIdsWithOptions([rec1.id], afterDt, null, [RecordCall, RecordNote])
    //         .build(RecordItem.buildForSort())
    //         .list()
    //         *.id.every { it in [rCall1, rCall2, rNote1]*.id }
    //     RecordItem.forRecordIdsWithOptions([rec1.id], afterDt, null, [RecordCall, RecordText, RecordNote])
    //         .build(RecordItem.buildForSort())
    //         .list()
    //         *.id.every { it in [rText1, rCall1, rCall2, rNote1]*.id }

    //     RecordItem.forRecordIdsWithOptions([rec1.id], afterDt, beforeDt, [RecordCall])
    //         .build(RecordItem.buildForSort())
    //         .list()
    //         *.id.every { it in [rCall1, rCall2]*.id }
    //     RecordItem.forRecordIdsWithOptions([rec1.id], afterDt, beforeDt, [RecordText])
    //         .build(RecordItem.buildForSort())
    //         .list()
    //         *.id.every { it in [rText1]*.id }
    //     RecordItem.forRecordIdsWithOptions([rec1.id], afterDt, beforeDt, [RecordNote])
    //         .build(RecordItem.buildForSort())
    //         .list()
    //         *.id.every { it in [rNote1]*.id }
    //     RecordItem.forRecordIdsWithOptions([rec1.id], afterDt, beforeDt, [RecordCall, RecordText])
    //         .build(RecordItem.buildForSort())
    //         .list()
    //         *.id.every { it in [rText1, rCall1, rCall2]*.id }
    //     RecordItem.forRecordIdsWithOptions([rec1.id], afterDt, beforeDt, [RecordText, RecordNote])
    //         .build(RecordItem.buildForSort())
    //         .list()
    //         *.id.every { it in [rText1, rNote1]*.id }
    //     RecordItem.forRecordIdsWithOptions([rec1.id], afterDt, beforeDt, [RecordCall, RecordNote])
    //         .build(RecordItem.buildForSort())
    //         .list()
    //         *.id.every { it in [rCall1, rCall2, rNote1]*.id }
    //     RecordItem.forRecordIdsWithOptions([rec1.id], afterDt, beforeDt, [RecordCall, RecordText, RecordNote])
    //         .build(RecordItem.buildForSort())
    //         .list()
    //         *.id.every { it in [rText1, rCall1, rCall2, rNote1]*.id }
    // }

    // void "test fetching records sort order"() {
    //     given: "records"
    //     Record rec1 = new Record()
    //     rec1.save(flush: true, failOnError: true)

    //     RecordItem rItem1 = new RecordItem(record: rec1, whenCreated: DateTime.now())
    //     RecordItem rItem2 = new RecordItem(record: rec1, whenCreated: DateTime.now().plusDays(1))
    //     RecordItem rItem3 = new RecordItem(record: rec1, whenCreated: DateTime.now().plusDays(2))
    //     [rItem1, rItem2, rItem3]*.save(flush: true, failOnError: true)

    //     expect:
    //     RecordItem.forRecordIdsWithOptions([rec1.id])
    //         .build(RecordItem.buildForSort())
    //         .list()*.id == [rItem3, rItem2, rItem1]*.id
    //     RecordItem.forRecordIdsWithOptions([rec1.id])
    //         .build(RecordItem.buildForSort(false))
    //         .list()*.id == [rItem1, rItem2, rItem3]*.id
    // }

    // void "test fetching all records for a phone id"() {
    //     given: "phone + records"
    //     Phone p1 = new Phone(numberAsString: TestUtils.randPhoneNumberString())
    //     p1.updateOwner(t1)
    //     p1.save(flush:true, failOnError:true)

    //     Record rec1 = new Record()
    //     Record rec2 = new Record()
    //     [rec1, rec2]*.save(flush: true, failOnError: true)

    //     Contact c1 = p1.createContact([:], [TestUtils.randPhoneNumberString()]).payload
    //     c1.record = rec1
    //     ContactTag ct1 = p1.createTag(name: TestUtils.randString()).payload
    //     ct1.record = rec2
    //     [c1, ct1]*.save(flush: true, failOnError: true)

    //     RecordItem rItem1 = new RecordItem(record: rec1)
    //     RecordItem rItem2 = new RecordItem(record: rec2)
    //     [rItem1, rItem2]*.save(flush: true, failOnError: true)

    //     expect:
    //     RecordItem.forPhoneIdWithOptions(p1.id)
    //         .build(RecordItem.buildForSort())
    //         .list()
    //         *.id.every { it in [rItem1, rItem2]*.id }
    // }
}
