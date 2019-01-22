package org.textup.rest

import org.joda.time.DateTime
import org.textup.*
import org.textup.type.*
import org.textup.validator.*
import org.textup.util.*

// Not type checked because of our use of Closures as a DSL for building Twiml.

class CallTwiml {

    static final String DIGITS_HEAR_ANNOUNCEMENTS = "1"
    static final String DIGITS_TOGGLE_SUBSCRIBE = "2"
    static final String DIGITS_ANNOUNCEMENT_UNSUBSCRIBE = "1"

    private static final int DIRECT_MESSAGE_MAX_REPEATS = 5
    private static final String ANNOUNCEMENT_AND_DIGITS_IDENT = "identifier"
    private static final String ANNOUNCEMENT_AND_DIGITS_MSG = "message"
    private static final String DIRECT_MESSAGE_TOKEN = "token"
    private static final String FINISH_BRIDGE_WRAPPER_ID = "contactId"
    private static final String HOLD_MUSIC_URL = "http://com.twilio.music.guitars.s3.amazonaws.com/Pitx_-_Long_Winter.mp3"
    private static final String SCREEN_INCOMING_FROM = "originalFrom"

    // CallResponse.DIRECT_MESSAGE
    static Map<String, String> infoForDirectMessage(String token) {
        [
            (CallbackUtils.PARAM_HANDLE): CallResponse.DIRECT_MESSAGE,
            (DIRECT_MESSAGE_TOKEN): token
        ]
    }

    static String extractDirectMessageToken(TypeMap params) { params.string(DIRECT_MESSAGE_TOKEN) }

    static Result<Closure> directMessage(String ident, String message, VoiceLanguage lang,
        List<URL> recordingUrls = []) {

        if (!ident || !message || !lang || recordingUrls == null) {
            return TwilioUtils.invalidTwimlInputs(CallResponse.DIRECT_MESSAGE.toString())
        }
        String messageIntro = IOCUtils.getMessage("twimlBuilder.call.messageIntro", [ident])
        TwilioUtils.wrapTwiml {
            Say(messageIntro)
            Pause(length: 1)
            DIRECT_MESSAGE_MAX_REPEATS.times {
                Say(language: lang.toTwimlValue(), message)
                recordingUrls.each { URL url -> Play(url.toString()) }
            }
            Hangup()
        }
    }

    // CallResponse.END_CALL
    static Result<Closure> hangUp() {
        TwilioUtils.wrapTwiml { Hangup() }
    }

    // CallResponse.BLOCKED
    static Result<Closure> blocked() {
        TwilioUtils.wrapTwiml { Reject(reason: "rejected") }
    }

    // Errors
    // ------

    static Result<Closure> invalid() {
        String invalidNumber = IOCUtils.getMessage("twimlBuilder.invalidNumber")
        TwilioUtils.wrapTwiml {
            Say(invalidNumber)
            Hangup()
        }
    }

    static Result<Closure> notFound() {
        String notFound = IOCUtils.getMessage("twimlBuilder.notFound")
        TwilioUtils.wrapTwiml {
            Say(notFound)
            Hangup()
        }
    }

    static Result<Closure> error() {
        String error = IOCUtils.getMessage("twimlBuilder.error")
        TwilioUtils.wrapTwiml {
            Say(error)
            Hangup()
        }
    }

    // Self calls
    // ----------

    // CallResponse.SELF_GREETING
    static Result<Closure> selfGreeting() {
        String directions = IOCUtils.getMessage("twimlBuilder.call.selfGreeting"),
            goodbye = IOCUtils.getMessage("twimlBuilder.call.goodbye")
        TwilioUtils.wrapTwiml {
            Gather(numDigits:10) {
                Say(loop: 20, directions)
            }
            Say(goodbye)
            Hangup()
        }
    }

    // CallResponse.SELF_CONNECTING
    static Result<Closure> selfConnecting(String displayedNumber, String numToCall) {
        if (!displayedNumber || !numToCall) {
            return TwilioUtils.invalidTwimlInputs(CallResponse.SELF_CONNECTING.toString())
        }
        String connecting = TwilioUtils.say("twimlBuilder.call.selfConnecting", [numToCall]),
            goodbye = IOCUtils.getMessage("twimlBuilder.call.goodbye")
        TwilioUtils.wrapTwiml {
            Say(connecting)
            Dial(callerId: displayedNumber) {
                Number(statusCallback: childCallStatus(numToCall), numToCall)
            }
            Say(goodbye)
            Hangup()
        }
    }

    // CallResponse.SELF_INVALID_DIGITS
    static Result<Closure> selfInvalid(String digits) {
        if (!digits) {
            return TwilioUtils.invalidTwimlInputs(CallResponse.SELF_INVALID_DIGITS.toString())
        }
        String error = TwilioUtils.say("twimlBuilder.call.selfInvalidDigits", [digits])
        TwilioUtils.wrapTwiml {
            Say(error)
            Redirect(IOCUtils.getWebhookLink()) // go back to selfGreeting
        }
    }

    // Incoming calls
    // --------------

    // CallResponse.CONNECT_INCOMING
    static Result<Closure> connectIncoming(BasePhoneNumber displayed, BasePhoneNumber originalFrom,
        Collection<? extends BasePhoneNumber> numsToCall) {

        if (!displayed?.validate() || !originalFrom?.validate() || !numsToCall) {
            return TwilioUtils.invalidTwimlInputs(CallResponse.CONNECT_INCOMING.toString())
        }
        String voicemailLink = IOCUtils.getHandleLink(CallResponse.CHECK_IF_VOICEMAIL),
            screenLink = IOCUtils.getWebhookLink(CallTwiml.infoForScreenIncoming(originalFrom))
        TwilioUtils.wrapTwiml {
            // have a short timeout here because we want to avoid having one
            // of the TextUp user's personal phone voicemails pick up and
            // take the voicemail instead of TextUp storing the voicemail
            Dial(callerId: displayed.e164PhoneNumber, timeout: 15, answerOnBridge: true,
                action: voicemailLink) {
                numsToCall.each { BasePhoneNumber bNum ->
                    Number(statusCallback: childCallStatus(bNum), url: screenLink,
                        bNum.e164PhoneNumber)
                }
            }
        }
    }

    static Map<String, String> infoForScreenIncoming(BasePhoneNumber bNum) {
        [
            (CallbackUtils.PARAM_HANDLE): CallResponse.SCREEN_INCOMING,
            (SCREEN_INCOMING_FROM): bNum.e164PhoneNumber
        ]
    }

    static Result<PhoneNumber> tryExtractScreenIncomingFrom(TypeMap params) {
        PhoneNumber.tryUrlDecode(params.string(SCREEN_INCOMING_FROM))
    }

    static Result<Closure> screenIncoming(Collection<String> idents) {
        if (!idents) {
            return TwilioUtils.invalidTwimlInputs(CallResponse.SCREEN_INCOMING.toString())
        }
        Collection<String> copiedIdents = new ArrayList<String>(idents)
        String identifier = CollectionUtils.joinWithDifferentLast(copiedIdents, ", ", " or "),
            directions = TwilioUtils.say("twimlBuilder.call.screenIncoming", [identifier]),
            goodbye = IOCUtils.getMessage("twimlBuilder.call.goodbye"),
            finishScreenWebhook = IOCUtils.getHandleLink(CallResponse.DO_NOTHING)
        TwilioUtils.wrapTwiml {
            Gather(numDigits: 1, action:finishScreenWebhook) {
                Pause(length: 1)
                Say(loop: 2, directions)
            }
            Say(goodbye)
            Hangup()
        }
    }

    // CallResponse.CHECK_IF_VOICEMAIL
    static Result<Closure> recordVoicemailMessage(Phone p1, BasePhoneNumber fromNum) {
        if (!p1 || !fromNum) {
            return TwilioUtils.invalidTwimlInputs(CallResponse.CHECK_IF_VOICEMAIL.toString())
        }
        String directions = IOCUtils.getMessage("twimlBuilder.call.voicemailDirections"),
            goodbye = IOCUtils.getMessage("twimlBuilder.call.goodbye"),
            // no-op for Record Twiml verb to call because recording might not be ready
            actionWebhook = IOCUtils.getHandleLink(CallResponse.END_CALL),
            // need to population From and To parameters to help in finding
            // phone and session in the recording status hook
            callbackWebhook = IOCUtils.getHandleLink(CallResponse.VOICEMAIL_DONE,
                [
                    (TwilioUtils.FROM): fromNum.e164PhoneNumber,
                    (TwilioUtils.TO): p1.number.e164PhoneNumber
                ])
        boolean shouldUseRecording = p1.useVoicemailRecordingIfPresent
        URL recordingUrl = p1.voicemailGreetingUrl
        TwilioUtils.wrapTwiml {
            Pause(length: 1)
            if (shouldUseRecording && recordingUrl) {
                Play(recordingUrl.toString())
            }
            else { Say(voice: p1.voice.toTwimlValue(), p1.buildAwayMessage()) }
            Say(directions)
            Record(action: actionWebhook, maxLength: 160, recordingStatusCallback: callbackWebhook)
            Say(goodbye)
            Hangup()
        }
    }

    // Outgoing call
    // -------------

    static Map<String, String> infoForFinishBridge(Long wrapperId) {
        [
            (CallbackUtils.PARAM_HANDLE): CallResponse.FINISH_BRIDGE,
            (FINISH_BRIDGE_WRAPPER_ID): wrapperId
        ]
    }

    static Result<Closure> finishBridge(TypeMap params) {
        IndividualPhoneRecordWrappers.mustFindActiveForId(params.long(FINISH_BRIDGE_WRAPPER_ID))
            .ifFail { TwilioUtils.invalidTwimlInputs(CallResponse.FINISH_BRIDGE.toString()) }
            .then { IndividualPhoneRecordWrapper w1 -> w1.tryGetSortedNumbers().curry(w1) }
            .then { IndividualPhoneRecordWrapper w1, List<ContactNumber> nums ->
                w1.tryGetSecureName().curry(w1, nums)
            }
            .then { IndividualPhoneRecordWrapper w1, List<ContactNumber> nums, String name ->
                w1.tryGetOriginalPhone().curry(nums, name)
            }
            .then { List<ContactNumber> nums, String name, Phone origPhone1 ->
                int lastIndex = nums.size() - 1
                TwilioUtils.wrapTwiml {
                    Pause(length: 3)
                    if (nums) {
                        nums.eachWithIndex { ContactNumber num, int index ->
                            String numForSay = TwilioUtils.say(num)
                            Say(IOCUtils.getMessage("twimlBuilder.call.bridgeNumberStart",
                                [numForSay, index + 1, lastIndex + 1]))
                            if (index != lastIndex) {
                                Say(IOCUtils.getMessage("twimlBuilder.call.bridgeNumberSkip"))
                            }
                            // (1) increase the timeout a bit to allow a longer window for the
                            // called party's voicemail to answer.
                            // (2) specify the callerId because, if shared, the bridge call is
                            // routed through the mutable phone and we want to present the
                            // original phone's number as the callerId to preserve a single
                            // point of contact for clients
                            Dial(timeout: 60,
                                hangupOnStar: true,
                                callerId: origPhone1.number.e164PhoneNumber) {
                                Number(statusCallback: childCallStatus(num.e164PhoneNumber),
                                    num.e164PhoneNumber)
                            }
                            Say(IOCUtils.getMessage("twimlBuilder.call.bridgeNumberFinish", [numForSay]))
                        }
                        Pause(length: 5)
                        Say(TwilioUtils.say("twimlBuilder.call.bridgeDone", [name]))
                    }
                    else {
                        Say(TwilioUtils.say("twimlBuilder.call.bridgeNoNumbers", [name]))
                    }
                    Hangup()
                }
            }
    }

    // Voicemail greeting
    // ------------------

    // CallResponse.VOICEMAIL_GREETING_RECORD
    static Map<String, String> infoForRecordVoicemailGreeting() {
        [(CallbackUtils.PARAM_HANDLE): CallResponse.VOICEMAIL_GREETING_RECORD]
    }

    static Result<Closure> recordVoicemailGreeting(BasePhoneNumber phoneNum, BasePhoneNumber sessNum) {
        if (!phoneNum || !sessNum) {
            return TwilioUtils.invalidTwimlInputs(CallResponse.VOICEMAIL_GREETING_RECORD.toString())
        }
        String directions = TwilioUtils.say("twimlBuilder.call.recordVoicemailGreeting", [phoneNum.number]),
            goodbye = IOCUtils.getMessage("twimlBuilder.call.goodbye"),
            processingLink = IOCUtils.getHandleLink(CallResponse.VOICEMAIL_GREETING_PROCESSING),
            doneLink = IOCUtils.getWebhookLink(CallTwiml
                .infoForVoicemailGreetingFinishedProcessing(phoneNum, sessNum))
        TwilioUtils.wrapTwiml {
            Pause(length: 1)
            Say(directions)
            Record(action: processingLink, maxLength: 180, recordingStatusCallback: doneLink)
            Say(goodbye)
            Hangup()
        }
    }

    // CallResponse.VOICEMAIL_GREETING_PROCESSING
    static Result<Closure> processingVoicemailGreeting() {
        String processingMessage = IOCUtils.getMessage("twimlBuilder.call.processingVoicemailGreeting")
        TwilioUtils.wrapTwiml {
            Say(loop: 2, processingMessage)
            Play(HOLD_MUSIC_URL)
            Say(loop: 2, processingMessage)
            Play(loop: 0, HOLD_MUSIC_URL)
        }
    }

    // CallResponse.VOICEMAIL_GREETING_PROCESSED
    static Map<String, String> infoForVoicemailGreetingFinishedProcessing(BasePhoneNumber phoneNum,
        BasePhoneNumber sessionNum) {

        [
            (CallbackUtils.PARAM_HANDLE): CallResponse.VOICEMAIL_GREETING_PROCESSED,
            (TwilioUtils.TO): sessionNum.number,
            (TwilioUtils.FROM): phoneNum.number
        ]
    }

    // CallResponse.VOICEMAIL_GREETING_PLAY
    static Map<String, String> infoForPlayVoicemailGreeting() {
        [(CallbackUtils.PARAM_HANDLE): CallResponse.VOICEMAIL_GREETING_PLAY]
    }

    static Result<Closure> playVoicemailGreeting(BasePhoneNumber fromNum, URL greetingLink) {
        if (!fromNum || !greetingLink) {
            return TwilioUtils.invalidTwimlInputs(CallResponse.VOICEMAIL_GREETING_PLAY.toString())
        }
        String goodbye = IOCUtils.getMessage("twimlBuilder.call.goodbye"),
            finishedMessage = TwilioUtils.say("twimlBuilder.call.finishedVoicemailGreeting",
                    [fromNum.number]),
            recordLink = IOCUtils.getWebhookLink(CallTwiml.infoForRecordVoicemailGreeting())
        TwilioUtils.wrapTwiml {
            Gather(numDigits: 1, action: recordLink) {
                Say(finishedMessage)
                Play(greetingLink.toString())
                Say(finishedMessage)
                Play(greetingLink.toString())
            }
            Say(goodbye)
            Hangup()
        }
    }

    // Announcements
    // -------------

    // CallResponse.ANNOUNCEMENT_GREETING
    static Result<Closure> announcementGreeting(String name, Boolean isSubscribed) {
        if (!name || isSubscribed == null) {
            return TwilioUtils.invalidTwimlInputs(CallResponse.ANNOUNCEMENT_GREETING.toString())
        }
        String welcome = IOCUtils.getMessage("twimlBuilder.call.announcementGreetingWelcome",
                [name, CallTwiml.DIGITS_HEAR_ANNOUNCEMENTS]),
            connectToStaff = IOCUtils.getMessage("twimlBuilder.call.connectToStaff"),
            sAction = isSubscribed
                ? IOCUtils.getMessage("twimlBuilder.call.announcementUnsubscribe", [CallTwiml.DIGITS_TOGGLE_SUBSCRIBE])
                : IOCUtils.getMessage("twimlBuilder.call.announcementSubscribe", [CallTwiml.DIGITS_TOGGLE_SUBSCRIBE])
        TwilioUtils.wrapTwiml {
            Gather(numDigits: 1) {
                Say(welcome)
                Say(sAction)
                Say(connectToStaff)
            }
            Redirect(IOCUtils.getWebhookLink())
        }
    }

    // CallResponse.HEAR_ANNOUNCEMENTS
    static Result<Closure> hearAnnouncements(Collection<FeaturedAnnouncement> announcements,
        Boolean isSubscribed) {

        if (!announcements || isSubscribed == null) {
            return TwilioUtils.invalidTwimlInputs(CallResponse.HEAR_ANNOUNCEMENTS.toString())
        }
        String toggleVal = CallTwiml.DIGITS_TOGGLE_SUBSCRIBE,
            sAction = isSubscribed ?
                IOCUtils.getMessage("twimlBuilder.call.announcementUnsubscribe", [toggleVal]) :
                IOCUtils.getMessage("twimlBuilder.call.announcementSubscribe", [toggleVal]),
            connectToStaff = IOCUtils.getMessage("twimlBuilder.call.connectToStaff")
        TwilioUtils.wrapTwiml {
            Gather(numDigits: 1) {
                TwilioUtils.formatAnnouncementsForRequest(announcements).each { Say(it) }
                Say(sAction)
                Say(connectToStaff)
            }
            Redirect(IOCUtils.getWebhookLink())
        }
    }

    static Map<String, String> infoForAnnouncementAndDigits(String identifier, String message) {
        [
            (CallbackUtils.PARAM_HANDLE): CallResponse.ANNOUNCEMENT_AND_DIGITS,
            (ANNOUNCEMENT_AND_DIGITS_IDENT): identifier,
            (ANNOUNCEMENT_AND_DIGITS_MSG): message
        ]
    }

    static Result<Closure> announcementAndDigits(TypeMap params) {
        String identifier = params.string(ANNOUNCEMENT_AND_DIGITS_IDENT),
            message = params.string(ANNOUNCEMENT_AND_DIGITS_MSG)
        if (!identifier || !message) {
            return TwilioUtils.invalidTwimlInputs(CallResponse.ANNOUNCEMENT_AND_DIGITS.toString())
        }
        String announcementIntro = IOCUtils.getMessage("twimlBuilder.call.announcementIntro",
                [identifier]),
            unsubscribe = IOCUtils.getMessage("twimlBuilder.call.announcementUnsubscribe",
                [CallTwiml.DIGITS_ANNOUNCEMENT_UNSUBSCRIBE]),
            // must have same handle or else from and to numbers are still
            // reversed and will result in a "no phone for that number" error
            repeatWebhook = IOCUtils.getWebhookLink(CallTwiml
                .infoForAnnouncementAndDigits(identifier, message))
        TwilioUtils.wrapTwiml {
            Say(announcementIntro)
            Gather(numDigits: 1) {
                Say(TwilioUtils.formatAnnouncementForRequest(DateTime.now(), identifier, message))
                Pause(length: 1)
                Say(unsubscribe)
            }
            Redirect(repeatWebhook)
        }
    }

    // CallResponse.UNSUBSCRIBED
    static Result<Closure> unsubscribed() {
        String unsubscribed = IOCUtils.getMessage("twimlBuilder.call.unsubscribed"),
            goodbye = IOCUtils.getMessage("twimlBuilder.call.goodbye")
        TwilioUtils.wrapTwiml {
            Say(unsubscribed)
            Say(goodbye)
            Hangup()
        }
    }

    // CallResponse.SUBSCRIBED
    static Result<Closure> subscribed() {
        String subscribed = IOCUtils.getMessage("twimlBuilder.call.subscribed"),
            goodbye = IOCUtils.getMessage("twimlBuilder.call.goodbye")
        TwilioUtils.wrapTwiml {
            Say(subscribed)
            Say(goodbye)
            Hangup()
        }
    }

    // Helpers
    // -------

    protected static String childCallStatus(String number) {
        IOCUtils.getHandleLink(CallbackUtils.STATUS,
            [(CallbackUtils.PARAM_CHILD_CALL_NUMBER): number])
    }
}
