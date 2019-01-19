package org.textup.rest

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import groovy.transform.TypeCheckingMode
import java.util.concurrent.TimeUnit
import org.textup.rest.*
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*

@GrailsTypeChecked
@Transactional
class CallbackService {

    AnnouncementCallbackService announcementCallbackService
    CallCallbackService callCallbackService
    IncomingCallService incomingCallService
    IncomingTextService incomingTextService
    ThreadService threadService
    VoicemailCallbackService voicemailCallbackService

    @OptimisticLockingRetry
    @RollbackOnResultFailure
    Result<Closure> process(TypeMap params) {
        processAnonymousCall(params) {
            String callId = params.string(TwilioUtils.ID_CALL),
                textId = params.string(TwilioUtils.ID_TEXT)
            PhoneNumber.tryUrlDecode(params.string(TwilioUtils.FROM))
                .then { PhoneNumber pNum ->
                    PhoneNumber.tryUrlDecode(params.string(TwilioUtils.TO)).curry(pNum)
                }
                .ifFail { callId ? CallTwiml.invalid() : TextTwiml.invalid() }
                .then { PhoneNumber fromNum, PhoneNumber toNum ->
                    BasePhoneNumber phoneNum = CallbackUtils.numberForPhone(fromNum, toNum, params)
                    Phones.mustFindActiveForNumber(phoneNum).curry(fromNum, toNum)
                }
                .ifFail { callId ? CallTwiml.notFound() : TextTwiml.notFound() }
                .then { PhoneNumber fromNum, PhoneNumber toNum, Phone p1 ->
                    CallbackUtils.tryGetNumberForSession(fromNum, toNum, params).curry(p1)
                }
                .then { Phone p1, BasePhoneNumber sessNum ->
                    IncomingSessions.mustFindForPhoneAndNumber(p1, sessNum, true).curry(p1)
                }
                .then { Phone p1, IncomingSession is1 ->
                    if (callId) {
                        processCall(p1, is1, callId, params)
                    }
                    else if (textId) {
                        processText(p1, is1, textId, params)
                    }
                    else {
                        resultFactory.failWithCodeAndStatus("callbackService.process.invalid",
                            ResultStatus.BAD_REQUEST)
                    }
                }
        }
    }

    // Helpers
    // -------

    protected Result<Closure> processAnonymousCall(TypeMap params,
        Closure<Result<Closure>> continueProcessing) {

        switch (params.enum(CallResponse, CallbackUtils.PARAM_HANDLE)) {
            case CallResponse.DIRECT_MESSAGE:
                callCallbackService.directMessageCall(CallTwiml.extractDirectMessageToken(params))
                break
            case CallResponse.DO_NOTHING:
                TwilioUtils.noResponseTwiml()
                break
            case CallResponse.END_CALL:
                CallTwiml.hangUp()
                break
            case CallResponse.VOICEMAIL_GREETING_PROCESSING:
                CallTwiml.processingVoicemailGreeting()
                break
            default:
                continueProcessing()
        }
    }

    protected Result<Closure> processCall(Phone p1, IncomingSession is1, String apiId,
        TypeMap params) {

        String digits = params.string(TwilioUtils.DIGITS)
        switch(params.enum(CallResponse, CallbackUtils.PARAM_HANDLE)) {
            case CallResponse.CHECK_IF_VOICEMAIL:
                callCallbackService.checkIfVoicemail(p1, is1, params)
                break
            case CallResponse.VOICEMAIL_DONE:
                // in about 5% of cases, when the RecordingStatusCallback is called, the recording
                // at the provided url still isn't ready and a request to that url returns a
                // NOT_FOUND. Therefore, we wait a few seconds to ensure that the voicemail
                // is completely done being stored before attempting to fetch it.
                threadService.delay(5, TimeUnit.SECONDS) {
                    IncomingRecordingInfo im1 = TwilioUtils.buildIncomingRecording(params)
                    int duration = params.int(TwilioUtils.RECORDING_DURATION, 0)
                    voicemailCallbackService.processVoicemailMessage(apiId, duration, im1)
                        .logFail("processCall: VOICEMAIL_DONE")
                }
                TwilioUtils.noResponseTwiml()
                break
            case CallResponse.VOICEMAIL_GREETING_RECORD:
                CallTwiml.recordVoicemailGreeting(p1.number, is1.number)
                break
            case CallResponse.VOICEMAIL_GREETING_PROCESSED:
                threadService.delay(5, TimeUnit.SECONDS) {
                    IncomingRecordingInfo im1 = TwilioUtils.buildIncomingRecording(params)
                    voicemailCallbackService.finishProcessingVoicemailGreeting(p1.id, apiId, im1)
                        .logFail("processCall: VOICEMAIL_GREETING_PROCESSED")
                }
                TwilioUtils.noResponseTwiml()
                break
            case CallResponse.VOICEMAIL_GREETING_PLAY:
                CallTwiml.playVoicemailGreeting(p1.number, p1.voicemailGreetingUrl)
                break
            case CallResponse.ANNOUNCEMENT_AND_DIGITS:
                announcementCallbackService.completeCallAnnouncement(is1, digits, params)
                break
            case CallResponse.FINISH_BRIDGE:
                CallTwiml.finishBridge(params)
                break
            case CallResponse.SCREEN_INCOMING:
                callCallbackService.screenIncomingCall(p1, is1)
                break
            default:
                incomingCallService.process(p1, is1, apiId, digits)
        }
    }

    protected Result<Closure> processText(Phone p1, IncomingSession is1, String apiId,
        TypeMap params) {

        String message = params.string(TwilioUtils.BODY)
        Integer numSegments = params.int(TwilioUtils.NUM_SEGMENTS)
        List<IncomingMediaInfo> media = TwilioUtils.buildIncomingMedia(apiId, params)
        switch (message) {
            case TextTwiml.BODY_SEE_ANNOUNCEMENTS:
                announcementCallbackService.textSeeAnnouncements(p1, is1) {
                    incomingTextService.process(p1, is1, apiId, message, numSegments, media)
                }
                break
            case TextTwiml.BODY_TOGGLE_SUBSCRIBE:
                announcementCallbackService.textToggleSubscribe(is1)
                break
            default:
                incomingTextService.process(p1, is1, apiId, message, numSegments, media)
        }
    }
}
