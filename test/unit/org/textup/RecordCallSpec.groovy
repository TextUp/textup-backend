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
class RecordCallSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test constraints + static creator"() {
        given:
        Record rec1 = TestUtils.buildRecord()

        when: "empty"
        Result res = RecordCall.tryCreate(null)

        then: "invalid"
        res.status == ResultStatus.UNPROCESSABLE_ENTITY

        when: "with record"
        res = RecordCall.tryCreate(TestUtils.buildRecord())

        then: "valid"
        res.status == ResultStatus.CREATED
    }

    void "test try updating for voicemail"() {
        given:
        Integer duration = TestUtils.randIntegerUpTo(88, true)
        MediaElement el1 = TestUtils.buildMediaElement()

        when:
        RecordCall rCall1 = RecordCall.tryCreate(TestUtils.buildRecord()).payload

        then:
        rCall1.media == null
        rCall1.hasAwayMessage == false
        rCall1.voicemailInSeconds == 0

        when:
        DateTime dt = rCall1.record.lastRecordActivity
        Result res = RecordCall.tryUpdateVoicemail(rCall1, duration, [el1])

        then:
        res.status == ResultStatus.OK
        res.payload.media instanceof MediaInfo
        el1 in res.payload.media.mediaElements
        res.payload.hasAwayMessage == true
        res.payload.voicemailInSeconds == duration
    }

    void "test getting duration in seconds and excluding receipt with longest duration"() {
        given: "a valid record call"
        RecordCall call = TestUtils.buildRecordCall(null, false)

        when: "no receipts"
        assert call.receipts == null

        then:
        call.isStillOngoing() == true
        call.buildParentCallApiId() == null
        call.durationInSeconds == 0
        call.showOnlyContactReceipts() == []

        when: "one receipt without numBillable"
        RecordItemReceipt rpt1 = TestUtils.buildReceipt()
        rpt1.numBillable = null
        call.addToReceipts(rpt1)

        then: "show only contact receipts excludes null"
        call.isStillOngoing() == true
        call.buildParentCallApiId() == rpt1.apiId
        call.durationInSeconds == 0
        call.showOnlyContactReceipts() == []

        when:
        rpt1.numBillable = 0

        then: "show only contact receipts excludes null"
        call.isStillOngoing() == false
        call.buildParentCallApiId() == rpt1.apiId
        call.durationInSeconds == 0
        call.showOnlyContactReceipts() == []

        when: "multiple receipts with varying numBillable"
        RecordItemReceipt rpt2 = TestUtils.buildReceipt()
        rpt2.numBillable = 12
        RecordItemReceipt rpt3 = TestUtils.buildReceipt()
        rpt3.numBillable = 88
        [rpt2, rpt3].each(call.&addToReceipts)

        then:
        call.isStillOngoing() == false
        call.buildParentCallApiId() == rpt3.apiId
        call.durationInSeconds == 88
        call.showOnlyContactReceipts().size() == 1
        call.showOnlyContactReceipts().every { it in [rpt2] }
    }

    void "test grouping receipts by status for incoming versus outgoing"() {
        given: "a valid record call with multiple receipts with varying numBillable"
        RecordCall call = TestUtils.buildRecordCall(null, false)
        RecordItemReceipt rpt1 = TestUtils.buildReceipt(ReceiptStatus.PENDING),
            rpt2 = TestUtils.buildReceipt(ReceiptStatus.PENDING),
            rpt3 = TestUtils.buildReceipt(ReceiptStatus.PENDING)
        rpt1.numBillable = null
        rpt2.numBillable = 12
        rpt3.numBillable = 88
        [rpt1, rpt2, rpt3].each(call.&addToReceipts)

        when: "call is incoming"
        call.outgoing = false

        then: "show all receipts"
        call.groupReceiptsByStatus().pending.size() == 3

        when: "call is outgoing"
        call.outgoing = true

        then: "exclude receipt with the longest or null durations b/c outgoing calls are bridged"
        call.groupReceiptsByStatus().pending.size() == 1 // exclude rp1 and rpt3

        when: "call is outgoing and was also scheduled"
        call.wasScheduled = true

        then: "show all receipts because scheduled outgoing calls are direct messages"
        call.groupReceiptsByStatus().pending.size() == 3
    }
}
