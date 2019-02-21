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
@TestFor(CallCallbackService)
class CallCallbackServiceSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test check if voicemail"() {
        given:
        PhoneNumber pNum1 = TestUtils.randPhoneNumber()
        TypeMap params1 = TestUtils.randTypeMap()
        TypeMap params2 = TypeMap.create((TwilioUtils.STATUS_DIALED_CALL): ReceiptStatus.SUCCESS.statuses[0])

        Phone p1 = GroovyMock()
        IncomingSession is1 = GroovyMock()

        MockedMethod recordVoicemailMessage = MockedMethod.create(CallTwiml, "recordVoicemailMessage")

        when:
        Result res = service.checkIfVoicemail(p1, is1, params1)

        then:
        1 * is1.number >> pNum1
        recordVoicemailMessage.latestArgs == [p1, pNum1]

        when:
        res = service.checkIfVoicemail(p1, is1, params2)

        then:
        res.status == ResultStatus.OK
        TestUtils.buildXml(res.payload) == TestUtils.buildXml { Response { } }

        cleanup:
        recordVoicemailMessage?.restore()
    }

    void "test direct message call"() {
        given:
        String str1 = TestUtils.randString()

        service.tokenService = GroovyMock(TokenService)

        when:
        Result res = service.directMessageCall(str1)

        then:
        1 * service.tokenService.buildDirectMessageCall(str1) >> Result.createError([], ResultStatus.BAD_REQUEST)
        res.status == ResultStatus.OK
        TestUtils.buildXml(res.payload).contains("callTwiml.error")

        when:
        res = service.directMessageCall(str1)

        then:
        1 * service.tokenService.buildDirectMessageCall(str1) >> Result.void()
        res.status == ResultStatus.NO_CONTENT
    }

    void "test screen incoming calls"() {
        given:
        PhoneNumber pNum1 = TestUtils.randPhoneNumber()

        Phone p1 = TestUtils.buildActiveStaffPhone()
        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord(p1)
        ipr1.mergeNumber(pNum1, 8)
        PhoneRecord spr1 = TestUtils.buildSharedPhoneRecord(null, p1)
        spr1.shareSource.mergeNumber(pNum1, 8)

        PhoneRecord.withSession { it.flush() }
        int prBaseline = PhoneRecord.count()

        IncomingSession is1 = GroovyMock()

        when:
        Result res = service.screenIncomingCall(p1, is1)

        then: "no phone records are created if not found"
        1 * is1.asBoolean() >> false
        res.status == ResultStatus.BAD_REQUEST
        PhoneRecord.count() == prBaseline

        when:
        res = service.screenIncomingCall(p1, is1)

        then:
        1 * is1.asBoolean() >> true
        1 * is1.number >> pNum1
        TestUtils.buildXml(res.payload).contains("callTwiml.screenIncoming")
        PhoneRecord.count() == prBaseline
    }
}
