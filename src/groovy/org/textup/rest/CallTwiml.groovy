package org.textup.rest

import org.joda.time.DateTime
import org.textup.*
import org.textup.type.*
import org.textup.validator.*

// Not type checked because of our use of Closures as a DSL for building Twiml.

class CallTwiml {

    // CallResponse.DIRECT_MESSAGE
    static Map<String, String> infoForDirectMessage(String token) {
        [handle: CallResponse.DIRECT_MESSAGE, token: token]
    }
    static Result<Closure> directMessage(String ident, String message, VoiceLanguage lang = null) {
        if (!ident || !message) {
            return TwilioUtils.invalidTwimlInputs(CallResponse.DIRECT_MESSAGE.toString())
        }
        String messageIntro = Helpers.getMessage("twimlBuilder.call.messageIntro", [ident])
        int maxRepeats = Constants.DIRECT_MESSAGE_MAX_REPEATS
        TwilioUtils.wrapTwiml {
            Say(messageIntro)
            Pause(length: 1)
            if (lang) {
                Say(language: lang.toTwimlValue(), loop: maxRepeats, message)
            }
            else { Say(loop: maxRepeats, message) }
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
        String invalidNumber = Helpers.getMessage("twimlBuilder.invalidNumber")
        TwilioUtils.wrapTwiml {
            Say(invalidNumber)
            Hangup()
        }
    }

    static Result<Closure> notFound() {
        String notFound = Helpers.getMessage("twimlBuilder.notFound")
        TwilioUtils.wrapTwiml {
            Say(notFound)
            Hangup()
        }
    }

    static Result<Closure> error() {
        String error = Helpers.getMessage("twimlBuilder.error")
        TwilioUtils.wrapTwiml {
            Say(error)
            Hangup()
        }
    }

    // Self calls
    // ----------

    // CallResponse.SELF_GREETING
    static Result<Closure> selfGreeting() {
        String directions = Helpers.getMessage("twimlBuilder.call.selfGreeting"),
            goodbye = Helpers.getMessage("twimlBuilder.call.goodbye")
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
            goodbye = Helpers.getMessage("twimlBuilder.call.goodbye")
        TwilioUtils.wrapTwiml {
            Say(connecting)
            Dial(callerId: displayedNumber) {
                Number(statusCallback: CallTwiml.childCallStatus(numToCall), numToCall)
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
            Redirect(Helpers.getWebhookLink()) // go back to selfGreeting
        }
    }

    // Incoming calls
    // --------------

    // CallResponse.CONNECT_INCOMING
    static Result<Closure> connectIncoming(String displayedNumber, String originalFrom,
        Collection<String> numsToCall) {

        if (!displayedNumber || !originalFrom || !numsToCall) {
            return TwilioUtils.invalidTwimlInputs(CallResponse.CONNECT_INCOMING.toString())
        }
        String voicemailLink = Helpers.getWebhookLink(handle: CallResponse.CHECK_IF_VOICEMAIL),
            screenLink = Helpers.getWebhookLink(CallTwiml.infoForScreenIncoming(originalFrom))
        TwilioUtils.wrapTwiml {
            // have a short timeout here because we want to avoid having one
            // of the TextUp user's personal phone voicemails pick up and
            // take the voicemail instead of TextUp storing the voicemail
            Dial(callerId: displayedNumber, timeout: 15, answerOnBridge: true,
                action: voicemailLink) {
                numsToCall.each { String num ->
                    Number(statusCallback: CallTwiml.childCallStatus(num), url: screenLink, num)
                }
            }
        }
    }

    // CallResponse.SCREEN_INCOMING
    static Map<String, String> infoForScreenIncoming(String originalFrom) {
        [handle: CallResponse.SCREEN_INCOMING, originalFrom: originalFrom]
    }
    static Result<Closure> screenIncoming(Collection<String> idents) {
        if (!idents) {
            return TwilioUtils.invalidTwimlInputs(CallResponse.SCREEN_INCOMING.toString())
        }
        Collection<String> copiedIdents = new ArrayList<String>(idents)
        String identifier = Helpers.joinWithDifferentLast(copiedIdents, ", ", " or "),
            directions = TwilioUtils.say("twimlBuilder.call.screenIncoming", [identifier]),
            goodbye = Helpers.getMessage("twimlBuilder.call.goodbye"),
            finishScreenWebhook = Helpers.getWebhookLink(handle: CallResponse.DO_NOTHING)
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
        String directions = Helpers.getMessage("twimlBuilder.call.voicemailDirections"),
            goodbye = Helpers.getMessage("twimlBuilder.call.goodbye"),
            shouldUseRecording = p1.useVoicemailRecordingIfPresent,
            // no-op for Record Twiml verb to call because recording might not be ready
            actionWebhook = Helpers.getWebhookLink(handle: CallResponse.END_CALL),
            // need to population From and To parameters to help in finding
            // phone and session in the recording status hook
            callbackWebhook = Helpers.getWebhookLink(handle: CallResponse.VOICEMAIL_DONE,
                From: fromNum.e164PhoneNumber, To: p1.number.e164PhoneNumber)
        URL recordingUrl = p1.voicemailGreetingUrl
        TwilioUtils.wrapTwiml {
            Pause(length: 1)
            if (shouldUseRecording && recordingUrl) {
                Play(recordingUrl.toString())
            }
            else { Say(voice: p1.voice.toTwimlValue(), p1.awayMessage) }
            Say(directions)
            Record(action: actionWebhook, maxLength: 160, recordingStatusCallback: callbackWebhook)
            Say(goodbye)
            Hangup()
        }
    }

    // Outgoing call
    // -------------

    // CallResponse.FINISH_BRIDGE
    static Map<String, String> infoForFinishBridge(Contactable cont1) {
        [contactId: cont1.contactId, handle: CallResponse.FINISH_BRIDGE]
    }
    static Result<Closure> finishBridge(Contact c1) {
        if (!c1) {
            return TwilioUtils.invalidTwimlInputs(CallResponse.FINISH_BRIDGE.toString())
        }
        List<ContactNumber> nums = c1.sortedNumbers ?: []
        int lastIndex = nums.size() - 1
        String nameOrNum = c1.getNameOrNumber()
        TwilioUtils.wrapTwiml {
            Pause(length: 1)
            if (nums) {
                nums.eachWithIndex { ContactNumber num, int index ->
                    String numForSay = TwilioUtils.say(num)
                    Say(Helpers.getMessage("twimlBuilder.call.bridgeNumberStart",
                        [numForSay, index + 1, lastIndex + 1]))
                    if (index != lastIndex) {
                        Say(Helpers.getMessage("twimlBuilder.call.bridgeNumberSkip"))
                    }
                    // increase the timeout a bit to allow a longer window
                    // for the called party's voicemail to answer
                    Dial(timeout: 60, hangupOnStar: true) {
                        Number(statusCallback: CallTwiml.childCallStatus(num.e164PhoneNumber),
                            num.e164PhoneNumber)
                    }
                    Say(Helpers.getMessage("twimlBuilder.call.bridgeNumberFinish", [numForSay]))
                }
                Pause(length: 5)
                Say(TwilioUtils.say("twimlBuilder.call.bridgeDone", [nameOrNum]))
            }
            else {
                Say(TwilioUtils.say("twimlBuilder.call.bridgeNoNumbers", [nameOrNum]))
            }
            Hangup()
        }
    }

    // Voicemail greeting
    // ------------------

    // CallResponse.VOICEMAIL_GREETING_RECORD
    static Map<String, String> infoForRecordVoicemailGreeting() {
        [handle: CallResponse.VOICEMAIL_GREETING_RECORD]
    }
    static Result<Closure> recordVoicemailGreeting(BasePhoneNumber fromNum) {
        if (!fromNum) {
            return TwilioUtils.invalidTwimlInputs(CallResponse.VOICEMAIL_GREETING_RECORD.toString())
        }
        String directions = TwilioUtils.say("twimlBuilder.call.recordVoicemailGreeting", [fromNum.number]),
            goodbye = Helpers.getMessage("twimlBuilder.call.goodbye"),
            processingLink = Helpers.getWebhookLink(handle: CallResponse.VOICEMAIL_GREETING_PROCESSING),
            doneLink = Helpers.getWebhookLink(CallTwiml.infoForVoicemailGreetingFinishedProcessing())
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
        String processingMessage = Helpers.getMessage("twimlBuilder.call.processingVoicemailGreeting")
        TwilioUtils.wrapTwiml {
            Say(loop: 2, processingMessage)
            Play(Constants.CALL_HOLD_MUSIC_URL)
            Say(loop: 2, processingMessage)
            Play(loop: 0, Constants.CALL_HOLD_MUSIC_URL)
        }
    }

    // CallResponse.VOICEMAIL_GREETING_PROCESSED
    static Map<String, String> infoForVoicemailGreetingFinishedProcessing() {
        [handle: CallResponse.VOICEMAIL_GREETING_PROCESSED]
    }

    // CallResponse.VOICEMAIL_GREETING_PLAY
    static Map<String, String> infoForPlayVoicemailGreeting() {
        [handle: CallResponse.VOICEMAIL_GREETING_PLAY]
    }
    static Result<Closure> playVoicemailGreeting(BasePhoneNumber fromNum, URL greetingLink) {
        if (!fromNum || !greetingLink) {
            return TwilioUtils.invalidTwimlInputs(CallResponse.VOICEMAIL_GREETING_PLAY.toString())
        }
        String goodbye = Helpers.getMessage("twimlBuilder.call.goodbye"),
            finishedMessage = TwilioUtils.say("twimlBuilder.call.finishedVoicemailGreeting",
                    [fromNum.number]),
            recordLink = Helpers.getWebhookLink(CallTwiml.infoForRecordVoicemailGreeting())
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
        String welcome = Helpers.getMessage("twimlBuilder.call.announcementGreetingWelcome",
                [name, Constants.CALL_HEAR_ANNOUNCEMENTS]),
            connectToStaff = Helpers.getMessage("twimlBuilder.call.connectToStaff"),
            sAction = isSubscribed
                ? Helpers.getMessage("twimlBuilder.call.announcementUnsubscribe", [Constants.CALL_TOGGLE_SUBSCRIBE])
                : Helpers.getMessage("twimlBuilder.call.announcementSubscribe", [Constants.CALL_TOGGLE_SUBSCRIBE])
        TwilioUtils.wrapTwiml {
            Gather(numDigits: 1) {
                Say(welcome)
                Say(sAction)
                Say(connectToStaff)
            }
            Redirect(Helpers.getWebhookLink())
        }
    }

    // CallResponse.HEAR_ANNOUNCEMENTS
    static Result<Closure> hearAnnouncements(Collection<FeaturedAnnouncement> announcements,
        Boolean isSubscribed) {

        if (!announcements || isSubscribed == null) {
            return TwilioUtils.invalidTwimlInputs(CallResponse.HEAR_ANNOUNCEMENTS.toString())
        }
        String sAction = isSubscribed
                ? Helpers.getMessage("twimlBuilder.call.announcementUnsubscribe", [Constants.CALL_TOGGLE_SUBSCRIBE])
                : Helpers.getMessage("twimlBuilder.call.announcementSubscribe", [Constants.CALL_TOGGLE_SUBSCRIBE]),
            connectToStaff = Helpers.getMessage("twimlBuilder.call.connectToStaff")
        TwilioUtils.wrapTwiml {
            Gather(numDigits: 1) {
                TwilioUtils.formatAnnouncementsForRequest(announcements).each { Say(it) }
                Say(sAction)
                Say(connectToStaff)
            }
            Redirect(Helpers.getWebhookLink())
        }
    }

    // CallResponse.ANNOUNCEMENT_AND_DIGITS
    static Map<String, String> infoForAnnouncementAndDigits(String identifier, String message) {
        [handle: CallResponse.ANNOUNCEMENT_AND_DIGITS, identifier: identifier, message: message]
    }
    static Result<Closure> announcementAndDigits(String identifier, String message) {
        if (!identifier || !message) {
            return TwilioUtils.invalidTwimlInputs(CallResponse.ANNOUNCEMENT_AND_DIGITS.toString())
        }
        String announcementIntro = Helpers.getMessage("twimlBuilder.call.announcementIntro",
                [identifier]),
            unsubscribe = Helpers.getMessage("twimlBuilder.call.announcementUnsubscribe",
                [Constants.CALL_ANNOUNCEMENT_UNSUBSCRIBE]),
            // must have same handle or else from and to numbers are still
            // reversed and will result in a "no phone for that number" error
            repeatWebhook = Helpers.getWebhookLink(handle: CallResponse.ANNOUNCEMENT_AND_DIGITS,
                identifier: identifier, message: message)
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
        String unsubscribed = Helpers.getMessage("twimlBuilder.call.unsubscribed"),
            goodbye = Helpers.getMessage("twimlBuilder.call.goodbye")
        TwilioUtils.wrapTwiml {
            Say(unsubscribed)
            Say(goodbye)
            Hangup()
        }
    }

    // CallResponse.SUBSCRIBED
    static Result<Closure> subscribed() {
        String subscribed = Helpers.getMessage("twimlBuilder.call.subscribed"),
            goodbye = Helpers.getMessage("twimlBuilder.call.goodbye")
        TwilioUtils.wrapTwiml {
            Say(subscribed)
            Say(goodbye)
            Hangup()
        }
    }

    // Helpers
    // -------

    protected static String childCallStatus(String number) {
        Helpers.getWebhookLink([
            handle: Constants.CALLBACK_STATUS,
            (Constants.CALLBACK_CHILD_CALL_NUMBER_KEY): number
        ])
    }
}
