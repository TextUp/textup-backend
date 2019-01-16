package org.textup.rest

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import groovy.transform.TypeCheckingMode
import java.util.concurrent.TimeUnit
import org.codehaus.groovy.grails.web.util.TypeConvertingMap
import org.textup.rest.*
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*

@GrailsTypeChecked
@Transactional
class CallbackService {

    AnnouncementService announcementService
    IncomingMessageService incomingMessageService
    OutgoingMessageService outgoingMessageService
    ResultFactory resultFactory
    ThreadService threadService
    VoicemailService voicemailService

    // Process request
    // ---------------

    @OptimisticLockingRetry
    @RollbackOnResultFailure
    Result<Closure> process(TypeConvertingMap params) {
        processAnonymousCall(params) {
            // step 1: check that both to and from numbers are valid US phone numbers
            PhoneNumber fromNum = PhoneNumber.urlDecode(params.From as String),
                toNum = PhoneNumber.urlDecode(params.To as String)
            if (!fromNum.validate() || !toNum.validate()) {
                return params.CallSid ? CallTwiml.invalid() : TextTwiml.invalid()
            }
            // step 2: get phone
            BasePhoneNumber pNum = getNumberForPhone(fromNum, toNum, params)
            Phone p1 = Phone.findByNumberAsString(pNum.number)
            if (!p1) {
                return params.CallSid ? CallTwiml.notFound() : TextTwiml.notFound()
            }
            // step 3: get session
            getOrCreateIncomingSession(p1, fromNum, toNum, params).then { IncomingSession is1 ->
                // step 4: process request
                String apiId = params.CallSid ?: params.MessageSid
                if (params.CallSid) {
                    processCall(p1, is1, apiId, params)
                }
                else if (params.MessageSid) {
                    processText(p1, is1, apiId, params)
                }
                else {
                    resultFactory.failWithCodeAndStatus("callbackService.process.invalid",
                        ResultStatus.BAD_REQUEST)
                }
            }
        }
    }

    // Gathering objects
    // -----------------

    protected Result<IncomingSession> getOrCreateIncomingSession(Phone p1, BasePhoneNumber fromNum,
        BasePhoneNumber toNum, TypeConvertingMap params) {
        //usually handle incoming from session (client) to phone (staff)
        BasePhoneNumber sessionNum = fromNum
        // finish bridge is call from phone to personal phone
        // announcements are from phone to session (client)
        if (params.handle == CallResponse.FINISH_BRIDGE.toString() ||
            params.handle == CallResponse.ANNOUNCEMENT_AND_DIGITS.toString() ||
            params.handle == CallResponse.VOICEMAIL_GREETING_RECORD.toString() ||
            params.handle == CallResponse.VOICEMAIL_GREETING_PROCESSED.toString() ||
            params.handle == CallResponse.VOICEMAIL_GREETING_PLAY.toString()) {
            sessionNum = toNum
        }
        // when screening incoming calls, the From number is the TextUp phone,
        // the original caller is stored in the originalFrom parameter and the
        // To number is actually the staff member's personal phone number
        else if (params.handle == CallResponse.SCREEN_INCOMING.toString() && params.originalFrom) {
            sessionNum = PhoneNumber.urlDecode(params.originalFrom as String)
        }
        IncomingSession is1 = IncomingSession.findByPhoneAndNumberAsString(p1, sessionNum.number)
        // create session for this phone if one doesn't exist yet
        if (!is1) {
            is1 = new IncomingSession(phone:p1, numberAsString:sessionNum.number)
            if (!is1.save()) { return resultFactory.failWithValidationErrors(is1.errors) }
        }
        resultFactory.success(is1)
    }

    protected BasePhoneNumber getNumberForPhone(BasePhoneNumber fromNum, BasePhoneNumber toNum,
        TypeConvertingMap params) {

        //usually handle incoming from session (client) to phone (staff)
        BasePhoneNumber phoneNum = toNum
        // (1) finish bridge is call from phone to personal phone
        // (2) announcements are from phone to session (client)
        // (3) when screening incoming calls, the From number is the TextUp phone,
        //      the original caller is stored in the originalFrom parameter and the
        //      To number is actually the staff member's personal phone number
        // (4 + 5) recording voicemail greeting takes place when TextUp phone calls personal phone
        if (params.handle == CallResponse.FINISH_BRIDGE.toString() ||
            params.handle == CallResponse.ANNOUNCEMENT_AND_DIGITS.toString() ||
            params.handle == CallResponse.SCREEN_INCOMING.toString() ||
            params.handle == CallResponse.VOICEMAIL_GREETING_RECORD.toString() ||
            params.handle == CallResponse.VOICEMAIL_GREETING_PROCESSED.toString() ||
            params.handle == CallResponse.VOICEMAIL_GREETING_PLAY.toString()) {
            phoneNum = fromNum
        }

        phoneNum
    }

    // Processing
    // ----------

    protected Result<Closure> processAnonymousCall(TypeConvertingMap params,
        Closure<Result<Closure>> continueProcessing) {

        switch (params.handle) {
            case CallResponse.DIRECT_MESSAGE.toString():
                outgoingMessageService.directMessageCall(params.token as String)
                break
            case CallResponse.DO_NOTHING.toString():
                TwilioUtils.noResponseTwiml()
                break
            case CallResponse.END_CALL.toString():
                CallTwiml.hangUp()
                break
            case CallResponse.VOICEMAIL_GREETING_PROCESSING.toString():
                CallTwiml.processingVoicemailGreeting()
                break
            default:
                continueProcessing()
        }
    }

    protected Result<Closure> processCall(Phone p1, IncomingSession is1, String callId,
        TypeConvertingMap params) {

        String digits = params.Digits
        switch(params.handle) {
            case CallResponse.CHECK_IF_VOICEMAIL.toString():
                ReceiptStatus rStatus = ReceiptStatus.translate(params.DialCallStatus as String)
                if (rStatus == ReceiptStatus.SUCCESS) { // call already connected so no voicemail
                    TwilioUtils.noResponseTwiml()
                }
                else { CallTwiml.recordVoicemailMessage(p1, is1.number) }
                break
            case CallResponse.VOICEMAIL_DONE.toString():
                // in about 5% of cases, when the RecordingStatusCallback is called, the recording
                // at the provided url still isn't ready and a request to that url returns a
                // NOT_FOUND. Therefore, we wait a few seconds to ensure that the voicemail
                // is completely done being stored before attempting to fetch it.
                int duration = params.int("RecordingDuration", 0)
                IncomingRecordingInfo im1 = TwilioUtils.buildIncomingRecording(params)
                threadService.delay(5, TimeUnit.SECONDS) {
                    voicemailService.processVoicemailMessage(callId, duration, im1)
                        .logFail("processCall: VOICEMAIL_DONE")
                }
                TwilioUtils.noResponseTwiml()
                break
            case CallResponse.VOICEMAIL_GREETING_RECORD.toString():
                CallTwiml.recordVoicemailGreeting(p1.number, is1.number)
                break
            case CallResponse.VOICEMAIL_GREETING_PROCESSED.toString():
                IncomingRecordingInfo im1 = TwilioUtils.buildIncomingRecording(params)
                threadService.delay(5, TimeUnit.SECONDS) {
                    voicemailService.finishProcessingVoicemailGreeting(p1.id, callId, im1)
                        .logFail("processCall: VOICEMAIL_GREETING_PROCESSED")
                }
                TwilioUtils.noResponseTwiml()
                break
            case CallResponse.VOICEMAIL_GREETING_PLAY.toString():
                CallTwiml.playVoicemailGreeting(p1.number, p1.voicemailGreetingUrl)
                break
            case CallResponse.ANNOUNCEMENT_AND_DIGITS.toString():
                String msg = params.message,
                    ident = params.identifier
                announcementService.completeCallAnnouncement(digits, msg, ident, is1)
                break
            case CallResponse.FINISH_BRIDGE.toString():
                outgoingMessageService.finishBridgeCall(params)
                break
            case CallResponse.SCREEN_INCOMING.toString():
                incomingMessageService.screenIncomingCall(p1, is1)
                break
            default:
                incomingMessageService.receiveCall(p1, callId, digits, is1)
        }
    }

    protected Result<Closure> processText(Phone p1, IncomingSession sess1, String messageId,
        TypeConvertingMap params) {

        // step 1: store incoming text without media
        IncomingText text = new IncomingText(apiId: messageId, message: params.Body as String,
            numSegments: params.int("NumSegments"))
        if (!text.validate()) {
            return resultFactory.failWithValidationErrors(text.errors)
        }
        switch (text.message) {
            case Constants.TEXT_SEE_ANNOUNCEMENTS:
                Collection<FeaturedAnnouncement> announces = p1.getAnnouncements()
                if (announces) {
                    announcementService.textSeeAnnouncements(announces, sess1)
                }
                else { incomingMessageService.processText(p1, text, sess1, params) }
                break
            case Constants.TEXT_TOGGLE_SUBSCRIBE:
                announcementService.textToggleSubscribe(sess1)
                break
            default:
                incomingMessageService.processText(p1, text, sess1, params)
        }
    }
}
