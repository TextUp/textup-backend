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
class RecordItemSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

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

        when: "add a note contents that is too long"
        rItem.noteContents = TestUtils.buildVeryLongString()

        then: "shared contraint on the noteContents field is executed"
        rItem.validate() == false
        rItem.errors.getFieldErrorCount("noteContents") == 1

        when: "number notified is negative"
        rItem.noteContents = "acceptable length string"
        rItem.numNotified = -88

        then: "shared contraint on the noteContents field is executed"
        rItem.validate() == false
        rItem.errors.getFieldErrorCount("numNotified") == 1
    }

    void "test adding receipt"() {
        given: "a valid record item"
        Record rec = new Record()
        RecordItem rItem = new RecordItem(record:rec)
        assert rItem.validate()
        TempRecordReceipt temp1 = TestUtils.buildTempReceipt()

        when:
        rItem.addReceipt(temp1)

        then:
        rItem.receipts.size() == 1
        rItem.receipts[0].status != null
        rItem.receipts[0].apiId != null
        rItem.receipts[0].contactNumberAsString != null
        rItem.receipts[0].numBillable != null
        rItem.receipts[0].status == temp1.status
        rItem.receipts[0].apiId == temp1.apiId
        rItem.receipts[0].contactNumberAsString == temp1.contactNumber.number
        rItem.receipts[0].numBillable == temp1.numBillable
    }

    void "test cascading validation and saving to media object"() {
        given:
        // these are already flushed and saved
        MediaElement el1 = TestUtils.buildMediaElement()
        Record rec1 = TestUtils.buildRecord()
        // these are still transient
        MediaInfo mInfo = new MediaInfo()
        mInfo.addToMediaElements(el1)
        assert mInfo.validate()
        RecordItem rItem = new RecordItem(record:rec1)
        assert rItem.validate()
        int miBaseline = MediaInfo.count()
        int riBaseline = RecordItem.count()

        when:
        rItem.media = mInfo

        then:
        rItem.validate() == true
        MediaInfo.count() == miBaseline
        RecordItem.count() == riBaseline

        when:
        el1.whenCreated = null

        then:
        rItem.validate() == false
        rItem.errors.getFieldErrorCount("media.mediaElements.0.whenCreated") == 1
        MediaInfo.count() == miBaseline
        RecordItem.count() == riBaseline

        when:
        el1.whenCreated = DateTime.now()
        assert rItem.save(flush: true, failOnError: true)

        then:
        MediaInfo.count() == miBaseline + 1
        RecordItem.count() == riBaseline + 1
    }

    void "test adding author"() {
        given: "a valid record item"
        Record rec = new Record()
        RecordItem rItem = new RecordItem(record:rec)
        assert rItem.validate()

        when: "we add an author"
        rItem.author = Author.create(88L, "hello", AuthorType.STAFF)

        then: "fields are correctly populated"
        rItem.validate() == true
        rItem.authorName == "hello"
        rItem.authorId == 88L
        rItem.authorType == AuthorType.STAFF
    }

    void "test equals (delegates to compareTo) and hashCode"() {
        given:
        DateTime dt = DateTime.now()
        RecordItem rItem1 = TestUtils.buildRecordItem()
        rItem1.whenCreated = dt
        RecordItem rItem2 = TestUtils.buildRecordItem()
        rItem2.whenCreated = dt

        RecordItem.withSession { it.flush() }

        expect: "all three are consistent"
        rItem1 == rItem1
        rItem1.equals(rItem1)
        rItem1.compareTo(rItem1) == 0
        rItem1.hashCode() == rItem1.hashCode()

        rItem1 != rItem2
        !rItem1.equals(rItem2)
        rItem1.compareTo(rItem2) != 0
        rItem1.hashCode() != rItem2.hashCode()
    }
}
