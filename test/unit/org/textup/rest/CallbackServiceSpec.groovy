package org.textup.rest

import org.textup.test.*
import com.twilio.security.RequestValidator
import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import grails.validation.ValidationErrors
import java.util.concurrent.TimeUnit
import javax.servlet.http.HttpServletRequest
import org.apache.commons.lang3.tuple.Pair
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.joda.time.DateTime
import org.springframework.context.MessageSource
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
@TestFor(CallbackService)
@TestMixin(HibernateTestMixin)
class CallbackServiceSpec extends CustomSpec {

	static doWithSpring = {
		resultFactory(ResultFactory)
	}

    def setup() {
    	setupData()
    	service.resultFactory = TestUtils.getResultFactory(grailsApplication)
    }

    def cleanup() {
    	cleanupData()
    }

    // Gathering and coordinating
    // --------------------------

    void "test process invalid inputs"() {
    	when: "'to' maps to nonexistent phone"
        String nonexistentNumber = "8888888888"
        assert Phone.countByNumberAsString(nonexistentNumber) == 0
    	TypeMap params = TypeMap.create(From: nonexistentNumber, To:nonexistentNumber)
    	Result<Closure> res = service.process(params)

    	then:
    	res.success == true
    	TestUtils.buildXml(res.payload).contains("twimlBuilder.notFound")

    	when: "neither messageSid nor callSid specified"
    	params = TypeMap.create(To:p1.numberAsString, From:"1112223333")
		res = service.process(params)

    	then:
    	res.success == false
    	res.status == ResultStatus.BAD_REQUEST
    	res.errorMessages[0] == "callbackService.process.invalid"
    }

    void "test process for non-US numbers"() {
        when: "incoming is non-US number for text"
        TypeMap params = TypeMap.create([To:"blah", From:"invalid", MessageSid:"ok"])
        Result<Closure> res = service.process(params)

        then:
        res.success == true
        TestUtils.buildXml(res.payload) == TestUtils.buildXml {
            Response {
                Message("twimlBuilder.invalidNumber")
            }
        }

        when: "incoming is non-US number for call"
        params = TypeMap.create([To: "blah", From:"invalid", CallSid:"ok"])
        res = service.process(params)

        then:
        res.success == true
        TestUtils.buildXml(res.payload) == TestUtils.buildXml {
            Response {
                Say("twimlBuilder.invalidNumber")
                Hangup()
            }
        }
    }

    void "test process for anonymous call responses"() {
        given:
        service.outgoingMessageService = Mock(OutgoingMessageService)

        when: "retrieving a outgoing direct message delivered through call"
        TypeMap params = TypeMap.create([handle:CallResponse.DIRECT_MESSAGE.toString()])
        Result<Closure> res = service.process(params)

        then:
        1 * service.outgoingMessageService.directMessageCall(*_) >> new Result()
        res.status == ResultStatus.OK

        when: "no-op"
        params = TypeMap.create([handle:CallResponse.DO_NOTHING.toString()])
        res = service.process(params)

        then:
        0 * service.outgoingMessageService._
        res.status == ResultStatus.OK
        TestUtils.buildXml(res.payload) == TestUtils.buildXml { Response { } }

        when: "hanging up"
        params = TypeMap.create([handle:CallResponse.END_CALL.toString()])
        res = service.process(params)

        then:
        0 * service.outgoingMessageService._
        res.status == ResultStatus.OK
        TestUtils.buildXml(res.payload) == TestUtils.buildXml { Response { Hangup() } }

        when: "voicemail greeting response"
        params = TypeMap.create([handle:CallResponse.VOICEMAIL_GREETING_PROCESSING.toString()])
        res = service.process(params)

        then:
        0 * service.outgoingMessageService._
        res.status == ResultStatus.OK
        TestUtils.buildXml(res.payload).contains("twimlBuilder.call.processingVoicemailGreeting")
        TestUtils.buildXml(res.payload).contains(Constants.CALL_HOLD_MUSIC_URL)
    }

    void "test handling sessions"() {
        given:
        PhoneNumber originalFromNumber = new PhoneNumber(number: TestUtils.randPhoneNumberString())
        PhoneNumber fromNumber = new PhoneNumber(number: TestUtils.randPhoneNumberString())
        PhoneNumber toNumber = new PhoneNumber(number: TestUtils.randPhoneNumberString())
        [originalFromNumber, fromNumber, toNumber].each { assert it.validate() }
        TypeMap params = TypeMap.create([originalFrom: originalFromNumber.number])
        int iBaseline = IncomingSession.count()

        when: "CallResponse.FINISH_BRIDGE + new phone number"
        params.handle = CallResponse.FINISH_BRIDGE.toString()
        Result<IncomingSession> res = service.getOrCreateIncomingSession(p1, fromNumber,
            toNumber, params)

        then:
        IncomingSession.count() == iBaseline + 1
        res.status == ResultStatus.OK
        res.payload.numberAsString == toNumber.number

        when: "CallResponse.ANNOUNCEMENT_AND_DIGITS + existing phone number"
        params.handle = CallResponse.ANNOUNCEMENT_AND_DIGITS.toString()
        res = service.getOrCreateIncomingSession(p1, fromNumber, toNumber, params)

        then:
        IncomingSession.count() == iBaseline + 1
        res.status == ResultStatus.OK
        res.payload.numberAsString == toNumber.number

        when: "CallResponse.VOICEMAIL_GREETING_RECORD + existing phone number"
        params.handle = CallResponse.VOICEMAIL_GREETING_RECORD.toString()
        res = service.getOrCreateIncomingSession(p1, fromNumber, toNumber, params)

        then:
        IncomingSession.count() == iBaseline + 1
        res.status == ResultStatus.OK
        res.payload.numberAsString == toNumber.number

        when: "CallResponse.VOICEMAIL_GREETING_PROCESSED + existing phone number"
        params.handle = CallResponse.VOICEMAIL_GREETING_PROCESSED.toString()
        res = service.getOrCreateIncomingSession(p1, fromNumber, toNumber, params)

        then:
        IncomingSession.count() == iBaseline + 1
        res.status == ResultStatus.OK
        res.payload.numberAsString == toNumber.number

        when: "CallResponse.VOICEMAIL_GREETING_PLAY + existing phone number"
        params.handle = CallResponse.VOICEMAIL_GREETING_PLAY.toString()
        res = service.getOrCreateIncomingSession(p1, fromNumber, toNumber, params)

        then:
        IncomingSession.count() == iBaseline + 1
        res.status == ResultStatus.OK
        res.payload.numberAsString == toNumber.number

        when: "CallResponse.SCREEN_INCOMING + new phone number"
        params.handle = CallResponse.SCREEN_INCOMING.toString()
        res = service.getOrCreateIncomingSession(p1, fromNumber, toNumber, params)

        then:
        IncomingSession.count() == iBaseline + 2
        res.status == ResultStatus.OK
        res.payload.numberAsString == originalFromNumber.number

        when: "another valid call response + new phone number"
        params.handle = "another valid call response"
        res = service.getOrCreateIncomingSession(p1, fromNumber, toNumber, params)

        then:
        IncomingSession.count() == iBaseline + 3
        res.status == ResultStatus.OK
        res.payload.numberAsString == fromNumber.number
    }

    void "test getting number for phone"() {
        given:
        PhoneNumber fromNum = new PhoneNumber(number: TestUtils.randPhoneNumberString())
        PhoneNumber toNum = new PhoneNumber(number: TestUtils.randPhoneNumberString())

        expect:
        service.getNumberForPhone(fromNum, toNum, TypeMap.create(handle: handle)).number ==
        phoneIsFromNumber ? fromNum.number : toNum.number

        where:
        handle                                               | phoneIsFromNumber
        "anything"                                           | false
        CallResponse.FINISH_BRIDGE.toString()                | true
        CallResponse.ANNOUNCEMENT_AND_DIGITS.toString()      | true
        CallResponse.SCREEN_INCOMING.toString()              | true
        CallResponse.VOICEMAIL_GREETING_RECORD.toString()    | true
        CallResponse.VOICEMAIL_GREETING_PROCESSED.toString() | true
        CallResponse.VOICEMAIL_GREETING_PLAY.toString()      | true
    }


    void "processing valid inputs overall"() {
        given:
        service.incomingMessageService = Mock(IncomingMessageService)

        when: "call sid specified"
        TypeMap params = TypeMap.create(To:p1.numberAsString, From:"1112223333",
            CallSid: "hi")
        Result<Closure> res = service.process(params)

        then:
        1 * service.incomingMessageService.receiveCall(*_)

        when: "message id specified"
        params.MessageSid = "hi"
        params.CallSid = null
        params.Body = "hi"
        params.NumSegments = 8
        res = service.process(params)

        then:
        1 * service.incomingMessageService.processText(*_)
    }

    // Text
    // ----

    void "test handling invalid text"() {
        when: "missing required inputs"
        Result<Closure> res = service.processText(null, null, null, TypeMap.create())

        then:
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
    }

    void "test announcement-related texts"() {
        given:
        service.announcementService = Mock(AnnouncementService)
        service.incomingMessageService = Mock(IncomingMessageService)
        Phone thisPhone = Mock(Phone)
        TypeMap params = TypeMap.create(Body: Constants.TEXT_SEE_ANNOUNCEMENTS,
            NumSegments: 8)

        when: "seeing announcements but has no announcements"
        Result<Closure> res = service.processText(thisPhone, null, "id", params)

        then:
        1 * thisPhone.announcements >> []
        0 * service.announcementService._
        1 * service.incomingMessageService.processText(*_) >> new Result()
        res.status == ResultStatus.OK

        when: "seeing announcements and has announcements"
        res = service.processText(thisPhone, null, "id", params)

        then:
        1 * thisPhone.announcements >> [1, 2]
        1 * service.announcementService.textSeeAnnouncements(*_) >> new Result()
        0 * service.incomingMessageService._
        res.status == ResultStatus.OK

        when: "toggling subscription status"
        params.Body = Constants.TEXT_TOGGLE_SUBSCRIBE
        res = service.processText(thisPhone, null, "id", params)

        then:
        0 * thisPhone._
        1 * service.announcementService.textToggleSubscribe(*_) >> new Result()
        0 * service.incomingMessageService._
        res.status == ResultStatus.OK
    }

    void "test processing texts in general"() {
        given:
        service.incomingMessageService = Mock(IncomingMessageService)
        TypeMap params = TypeMap.create(Body: "hi", NumSegments: 8)

        when:
        Result<Closure> res = service.processText(null, null, "id", params)

        then:
        1 * service.incomingMessageService.processText(*_) >> new Result()
        res.status == ResultStatus.OK
    }

    // Calls
    // -----

    void "test process for incoming calls and voicemail"() {
        given:
        service.threadService = Mock(ThreadService)
        service.voicemailService = Mock(VoicemailService)
        service.announcementService = Mock(AnnouncementService)
        service.outgoingMessageService = Mock(OutgoingMessageService)
        service.incomingMessageService = Mock(IncomingMessageService)
        IncomingSession is1 = [number: TestUtils.randPhoneNumberString()] as IncomingSession

        when: "starting voicemail"
        String clientNum = URLEncoder.encode("+1233834920", "UTF-8")
        TypeMap params = TypeMap.create(CallSid: "iamasid!!",
            handle: CallResponse.SCREEN_INCOMING.toString())
        // voicemail is inbound so from client to TextUp phone
        // but we use a relayed call to allow for screening so we store the
        // originalFrom and use the From of the second bridged call to keep track
        //  of the "from" client and the "to" TextUp phone number
        params.originalFrom = clientNum
        params.From = p1.numberAsString
        params.To = "1112223333"
        Result<Closure> res = service.processCall(p1, is1, null, params)

        then:
        1 * service.incomingMessageService.screenIncomingCall(*_) >> new Result()
        res.status == ResultStatus.OK

        when: "in the status callback, check to see if the call was answered and if voicemail should start"
        params.From = clientNum
        params.To = p1.numberAsString
        params.DialCallStatus = "in-progress"
        params.handle = CallResponse.CHECK_IF_VOICEMAIL.toString()
        res = service.processCall(p1, is1, null, params)

        then:
        res.status == ResultStatus.OK
        TestUtils.buildXml(res.payload).contains("twimlBuilder.call.voicemailDirections")

        when:
        params.DialCallStatus = "delivered"
        res = service.processCall(p1, is1, null, params)

        then:
        res.status == ResultStatus.OK
        TestUtils.buildXml(res.payload) == TestUtils.buildXml { Response { } }

        when: "completing voicemail"
        params.handle = CallResponse.VOICEMAIL_DONE.toString()
        params.RecordingSid = "recording id"
        params.RecordingDuration = 88
        params.RecordingUrl = "https://www.example.com"
        res = service.processCall(p1, is1, null, params)

        then:
        1 * service.threadService.delay(*_) >> { args -> args[2].call(); null; }
        1 * service.voicemailService.processVoicemailMessage(*_) >> new ResultGroup()
        res.status == ResultStatus.OK
        TestUtils.buildXml(res.payload) == TestUtils.buildXml { Response { } }

        when: "unspecified or invalid"
        params.handle = "blahblahinvalid"
        res = service.processCall(p1, is1, null, params)

        then: "receive call"
        1 * service.incomingMessageService.receiveCall(*_)
    }

    void "test process for outbound calls"() {
        given:
        service.announcementService = Mock(AnnouncementService)
        service.outgoingMessageService = Mock(OutgoingMessageService)

    	when: "voicemail"
    	String clientNum = "1233834920"
    	TypeMap params = TypeMap.create([CallSid:"iamasid!!",
            handle:CallResponse.FINISH_BRIDGE.toString()])
        // outbound so from TextUp phone to client
        params.From = p1.numberAsString
        params.To = clientNum
    	Result<Closure> res = service.processCall(null, null, null, params)

    	then:
        1 * service.outgoingMessageService.finishBridgeCall(*_)

		when: "announcement and digits"
		params.handle = CallResponse.ANNOUNCEMENT_AND_DIGITS.toString()
		res = service.processCall(null, null, null, params)

		then:
        1 * service.announcementService.completeCallAnnouncement(*_)
    }

    void "test handling voicemail greeting"() {
        given:
        service.threadService = Mock(ThreadService)
        service.voicemailService = Mock(VoicemailService)
        Phone thisPhone = Mock()
        IncomingSession is1 = Mock()

        when: "recording voicemail greeting"
        TypeMap params = TypeMap.create(handle: CallResponse.VOICEMAIL_GREETING_RECORD)
        Result<Closure> res = service.processCall(thisPhone, is1, null, params)

        then:
        1 * thisPhone.number >> new PhoneNumber(number: TestUtils.randPhoneNumberString())
        1 * is1.number >> new PhoneNumber(number: TestUtils.randPhoneNumberString())
        res.status == ResultStatus.OK
        TestUtils.buildXml(res.payload).contains("twimlBuilder.call.recordVoicemailGreeting")

        when: "finished processing voicemail greeting"
        params.handle = CallResponse.VOICEMAIL_GREETING_PROCESSED
        res = service.processCall(p1, null, null, params)

        then:
        1 * service.threadService.delay(*_) >> { args -> args[2].call(); null; }
        1 * service.voicemailService.finishedProcessingVoicemailGreeting(*_) >> new Result()
        res.status == ResultStatus.OK
        TestUtils.buildXml(res.payload) == TestUtils.buildXml { Response { } }

        when: "playing voicemail greeting"
        params.handle = CallResponse.VOICEMAIL_GREETING_PLAY
        res = service.processCall(thisPhone, null, null, params)

        then:
        1 * thisPhone.number >> new PhoneNumber(number: TestUtils.randPhoneNumberString())
        1 * thisPhone.voicemailGreetingUrl >> new URL("https://www.example.com")
        res.status == ResultStatus.OK
        TestUtils.buildXml(res.payload).contains("twimlBuilder.call.finishedVoicemailGreeting")
    }
}
