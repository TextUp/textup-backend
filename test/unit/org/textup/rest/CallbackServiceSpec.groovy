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
@TestFor(CallbackService)
@TestMixin(HibernateTestMixin)
class CallbackServiceSpec extends Specification {

	static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test processing text"() {
        given:
        String apiId = TestUtils.randString()
        TypeMap params1 = TypeMap.create((TwilioUtils.BODY): TextTwiml.BODY_SEE_ANNOUNCEMENTS,
            (TwilioUtils.NUM_SEGMENTS): TestUtils.randIntegerUpTo(88, true))
        TypeMap params2 = TypeMap.create((TwilioUtils.BODY): TextTwiml.BODY_TOGGLE_SUBSCRIBE,
            (TwilioUtils.NUM_SEGMENTS): TestUtils.randIntegerUpTo(88, true))
        TypeMap params3 = TypeMap.create((TwilioUtils.BODY): TestUtils.randString(),
            (TwilioUtils.NUM_SEGMENTS): TestUtils.randIntegerUpTo(88, true))

        Phone p1 = GroovyMock()
        IncomingSession is1 = GroovyMock()
        IncomingMediaInfo im1 = GroovyMock()
        service.announcementCallbackService = GroovyMock(AnnouncementCallbackService)
        service.incomingTextService = GroovyMock(IncomingTextService)
        MockedMethod tryBuildIncomingMedia = MockedMethod.create(TwilioUtils, "tryBuildIncomingMedia") {
            Result.createSuccess([im1])
        }

        when:
        Result res = service.processText(p1, is1, apiId, params1)

        then:
        tryBuildIncomingMedia.latestArgs == [apiId, params1]
        1 * service.announcementCallbackService.textSeeAnnouncements(p1, is1, _ as Closure) >> { args ->
            args[2].call()
        }
        1 * service.incomingTextService.process(p1,
            is1,
            apiId,
            TextTwiml.BODY_SEE_ANNOUNCEMENTS,
            params1[TwilioUtils.NUM_SEGMENTS],
            [im1]) >> Result.void()
        res.status == ResultStatus.NO_CONTENT

        when:
        res = service.processText(p1, is1, apiId, params2)

        then:
        tryBuildIncomingMedia.latestArgs == [apiId, params2]
        1 * service.announcementCallbackService.textToggleSubscribe(is1) >> Result.void()
        res.status == ResultStatus.NO_CONTENT

        when:
        res = service.processText(p1, is1, apiId, params3)

        then:
        tryBuildIncomingMedia.latestArgs == [apiId, params3]
        1 * service.incomingTextService.process(p1,
            is1,
            apiId,
            params3[TwilioUtils.BODY],
            params3[TwilioUtils.NUM_SEGMENTS],
            [im1]) >> Result.void()
        res.status == ResultStatus.NO_CONTENT

        cleanup:
        tryBuildIncomingMedia?.restore()
    }

    void "test when voicemail greeting has finished processing"() {
        given:
        Long pId = TestUtils.randIntegerUpTo(88)
        String apiId = TestUtils.randString()
        TypeMap params = TestUtils.randTypeMap()

        IncomingRecordingInfo ir1 = GroovyMock()
        service.threadService = GroovyMock(ThreadService)
        service.voicemailCallbackService = GroovyMock(VoicemailCallbackService)
        MockedMethod tryCreate = MockedMethod.create(IncomingRecordingInfo, "tryCreate") {
            Result.createSuccess(ir1)
        }

        when:
        Result res = service.processFinishedVoicemailGreeting(pId, apiId, params)

        then:
        tryCreate.latestArgs == [params]
        1 * service.threadService.delay(*_) >> { args -> args[2].call(); null; }
        1 * service.voicemailCallbackService.finishProcessingVoicemailGreeting(pId, apiId, ir1) >> Result.void()
        res.status == ResultStatus.OK
        TestUtils.buildXml(res.payload) == TestUtils.buildXml { Response { } }

        cleanup:
        tryCreate?.restore()
    }

    void "test when recorded voicemail message has finished processing"() {
        given:
        String apiId = TestUtils.randString()
        TypeMap params = TypeMap.create((TwilioUtils.RECORDING_DURATION): TestUtils.randIntegerUpTo(88, true))

        IncomingRecordingInfo ir1 = GroovyMock()
        service.threadService = GroovyMock(ThreadService)
        service.voicemailCallbackService = GroovyMock(VoicemailCallbackService)
        MockedMethod tryCreate = MockedMethod.create(IncomingRecordingInfo, "tryCreate") {
            Result.createSuccess(ir1)
        }

        when:
        Result res = service.processFinishedVoicemailMessage(apiId, params)

        then:
        tryCreate.latestArgs == [params]
        1 * service.threadService.delay(*_) >> { args -> args[2].call(); null; }
        1 * service.voicemailCallbackService.processVoicemailMessage(apiId,
            params[TwilioUtils.RECORDING_DURATION],
            ir1) >> Result.void()
        res.status == ResultStatus.OK
        TestUtils.buildXml(res.payload) == TestUtils.buildXml { Response { } }

        cleanup:
        tryCreate?.restore()
    }

    void "test processing call"() {
        given:
        String apiId = TestUtils.randString()
        TypeMap params

        Phone p1 = TestUtils.buildActiveStaffPhone()
        IncomingSession is1 = TestUtils.buildSession()

        service.announcementCallbackService = GroovyMock(AnnouncementCallbackService)
        service.callCallbackService = GroovyMock(CallCallbackService)
        service.incomingCallService = GroovyMock(IncomingCallService)
        MockedMethod processFinishedVoicemailMessage = MockedMethod.create(service, "processFinishedVoicemailMessage") {
            Result.void()
        }
        MockedMethod recordVoicemailGreeting = MockedMethod.create(CallTwiml, "recordVoicemailGreeting") {
            Result.void()
        }
        MockedMethod processFinishedVoicemailGreeting = MockedMethod.create(service, "processFinishedVoicemailGreeting") {
            Result.void()
        }
        MockedMethod playVoicemailGreeting = MockedMethod.create(CallTwiml, "playVoicemailGreeting") {
            Result.void()
        }
        MockedMethod finishBridge = MockedMethod.create(CallTwiml, "finishBridge") {
            Result.void()
        }

        when:
        params = TypeMap.create((CallbackUtils.PARAM_HANDLE): CallResponse.CHECK_IF_VOICEMAIL)
        Result res = service.processCall(p1, is1, apiId, params)

        then:
        1 * service.callCallbackService.checkIfVoicemail(p1, is1, { it == params }) >> Result.void()
        res.status == ResultStatus.NO_CONTENT

        when:
        params = TypeMap.create((CallbackUtils.PARAM_HANDLE): CallResponse.VOICEMAIL_DONE)
        res = service.processCall(p1, is1, apiId, params)

        then:
        processFinishedVoicemailMessage.latestArgs == [apiId, params]
        res.status == ResultStatus.NO_CONTENT

        when:
        params = TypeMap.create((CallbackUtils.PARAM_HANDLE): CallResponse.VOICEMAIL_GREETING_RECORD)
        res = service.processCall(p1, is1, apiId, params)

        then:
        recordVoicemailGreeting.latestArgs == [p1.number, is1.number]
        res.status == ResultStatus.NO_CONTENT

        when:
        params = TypeMap.create((CallbackUtils.PARAM_HANDLE): CallResponse.VOICEMAIL_GREETING_PROCESSED)
        res = service.processCall(p1, is1, apiId, params)

        then:
        processFinishedVoicemailGreeting.latestArgs == [p1.id, apiId, params]
        res.status == ResultStatus.NO_CONTENT

        when:
        params = TypeMap.create((CallbackUtils.PARAM_HANDLE): CallResponse.VOICEMAIL_GREETING_PLAY)
        res = service.processCall(p1, is1, apiId, params)

        then:
        playVoicemailGreeting.latestArgs == [p1.number, p1.voicemailGreetingUrl]
        res.status == ResultStatus.NO_CONTENT

        when:
        params = TypeMap.create((CallbackUtils.PARAM_HANDLE): CallResponse.ANNOUNCEMENT_AND_DIGITS,
            (TwilioUtils.DIGITS): TestUtils.randString())
        res = service.processCall(p1, is1, apiId, params)

        then:
        1 * service.announcementCallbackService.completeCallAnnouncement(is1,
            { it == params[TwilioUtils.DIGITS] },
            { it == params }) >> Result.void()
        res.status == ResultStatus.NO_CONTENT

        when:
        params = TypeMap.create((CallbackUtils.PARAM_HANDLE): CallResponse.FINISH_BRIDGE)
        res = service.processCall(p1, is1, apiId, params)

        then:
        finishBridge.latestArgs == [params]
        res.status == ResultStatus.NO_CONTENT

        when:
        params = TypeMap.create((CallbackUtils.PARAM_HANDLE): CallResponse.SCREEN_INCOMING)
        res = service.processCall(p1, is1, apiId, params)

        then:
        1 * service.callCallbackService.screenIncomingCall(p1, is1) >> Result.void()
        res.status == ResultStatus.NO_CONTENT

        when:
        params = TypeMap.create((CallbackUtils.PARAM_HANDLE): TestUtils.randString(),
            (TwilioUtils.DIGITS): TestUtils.randString())
        res = service.processCall(p1, is1, apiId, params)

        then:
        1 * service.incomingCallService.process(p1,
            is1,
            apiId,
            { it == params[TwilioUtils.DIGITS] }) >> Result.void()
        res.status == ResultStatus.NO_CONTENT

        cleanup:
        processFinishedVoicemailMessage?.restore()
        recordVoicemailGreeting?.restore()
        processFinishedVoicemailGreeting?.restore()
        playVoicemailGreeting?.restore()
        finishBridge?.restore()
    }

    @Unroll
    void "test processing identified message errors for #messageIdName"() {
        given:
        TypeMap params1 = TypeMap.create((TwilioUtils.FROM): TestUtils.randString(),
            (messageIdName): TestUtils.randString())
        TypeMap params2 = TypeMap.create((TwilioUtils.FROM): TestUtils.randPhoneNumberString(),
            (TwilioUtils.TO): TestUtils.randPhoneNumberString(),
            (messageIdName): TestUtils.randString())

        MockedMethod mustFindActiveForNumber = MockedMethod.create(Phones, "mustFindActiveForNumber") {
            Result.createError([], ResultStatus.BAD_REQUEST)
        }

        when:
        Result res = service.processIdentifiedMessage(params1)

        then:
        mustFindActiveForNumber.notCalled
        res.status == ResultStatus.OK
        TestUtils.buildXml(res.payload).contains("twiml.invalidNumber")
        TestUtils.buildXml(res.payload).contains(twimlString)

        when:
        res = service.processIdentifiedMessage(params2)

        then:
        mustFindActiveForNumber.hasBeenCalled
        res.status == ResultStatus.OK
        TestUtils.buildXml(res.payload).contains("twiml.notFound")
        TestUtils.buildXml(res.payload).contains(twimlString)

        cleanup:
        mustFindActiveForNumber?.restore()

        where:
        messageIdName       | twimlString
        TwilioUtils.ID_CALL | "Say"
        TwilioUtils.ID_TEXT | "Message"
    }

    void "test processing identified message"() {
        given:
        Phone p1 = TestUtils.buildActiveStaffPhone()
        IncomingSession is1 = TestUtils.buildSession(p1)

        TypeMap params1 = TypeMap.create((TwilioUtils.FROM): is1.number,
            (TwilioUtils.TO): p1.number)
        TypeMap params2 = TypeMap.create((TwilioUtils.FROM): is1.number,
            (TwilioUtils.TO): p1.number,
            (TwilioUtils.ID_CALL): TestUtils.randString())
        TypeMap params3 = TypeMap.create((TwilioUtils.FROM): is1.number,
            (TwilioUtils.TO): p1.number,
            (TwilioUtils.ID_TEXT): TestUtils.randString())

        MockedMethod processCall = MockedMethod.create(service, "processCall") { Result.void() }
        MockedMethod processText = MockedMethod.create(service, "processText") { Result.void() }

        when:
        Result res = service.processIdentifiedMessage(params1)

        then:
        res.status == ResultStatus.BAD_REQUEST
        res.errorMessages == ["callbackService.neitherCallNorText"]

        when:
        res = service.processIdentifiedMessage(params2)

        then:
        processCall.latestArgs == [p1, is1, params2[TwilioUtils.ID_CALL], params2]
        res.status == ResultStatus.NO_CONTENT

        when:
        res = service.processIdentifiedMessage(params3)

        then:
        processText.latestArgs == [p1, is1, params3[TwilioUtils.ID_TEXT], params3]
        res.status == ResultStatus.NO_CONTENT

        cleanup:
        processCall?.restore()
        processText?.restore()
    }

    void "test processing anonymous call"() {
        given:
        TypeMap params
        String token1 = TestUtils.randString()

        service.callCallbackService = GroovyMock(CallCallbackService)
        MockedMethod extractDirectMessageToken = MockedMethod.create(CallTwiml, "extractDirectMessageToken") {
            token1
        }
        int callCount = 0
        Closure continueAction = { ++callCount; Result.void() }

        when:
        params = TypeMap.create((CallbackUtils.PARAM_HANDLE): CallResponse.DIRECT_MESSAGE)
        Result res = service.processAnonymousCall(params, continueAction)

        then:
        callCount == 0
        extractDirectMessageToken.hasBeenCalled
        1 * service.callCallbackService.directMessageCall(token1) >> Result.void()
        res.status == ResultStatus.NO_CONTENT

        when:
        params = TypeMap.create((CallbackUtils.PARAM_HANDLE): CallResponse.DO_NOTHING)
        res = service.processAnonymousCall(params, continueAction)

        then:
        callCount == 0
        res.status == ResultStatus.OK
        TestUtils.buildXml(res.payload) == TestUtils.buildXml { Response { } }

        when:
        params = TypeMap.create((CallbackUtils.PARAM_HANDLE): CallResponse.END_CALL)
        res = service.processAnonymousCall(params, continueAction)

        then:
        callCount == 0
        res.status == ResultStatus.OK
        TestUtils.buildXml(res.payload) == TestUtils.buildXml { Response { Hangup() } }

        when:
        params = TypeMap.create((CallbackUtils.PARAM_HANDLE): CallResponse.VOICEMAIL_GREETING_PROCESSING)
        res = service.processAnonymousCall(params, continueAction)

        then:
        callCount == 0
        res.status == ResultStatus.OK
        TestUtils.buildXml(res.payload).contains("callTwiml.processingVoicemailGreeting")

        when:
        params = TypeMap.create((CallbackUtils.PARAM_HANDLE): TestUtils.randString())
        res = service.processAnonymousCall(params, continueAction)

        then:
        callCount == 1
        res.status == ResultStatus.NO_CONTENT

        cleanup:
        extractDirectMessageToken?.restore()
    }

    void "test processing overall"() {
        given:
        TypeMap params = TestUtils.randTypeMap()

        MockedMethod processAnonymousCall = MockedMethod.create(service, "processAnonymousCall") {
            Result.void()
        }
        MockedMethod processIdentifiedMessage = MockedMethod.create(service, "processIdentifiedMessage") {
            Result.void()
        }

        when:
        Result res = service.process(params)

        then:
        processAnonymousCall.latestArgs[0] == params
        processAnonymousCall.latestArgs[1] instanceof Closure
        processIdentifiedMessage.notCalled
        res.status == ResultStatus.NO_CONTENT

        when:
        def retVal = processAnonymousCall.latestArgs[1].call()

        then:
        processIdentifiedMessage.latestArgs == [params]
        retVal.status == ResultStatus.NO_CONTENT

        cleanup:
        processAnonymousCall?.restore()
        processIdentifiedMessage?.restore()
    }
}
