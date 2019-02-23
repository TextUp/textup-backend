package org.textup.rest

import grails.test.mixin.*
import grails.test.mixin.gorm.*
import grails.test.mixin.hibernate.*
import org.textup.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*
import spock.lang.*

@Domain([AnnouncementReceipt, ContactNumber, CustomAccountDetails, FeaturedAnnouncement,
    FutureMessage, GroupPhoneRecord, IncomingSession, IndividualPhoneRecord, Location, MediaElement,
    MediaElementVersion, MediaInfo, Organization, OwnerPolicy, Phone, PhoneNumberHistory,
    PhoneOwnership, PhoneRecord, PhoneRecordMembers, Record, RecordCall, RecordItem,
    RecordItemReceipt, RecordNote, RecordNoteRevision, RecordText, Role, Schedule,
    SimpleFutureMessage, Staff, StaffRole, Team, Token])
@TestMixin(HibernateTestMixin)
@TestFor(IncomingCallService)
class IncomingCallServiceSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test building call response"() {
        given:
        OwnerPolicy op1 = TestUtils.buildOwnerPolicy()
        Phone p1 = TestUtils.buildActiveStaffPhone()
        IncomingSession is1 = TestUtils.buildSession()
        RecordCall rCall1 = TestUtils.buildRecordCall()

        NotificationGroup notifGroup1 = GroovyMock()
        MockedMethod connectIncoming = MockedMethod.create(CallTwiml, "connectIncoming")
        MockedMethod recordVoicemailMessage = MockedMethod.create(CallTwiml, "recordVoicemailMessage")

        when:
        Result res = service.buildCallResponse(p1, is1, [rCall1], notifGroup1)

        then:
        1 * notifGroup1.buildCanNotifyReadOnlyPolicies(NotificationFrequency.IMMEDIATELY) >> []
        connectIncoming.notCalled
        recordVoicemailMessage.latestArgs == [p1, is1.number]
        rCall1.hasAwayMessage
        res == null

        when:
        res = service.buildCallResponse(p1, is1, [rCall1], notifGroup1)

        then:
        1 * notifGroup1.buildCanNotifyReadOnlyPolicies(NotificationFrequency.IMMEDIATELY) >> [op1]
        connectIncoming.latestArgs == [p1.number, is1.number, [op1.staff.personalNumber]]

        cleanup:
        connectIncoming?.restore()
        recordVoicemailMessage?.restore()
    }

    void "test finishing relaying call"() {
        given:
        RecordCall rCall1 = TestUtils.buildRecordCall()

        Phone p1 = GroovyMock()
        IncomingSession is1 = GroovyMock()
        MockedMethod buildCallResponse = MockedMethod.create(service, "buildCallResponse")

        when:
        Result res = service.finishRelayCall(p1, is1, null)

        then:
        res.status == ResultStatus.OK
        TestUtils.buildXml(res.payload).contains("Reject")

        when:
        res = service.finishRelayCall(p1, is1, [rCall1])

        then:
        res == null
        buildCallResponse.latestArgs[0] == p1
        buildCallResponse.latestArgs[1] == is1
        buildCallResponse.latestArgs[2] == [rCall1]
        buildCallResponse.latestArgs[3] instanceof NotificationGroup

        cleanup:
        buildCallResponse?.restore()
    }

    void "test start relaying call"() {
        given:
        String apiId = TestUtils.randString()

        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord()
        PhoneRecord spr1 = TestUtils.buildSharedPhoneRecord()
        spr1.permission = SharePermission.NONE
        IncomingSession is1 = TestUtils.buildSession()

        int callBaseline = RecordCall.count()

        Phone p1 = GroovyMock()
        service.socketService = GroovyMock(SocketService)
        MockedMethod tryMarkUnread = MockedMethod.create(PhoneRecordUtils, "tryMarkUnread") {
            Result.createSuccess([ipr1, spr1]*.toWrapper())
        }
        MockedMethod finishRelayCall = MockedMethod.create(service, "finishRelayCall") { arg1, arg2, rCalls ->
            Result.createSuccess(rCalls)
        }
        ByteArrayOutputStream stdErr = TestUtils.captureAllStreamsReturnStdErr()

        when:
        Result res = service.relayCall(p1, is1, apiId)

        then: "ignores failures"
        tryMarkUnread.latestArgs == [p1, is1.number]
        1 * service.socketService.sendIndividualWrappers([ipr1, spr1]*.toWrapper())
        finishRelayCall.latestArgs[0] == p1
        finishRelayCall.latestArgs[1] == is1
        res.status == ResultStatus.OK
        res.payload.size() == 1
        RecordCall.count() == callBaseline + 1
        RecordCall.findByRecord(ipr1.record) == res.payload[0]
        res.payload[0].outgoing == false
        res.payload[0].receipts.find {  it.apiId == apiId }
        res.payload[0].receipts.find { it.contactNumber == is1.number }
        RecordCall.findByRecord(spr1.record) == null
        stdErr.toString().contains("relayCall")
        stdErr.toString().contains("phoneRecordWrapper.insufficientPermission")

        cleanup:
        tryMarkUnread?.restore()
        finishRelayCall?.restore()
        TestUtils.restoreAllStreams()
    }

    void "test handling self call errors"() {
        given:
        String digits1 = "invalid phone number"
        String digits2 = TestUtils.randPhoneNumberString()

        Phone p1 = GroovyMock()
        Staff s1 = GroovyMock()

        when:
        Result res = service.handleSelfCall(null, null, null, null)

        then:
        res.status == ResultStatus.OK
        TestUtils.buildXml(res.payload).contains("callTwiml.selfGreeting")

        when:
        res = service.handleSelfCall(p1, null, digits1, s1)

        then:
        res.status == ResultStatus.OK
        TestUtils.buildXml(res.payload).contains("callTwiml.selfInvalidDigits")

        when:
        res = service.handleSelfCall(p1, null, digits2, s1)

        then:
        res.status == ResultStatus.OK
        TestUtils.buildXml(res.payload).contains("callTwiml.error")
    }

    void "test handling self call"() {
        given:
        String apiId = TestUtils.randString()
        String digits = TestUtils.randPhoneNumberString()

        Staff s1 = TestUtils.buildStaff()
        Phone p1 = TestUtils.buildActiveStaffPhone()
        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord()
        PhoneRecord spr1 = TestUtils.buildSharedPhoneRecord()
        spr1.permission = SharePermission.NONE

        PhoneRecord.withSession { it.flush() }

        int callBaseline = RecordCall.count()

        service.socketService = GroovyMock(SocketService)
        MockedMethod tryMarkUnread = MockedMethod.create(PhoneRecordUtils, "tryMarkUnread") {
            Result.createSuccess([ipr1, spr1]*.toWrapper())
        }
        MockedMethod selfConnecting = MockedMethod.create(CallTwiml, "selfConnecting") {
            Result.void()
        }
        ByteArrayOutputStream stdErr = TestUtils.captureAllStreamsReturnStdErr()

        when:
        Result res = service.handleSelfCall(p1, apiId, digits, s1)

        then:
        tryMarkUnread.latestArgs == [p1, PhoneNumber.create(digits)]
        1 * service.socketService.sendIndividualWrappers([ipr1, spr1]*.toWrapper())
        selfConnecting.latestArgs == [p1.number.e164PhoneNumber, PhoneNumber.create(digits).number]
        res.status == ResultStatus.NO_CONTENT
        RecordCall.findByRecord(ipr1.record).outgoing
        RecordCall.findByRecord(ipr1.record).author == Author.create(s1)
        RecordCall.findByRecord(ipr1.record).receipts[0].apiId == apiId
        RecordCall.findByRecord(ipr1.record).receipts[0].contactNumber == PhoneNumber.create(digits)
        RecordCall.findByRecord(spr1.record) == null
        stdErr.toString().contains("phoneRecordWrapper.insufficientPermission")

        cleanup:
        tryMarkUnread?.restore()
        selfConnecting?.restore()
        TestUtils.restoreAllStreams()
    }

    void "test processing incoming call overall"() {
        given:
        String apiId = TestUtils.randString()
        String digits = TestUtils.randString()

        Staff s1 = TestUtils.buildStaff()
        Phone p1 = TestUtils.buildStaffPhone(s1)
        Phone p2 = TestUtils.buildActiveStaffPhone()
        Phone p3 = TestUtils.buildActiveStaffPhone()
        FeaturedAnnouncement fa1 = TestUtils.buildAnnouncement(p2)

        IncomingSession is1 = GroovyMock()
        service.announcementCallbackService = GroovyMock(AnnouncementCallbackService)
        MockedMethod handleSelfCall = MockedMethod.create(service, "handleSelfCall") {
            Result.void()
        }
        MockedMethod relayCall = MockedMethod.create(service, "relayCall") {
            Result.void()
        }

        when:
        Result res = service.process(p1, is1, apiId, digits)

        then:
        1 * is1.numberAsString >> s1.personalNumberAsString
        handleSelfCall.latestArgs == [p1, apiId, digits, s1]
        relayCall.notCalled
        res.status == ResultStatus.NO_CONTENT

        when:
        res = service.process(p2, is1, apiId, digits)

        then:
        1 * service.announcementCallbackService.handleAnnouncementCall(p2, is1, digits, _ as Closure) >> { args ->
            args[3].call()
        }
        handleSelfCall.callCount == 1
        relayCall.latestArgs == [p2, is1, apiId]
        res.status == ResultStatus.NO_CONTENT

        when:
        res = service.process(p3, is1, apiId, digits)

        then:
        handleSelfCall.callCount == 1
        relayCall.latestArgs == [p3, is1, apiId]
        res.status == ResultStatus.NO_CONTENT

        cleanup:
        handleSelfCall?.restore()
        relayCall?.restore()
    }
}
