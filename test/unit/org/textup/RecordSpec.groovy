package org.textup

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import grails.validation.ValidationErrors
import org.joda.time.DateTime
import org.textup.util.TestHelpers
import org.textup.validator.IncomingText
import spock.lang.Shared
import spock.lang.Specification

// Need to mock Organization and Location to enable rolling back transaction on resultFactory failure
@Domain([Record, RecordItem, RecordText, RecordCall, RecordItemReceipt, Organization, Location,
    RecordNote, RecordNoteRevision, Location, MediaInfo, MediaElement, MediaElementVersion])
@TestMixin(HibernateTestMixin)
class RecordSpec extends Specification {

	static doWithSpring = {
        resultFactory(ResultFactory)
    }

	def setup() {
        Helpers.metaClass.'static'.getResultFactory = TestHelpers.getResultFactory(grailsApplication)
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
        !rec.lastRecordActivity.isBefore(currentTimestamp)
        RecordItem.countByRecord(rec) == 4
        RecordText.countByRecord(rec) == 2
        RecordCall.countByRecord(rec) == 2
    }

    void "test retrieving items from the record"() {
    	given: "a record with items of various ages"
    	Record rec = new Record()
    	rec.save(flush:true, failOnError:true)
    	RecordItem nowItem = rec.add(new RecordItem(), null).payload,
    		lWkItem = rec.add(new RecordItem(), null).payload,
    		yestItem = rec.add(new RecordItem(), null).payload,
    		twoDItem = rec.add(new RecordItem(), null).payload,
    		thrDItem = rec.add(new RecordItem(), null).payload
    	rec.save(flush:true, failOnError:true)
    	assert RecordItem.countByRecord(rec) == 5
    	//can't set the whenCreated in the constructor
    	lWkItem.whenCreated = DateTime.now().minusWeeks(1)
    	yestItem.whenCreated = DateTime.now().minusDays(1)
    	twoDItem.whenCreated = DateTime.now().minusDays(2)
    	thrDItem.whenCreated = DateTime.now().minusDays(3)
    	thrDItem.save(flush:true, failOnError:true)

    	when: "we get items between a date range"
    	List<RecordItem> items = rec.getBetween(DateTime.now().minusDays(4),
    		DateTime.now().minusHours(22), [max:2, offset:1])

    	then:
    	items.size() == 2 // notice offset 1
    	items[0] == twoDItem // newer item
    	items[1] == thrDItem // older item

    	when: "we get items since a certain date"
    	items = rec.getSince(DateTime.now().minusWeeks(4), [max:3, offset:1])

    	then:
    	items.size() == 3 // notice offset 1
    	items[0] == yestItem // newer item
    	items[1] == twoDItem
    	items[2] == thrDItem // older item
    }

    void "test getting items from record by type"() {
        given: "record with items of all types"
        Record rec1 = new Record()
        rec1.save(flush:true, failOnError:true)

        RecordText rText1 = rec1.storeOutgoingText("hello").payload
        RecordCall rCall1 = rec1.storeOutgoingCall().payload,
            rCall2 = rec1.storeOutgoingCall().payload
        rCall2.voicemailInSeconds = 22
        rCall2.hasAwayMessage = true
        RecordNote rNote1 = new RecordNote(record:rec1)
        [rText1, rCall1, rCall2, rNote1]*.save(flush:true, failOnError:true)

        DateTime afterDt = DateTime.now().minusWeeks(3)
        DateTime beforeDt = DateTime.now().plusWeeks(3)

        expect:
        rec1.countItems([RecordCall]) == 2
        rec1.countItems([RecordText]) == 1
        rec1.countItems([RecordNote]) == 1
        rec1.countItems([RecordCall, RecordText]) == 3
        rec1.countItems([RecordText, RecordNote]) == 2
        rec1.countItems([RecordCall, RecordNote]) == 3
        rec1.countItems([RecordCall, RecordText, RecordNote]) == 4

        rec1.getItems([RecordCall])*.id.every { it in [rCall1, rCall2]*.id }
        rec1.getItems([RecordText])*.id.every { it in [rText1]*.id }
        rec1.getItems([RecordNote])*.id.every { it in [rNote1]*.id }
        rec1.getItems([RecordCall, RecordText])*.id.every { it in [rText1, rCall1, rCall2]*.id }
        rec1.getItems([RecordText, RecordNote])*.id.every { it in [rText1, rNote1]*.id }
        rec1.getItems([RecordCall, RecordNote])*.id.every { it in [rCall1, rNote1, rCall2]*.id }
        rec1.getItems([RecordCall, RecordText, RecordNote])*.id.every { it in [rText1, rCall1, rCall2, rNote1]*.id }

        rec1.countCallsSince(afterDt, false) == 1 // one call that isn't a voicemail
        rec1.countCallsSince(afterDt, true) == 1 // one call that IS a voicemail
        rec1.countSince(afterDt, [RecordCall]) == 2 // overall, two calls of any voicemail status
        rec1.countSince(afterDt, [RecordText]) == 1
        rec1.countSince(afterDt, [RecordNote]) == 1
        rec1.countSince(afterDt, [RecordCall, RecordText]) == 3
        rec1.countSince(afterDt, [RecordText, RecordNote]) == 2
        rec1.countSince(afterDt, [RecordCall, RecordNote]) == 3
        rec1.countSince(afterDt, [RecordCall, RecordText, RecordNote]) == 4

        rec1.getSince(afterDt, [RecordCall])*.id.every { it in [rCall1, rCall2]*.id }
        rec1.getSince(afterDt, [RecordText])*.id.every { it in [rText1]*.id }
        rec1.getSince(afterDt, [RecordNote])*.id.every { it in [rNote1]*.id }
        rec1.getSince(afterDt, [RecordCall, RecordText])*.id.every { it in [rText1, rCall1, rCall2]*.id }
        rec1.getSince(afterDt, [RecordText, RecordNote])*.id.every { it in [rText1, rNote1]*.id }
        rec1.getSince(afterDt, [RecordCall, RecordNote])*.id.every { it in [rCall1, rCall2, rNote1]*.id }
        rec1.getSince(afterDt, [RecordCall, RecordText, RecordNote])*.id.every { it in [rText1, rCall1, rCall2, rNote1]*.id }

        rec1.countBetween(afterDt, beforeDt, [RecordCall]) == 2
        rec1.countBetween(afterDt, beforeDt, [RecordText]) == 1
        rec1.countBetween(afterDt, beforeDt, [RecordNote]) == 1
        rec1.countBetween(afterDt, beforeDt, [RecordCall, RecordText]) == 3
        rec1.countBetween(afterDt, beforeDt, [RecordText, RecordNote]) == 2
        rec1.countBetween(afterDt, beforeDt, [RecordCall, RecordNote]) == 3
        rec1.countBetween(afterDt, beforeDt, [RecordCall, RecordText, RecordNote]) == 4

        rec1.getBetween(afterDt, beforeDt, [RecordCall])*.id.every { it in [rCall1, rCall2]*.id }
        rec1.getBetween(afterDt, beforeDt, [RecordText])*.id.every { it in [rText1]*.id }
        rec1.getBetween(afterDt, beforeDt, [RecordNote])*.id.every { it in [rNote1]*.id }
        rec1.getBetween(afterDt, beforeDt, [RecordCall, RecordText])*.id.every { it in [rText1, rCall1, rCall2]*.id }
        rec1.getBetween(afterDt, beforeDt, [RecordText, RecordNote])*.id.every { it in [rText1, rNote1]*.id }
        rec1.getBetween(afterDt, beforeDt, [RecordCall, RecordNote])*.id.every { it in [rCall1, rCall2, rNote1]*.id }
        rec1.getBetween(afterDt, beforeDt, [RecordCall, RecordText, RecordNote])*.id.every { it in [rText1, rCall1, rCall2, rNote1]*.id }
    }
}
