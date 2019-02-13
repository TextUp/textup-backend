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
class RecordSpec extends Specification {

	static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test static creation"() {
        when:
        Result res = Record.tryCreate()

        then:
        res.status == ResultStatus.CREATED
        res.payload.lastRecordActivity != null
        res.payload.language == VoiceLanguage.ENGLISH
    }

    void "test try adding notes"() {
        given:
        Record rec1 = TestUtils.buildRecord()

        when:
        Result res = rec1.tryCreateItem(RecordItemType.NOTE, TestUtils.randString())

        then:
        res.status == ResultStatus.INTERNAL_SERVER_ERROR
        res.errorMessages == ["record.cannotAddNoteHere"]
    }

    void "test adding outgoing items for type #type"() {
        given:
        Record rec1 = TestUtils.buildRecord()
        Author a1 = TestUtils.buildAuthor()
        String msg = TestUtils.randString()

        int iBaseline = RecordItem.count()
        int rBaseline = RecordItemReceipt.count()
        int mBaseline = MediaInfo.count()
        DateTime origRecordActivity = rec1.lastRecordActivity

        when:
        Result res = rec1.storeOutgoing(type, a1, msg, new MediaInfo())
        RecordItem.withSession { it.flush() }

        then:
        res.status == ResultStatus.CREATED
        type.toClass().isAssignableFrom(res.payload.class)
        res.payload.outgoing == true
        res.payload.record == rec1
        res.payload[msgPropName] == msg
        rec1.lastRecordActivity.isAfter(origRecordActivity)

        res.payload.receipts == null

        RecordItem.count() == iBaseline + 1
        RecordItemReceipt.count() == rBaseline
        MediaInfo.count() == mBaseline + 1

        where:
        type                | msgPropName
        RecordItemType.TEXT | "contents"
        RecordItemType.CALL | "noteContents"
    }

    void "test adding incoming items for type #type"() {
        given:
        Record rec1 = TestUtils.buildRecord()
        Author a1 = TestUtils.buildAuthor()
        PhoneNumber pNum = TestUtils.randPhoneNumber()
        String apiId = TestUtils.randString()
        String msg = TestUtils.randString()
        Integer numBillable = TestUtils.randIntegerUpTo(88, true)

        int iBaseline = RecordItem.count()
        int rBaseline = RecordItemReceipt.count()
        int mBaseline = MediaInfo.count()
        DateTime origRecordActivity = rec1.lastRecordActivity

        when:
        Result res = rec1.storeIncoming(type, a1, pNum, apiId, msg, numBillable)
        RecordItem.withSession { it.flush() }

        then:
        res.status == ResultStatus.CREATED
        type.toClass().isAssignableFrom(res.payload.class)
        res.payload.outgoing == false
        res.payload.record == rec1
        res.payload[msgPropName] == msg
        rec1.lastRecordActivity.isAfter(origRecordActivity)

        res.payload.receipts.size() == 1
        res.payload.receipts[0].apiId == apiId
        res.payload.receipts[0].numBillable == numBillable
        res.payload.receipts[0].contactNumberAsString == pNum.number
        res.payload.receipts[0].status == ReceiptStatus.SUCCESS

        RecordItem.count() == iBaseline + 1
        RecordItemReceipt.count() == rBaseline + 1
        MediaInfo.count() == mBaseline

        where:
        type                | msgPropName
        RecordItemType.TEXT | "contents"
        RecordItemType.CALL | "noteContents"
    }
}
