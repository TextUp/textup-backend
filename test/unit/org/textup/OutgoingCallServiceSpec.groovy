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
@TestFor(OutgoingCallService)
class OutgoingCallServiceSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test after starting bridge call"() {
        given:
        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord()
        Author author1 = TestUtils.buildAuthor()
        TempRecordReceipt tempRpt1 = TestUtils.buildTempReceipt()
        int callBaseline = RecordCall.count()

        when:
        Result res = service.afterBridgeCall(ipr1.toWrapper(), author1, tempRpt1)

        then:
        res.status == ResultStatus.CREATED
        res.payload.outgoing
        res.payload.record == ipr1.record
        res.payload.author == author1
        res.payload.receipts.size() == 1
        res.payload.receipts[0].apiId == tempRpt1.apiId
        RecordCall.count() == callBaseline + 1
    }

    void "test starting bridge call"() {
        given:
        PhoneNumber pNum1 = TestUtils.randPhoneNumber()
        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord()
        Author author1 = TestUtils.buildAuthor()
        TempRecordReceipt tempRpt1 = TestUtils.buildTempReceipt()

        RecordCall rCall1 = GroovyMock()
        service.callService = GroovyMock(CallService)
        MockedMethod afterBridgeCall = MockedMethod.create(service, "afterBridgeCall") {
            Result.createSuccess(rCall1)
        }

        when:
        Result res = service.tryStart(null, null, null)

        then:
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages == ["outgoingCallService.noPersonalNumber"]

        when:
        res = service.tryStart(pNum1, ipr1.toWrapper(), author1)

        then:
        1 * service.callService.start(ipr1.phone.number, [pNum1], _ as Map, null) >>
            Result.createSuccess(tempRpt1)
        afterBridgeCall.latestArgs == [ipr1.toWrapper(), author1, tempRpt1]
        res.status == ResultStatus.OK
        res.payload == rCall1

        cleanup:
        afterBridgeCall?.restore()
    }
}
