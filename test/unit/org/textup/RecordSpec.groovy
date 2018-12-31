package org.textup

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import grails.validation.ValidationErrors
import org.joda.time.DateTime
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.IncomingText
import spock.lang.Shared
import spock.lang.Specification

// Need to mock Organization and Location to enable rolling back transaction on resultFactory failure
@Domain([CustomAccountDetails, Record, RecordItem, RecordText, RecordCall, RecordItemReceipt, Organization, Location,
    RecordNote, RecordNoteRevision, Location, MediaInfo, MediaElement, MediaElementVersion])
@TestMixin(HibernateTestMixin)
class RecordSpec extends Specification {

	static doWithSpring = {
        resultFactory(ResultFactory)
    }

	def setup() {
        IOCUtils.metaClass."static".getMessageSource = { -> TestUtils.mockMessageSource() }
	}

    void "test adding items errors"() {
    	given: "a valid record"
    	Record rec = new Record()
    	rec.save(flush:true, failOnError:true)

    	when: "we add a missing item"
    	Result res = rec.add(null, null)

    	then:
    	res.success == false
        res.status == ResultStatus.BAD_REQUEST
        res.errorMessages.size() == 1
    	res.errorMessages[0] == "record.noRecordItem"
    }

    void "test adding items, keeping lastRecordActivity up-to-date"() {
        given: "a valid record"
        Record rec = new Record()
        rec.save(flush:true, failOnError:true)

        DateTime currentTimestamp = rec.lastRecordActivity

        when: "add an outgoing text"
        assert rec.storeOutgoingText("hello").success
        rec.save(flush: true, failOnError: true)

        then: "lastRecordActivity is updated"
        !rec.lastRecordActivity.isBefore(currentTimestamp)
        RecordItem.countByRecord(rec) == 1
        RecordText.countByRecord(rec) == 1
        RecordCall.countByRecord(rec) == 0

        when: "add an outgoing call"
        currentTimestamp = rec.lastRecordActivity
        assert rec.storeOutgoingCall().success

        then: "lastRecordActivity is updated"
        !rec.lastRecordActivity.isBefore(currentTimestamp)
        RecordItem.countByRecord(rec) == 2
        RecordText.countByRecord(rec) == 1
        RecordCall.countByRecord(rec) == 1

        when: "add an incoming text"
        currentTimestamp = rec.lastRecordActivity
        IncomingText text = new IncomingText(apiId: "apiId", message: "hi", numSegments: 88)
        IncomingSession session1 = new IncomingSession(phone: new Phone(), numberAsString: "6261231234")
        Result<RecordText> textRes = rec.storeIncomingText(text, session1)

        then: "lastRecordActivity is updated"
        textRes.success == true
        textRes.payload.receipts.size() == 1
        textRes.payload.receipts[0].contactNumberAsString == session1.numberAsString
        textRes.payload.receipts[0].status == ReceiptStatus.SUCCESS
        textRes.payload.numSegments == text.numSegments
        !rec.lastRecordActivity.isBefore(currentTimestamp)
        RecordItem.countByRecord(rec) == 3
        RecordText.countByRecord(rec) == 2
        RecordCall.countByRecord(rec) == 1

        when: "add an incoming call"
        currentTimestamp = rec.lastRecordActivity
        Result<RecordCall> callRes = rec.storeIncomingCall("apiId", session1)

        then: "lastRecordActivity is updated"
        callRes.success == true
        callRes.payload.receipts[0].contactNumberAsString == session1.numberAsString
        callRes.payload.receipts[0].status == ReceiptStatus.SUCCESS
        !rec.lastRecordActivity.isBefore(currentTimestamp)
        RecordItem.countByRecord(rec) == 4
        RecordText.countByRecord(rec) == 2
        RecordCall.countByRecord(rec) == 2
    }

    void "test checking for unread info"() {
        given:
        Record rec1 = new Record()
        rec1.save(flush: true, failOnError: true)

        DateTime dt = DateTime.now()
        RecordItem rItem1 = new RecordItem(record: rec1, whenCreated: dt)
        rItem1.save(flush: true, failOnError: true)

        expect:
        rec1.hasUnreadInfo(dt.minusDays(1)) == true
        rec1.hasUnreadInfo(dt.plusDays(1)) == false
    }

    void "test building unread info"() {
        given:
        Record rec1 = new Record()
        rec1.save(flush: true, failOnError: true)

        RecordText rText1 = rec1.storeOutgoingText("hello").payload,
            rText2 = rec1.storeOutgoingText("hello").payload
        rText1.outgoing = false
        rText2.outgoing = true
        RecordCall rCall1 = rec1.storeOutgoingCall().payload,
            rCall2 = rec1.storeOutgoingCall().payload,
            rCall3 = rec1.storeOutgoingCall().payload
        rCall1.outgoing = false
        rCall2.outgoing = false
        rCall2.voicemailInSeconds = 22
        rCall2.hasAwayMessage = true
        rCall3.outgoing = true
        RecordNote rNote1 = new RecordNote(record:rec1)
        [rText1, rText2, rCall1, rCall2, rCall3, rNote1]*.save(flush:true, failOnError:true)

        when:
        UnreadInfo uInfo = rec1.getUnreadInfo(null)

        then: "unread info only includes counts for incoming items"
        uInfo.numTexts == 1 // excludes outgoing text
        uInfo.numCalls == 1 // excludes outgoing call and call with voicemail
        uInfo.numVoicemails == 1
    }
}
