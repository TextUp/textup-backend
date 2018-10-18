package org.textup

import com.twilio.security.RequestValidator
import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import grails.validation.ValidationErrors
import java.util.concurrent.TimeUnit
import javax.servlet.http.HttpServletRequest
import org.apache.commons.lang3.tuple.Pair
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.codehaus.groovy.grails.web.util.TypeConvertingMap
import org.joda.time.DateTime
import org.springframework.context.MessageSource
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*
import spock.lang.Ignore
import spock.lang.Shared

@TestFor(CallbackService)
@Domain([Contact, Phone, ContactTag, ContactNumber, Record, RecordItem, RecordText,
	RecordCall, RecordItemReceipt, SharedContact, Staff, Team, Organization, Schedule,
	Location, WeeklySchedule, PhoneOwnership, FeaturedAnnouncement, IncomingSession,
	AnnouncementReceipt, Role, StaffRole, NotificationPolicy,
    MediaInfo, MediaElement, MediaElementVersion])
@TestMixin(HibernateTestMixin)
class CallbackServiceSpec extends CustomSpec {

	static doWithSpring = {
		resultFactory(ResultFactory)
	}

    def setup() {
    	setupData()
    	service.resultFactory = TestHelpers.getResultFactory(grailsApplication)
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
    	TypeConvertingMap params = new TypeConvertingMap(From: nonexistentNumber, To:nonexistentNumber)
    	Result<Closure> res = service.process(params)

    	then:
    	res.success == true
    	TestHelpers.buildXml(res.payload).contains("twimlBuilder.notFound")

    	when: "neither messageSid nor callSid specified"
    	params = new TypeConvertingMap(To:p1.numberAsString, From:"1112223333")
		res = service.process(params)

    	then:
    	res.success == false
    	res.status == ResultStatus.BAD_REQUEST
    	res.errorMessages[0] == "callbackService.process.invalid"
    }

    void "test process for non-US numbers"() {
        when: "incoming is non-US number for text"
        TypeConvertingMap params = new TypeConvertingMap([To:"blah", From:"invalid", MessageSid:"ok"])
        Result<Closure> res = service.process(params)

        then:
        res.success == true
        TestHelpers.buildXml(res.payload) == TestHelpers.buildXml {
            Response {
                Message("twimlBuilder.invalidNumber")
            }
        }

        when: "incoming is non-US number for call"
        params = new TypeConvertingMap([To: "blah", From:"invalid", CallSid:"ok"])
        res = service.process(params)

        then:
        res.success == true
        TestHelpers.buildXml(res.payload) == TestHelpers.buildXml {
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
        TypeConvertingMap params = new TypeConvertingMap([handle:CallResponse.DIRECT_MESSAGE.toString()])
        Result<Closure> res = service.process(params)

        then:
        1 * service.outgoingMessageService.directMessageCall(*_) >> new Result()
        res.status == ResultStatus.OK

        when: "no-op"
        params = new TypeConvertingMap([handle:CallResponse.DO_NOTHING.toString()])
        res = service.process(params)

        then:
        0 * service.outgoingMessageService._
        res.status == ResultStatus.OK
        TestHelpers.buildXml(res.payload) == TestHelpers.buildXml { Response { } }

        when: "hanging up"
        params = new TypeConvertingMap([handle:CallResponse.END_CALL.toString()])
        res = service.process(params)

        then:
        0 * service.outgoingMessageService._
        res.status == ResultStatus.OK
        TestHelpers.buildXml(res.payload) == TestHelpers.buildXml { Response { Hangup() } }

        when: "voicemail greeting response"
        params = new TypeConvertingMap([handle:CallResponse.VOICEMAIL_GREETING_PROCESSING.toString()])
        res = service.process(params)

        then:
        0 * service.outgoingMessageService._
        res.status == ResultStatus.OK
        TestHelpers.buildXml(res.payload).contains("twimlBuilder.call.processingVoicemailGreeting")
        TestHelpers.buildXml(res.payload).contains(Constants.CALL_HOLD_MUSIC_URL)
    }

    void "test handling sessions"() {
        given:
        PhoneNumber originalFromNumber = new PhoneNumber(number: TestHelpers.randPhoneNumber())
        PhoneNumber fromNumber = new PhoneNumber(number: TestHelpers.randPhoneNumber())
        PhoneNumber toNumber = new PhoneNumber(number: TestHelpers.randPhoneNumber())
        [originalFromNumber, fromNumber, toNumber].each { assert it.validate() }
        TypeConvertingMap params = new TypeConvertingMap([originalFrom: originalFromNumber.number])
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

    void "processing valid inputs overall"() {
        given:
        service.incomingMessageService = Mock(IncomingMessageService)

        when: "call sid specified"
        TypeConvertingMap params = new TypeConvertingMap(To:p1.numberAsString, From:"1112223333",
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
        Result<Closure> res = service.processText(null, null, null, new TypeConvertingMap())

        then:
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
    }

    void "test announcement-related texts"() {
        given:
        service.announcementService = Mock(AnnouncementService)
        service.incomingMessageService = Mock(IncomingMessageService)
        Phone thisPhone = Mock(Phone)
        TypeConvertingMap params = new TypeConvertingMap(Body: Constants.TEXT_SEE_ANNOUNCEMENTS,
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
        TypeConvertingMap params = new TypeConvertingMap(Body: "hi", NumSegments: 8)

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
        IncomingSession is1 = [number: TestHelpers.randPhoneNumber()] as IncomingSession

        when: "starting voicemail"
        String clientNum = URLEncoder.encode("+1233834920", "UTF-8")
        TypeConvertingMap params = new TypeConvertingMap(CallSid: "iamasid!!",
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
        TestHelpers.buildXml(res.payload).contains("twimlBuilder.call.voicemailDirections")

        when:
        params.DialCallStatus = "delivered"
        res = service.processCall(p1, is1, null, params)

        then:
        res.status == ResultStatus.OK
        TestHelpers.buildXml(res.payload) == TestHelpers.buildXml { Response { } }

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
        TestHelpers.buildXml(res.payload) == TestHelpers.buildXml { Response { } }

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
    	TypeConvertingMap params = new TypeConvertingMap([CallSid:"iamasid!!",
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
        Phone thisPhone = Mock(Phone)

        when: "recording voicemail greeting"
        TypeConvertingMap params = new TypeConvertingMap(handle: CallResponse.VOICEMAIL_GREETING_RECORD)
        Result<Closure> res = service.processCall(p1, null, null, params)

        then:
        res.status == ResultStatus.OK
        TestHelpers.buildXml(res.payload).contains("twimlBuilder.call.recordVoicemailGreeting")

        when: "finished processing voicemail greeting"
        params.handle = CallResponse.VOICEMAIL_GREETING_PROCESSED
        res = service.processCall(p1, null, null, params)

        then:
        1 * service.threadService.delay(*_) >> { args -> args[2].call(); null; }
        1 * service.voicemailService.finishedProcessingVoicemailGreeting(*_) >> new Result()
        res.status == ResultStatus.OK
        TestHelpers.buildXml(res.payload) == TestHelpers.buildXml { Response { } }

        when: "playing voicemail greeting"
        params.handle = CallResponse.VOICEMAIL_GREETING_PLAY
        res = service.processCall(thisPhone, null, null, params)

        then:
        1 * thisPhone.number >> new PhoneNumber(number: TestHelpers.randPhoneNumber())
        1 * thisPhone.voicemailGreetingUrl >> new URL("https://www.example.com")
        res.status == ResultStatus.OK
        TestHelpers.buildXml(res.payload).contains("twimlBuilder.call.finishedVoicemailGreeting")
    }
}
