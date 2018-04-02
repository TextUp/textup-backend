package org.textup.rest

import grails.compiler.GrailsCompileStatic
import groovy.transform.TypeCheckingMode
import org.codehaus.groovy.grails.web.mapping.LinkGenerator
import org.joda.time.DateTime
import org.ocpsoft.prettytime.PrettyTime
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.i18n.LocaleContextHolder as LCH
import org.springframework.context.MessageSource
import org.textup.*
import org.textup.type.CallResponse
import org.textup.type.TextResponse
import org.textup.type.VoiceLanguage
import org.textup.type.VoiceType

class TwimlBuilder {

    @Autowired
    LinkGenerator linkGenerator
    @Autowired
    MessageSource messageSource
    @Autowired
    ResultFactory resultFactory

    // Errors
    // ------

    Result<Closure> invalidNumberForText() {
        String invalidNumber = getMessage("twimlBuilder.invalidNumber")
        resultFactory.success({
            Response { Message(invalidNumber) }
        })
    }
    Result<Closure> invalidNumberForCall() {
        String invalidNumber = getMessage("twimlBuilder.invalidNumber")
        resultFactory.success({
            Response {
                Say(invalidNumber)
                Hangup()
            }
        })
    }

    Result<Closure> notFoundForText() {
        String notFound = getMessage("twimlBuilder.notFound")
        resultFactory.success({
            Response { Message(notFound) }
        })
    }
    Result<Closure> notFoundForCall() {
        String notFound = getMessage("twimlBuilder.notFound")
        resultFactory.success({
            Response {
                Say(notFound)
                Hangup()
            }
        })
    }
    Result<Closure> errorForText() {
        String error = getMessage("twimlBuilder.error")
        resultFactory.success({
            Response { Message(error) }
        })
    }
    Result<Closure> errorForCall() {
        String error = getMessage("twimlBuilder.error")
        resultFactory.success({
            Response {
                Say(error)
                Hangup()
            }
        })
    }
    Result<Closure> noResponse() {
        resultFactory.success({ Response {} })
    }

    // Texts
    // -----

    Result<Closure> build(TextResponse code, Map params=[:]) {
        translate(code, params).then({ List<String> responses ->
            buildTexts(responses)
        })
    }
    Result<Closure> buildTexts(List<String> responses) {
        resultFactory.success({
            Response { responses.each { Message(it) } }
        })
    }

    // Calls
    // -----

    Result<Closure> build(CallResponse code, Map params=[:]) {
        translate(code, params).then({ Closure callBody ->
            resultFactory.success({
                Response {
                    callBody.delegate = delegate
                    callBody()
                }
            })
        })
    }

    // Utility methods
    // ---------------

    @GrailsCompileStatic
    protected String getMessage(String code, Collection<String> args=[]) {
        messageSource.getMessage(code, args as Object[], LCH.getLocale())
    }
    @GrailsCompileStatic
    protected String getLink(Map linkParams=[:]) {
        linkGenerator.link(namespace:"v1", resource:"publicRecord", action:"save",
            params:linkParams, absolute:true)
    }
    @GrailsCompileStatic
    protected List<String> formatAnnouncements(List<FeaturedAnnouncement> announces) {
        if (announces) {
            announces.collect { FeaturedAnnouncement announce ->
                formatAnnouncement(announce.whenCreated, announce.owner.name,
                    announce.message)
            }
        }
        else { [getMessage("twimlBuilder.noAnnouncements")] }
    }
    @GrailsCompileStatic
    protected String formatAnnouncement(DateTime dt, String identifier, String msg) {
        String timeAgo = new PrettyTime(LCH.getLocale()).format(dt.toDate())
        getMessage("twimlBuilder.announcement", [timeAgo, identifier, msg])
    }

    // Translate responses
    // -------------------

    Result<List<String>> translate(TextResponse code, Map params=[:]) {
        List<String> responses = []
        switch (code) {
            case TextResponse.INSTRUCTIONS_UNSUBSCRIBED:
                responses << getMessage("twimlBuilder.text.instructionsUnsubscribed",
                    [Constants.TEXT_SEE_ANNOUNCEMENTS, Constants.TEXT_TOGGLE_SUBSCRIBE])
                break
            case TextResponse.INSTRUCTIONS_SUBSCRIBED:
                responses << getMessage("twimlBuilder.text.instructionsSubscribed",
                    [Constants.TEXT_SEE_ANNOUNCEMENTS, Constants.TEXT_TOGGLE_SUBSCRIBE])
                break
            case TextResponse.SEE_ANNOUNCEMENTS:
                if (params.announcements instanceof List) {
                    responses += this.formatAnnouncements(params.announcements)
                }
                break
            case TextResponse.ANNOUNCEMENT:
                if (params.identifier instanceof String &&
                    params.message instanceof String) {
                    String unsubscribe = getMessage("twimlBuilder.text.announcementUnsubscribe",
                        [Constants.TEXT_TOGGLE_SUBSCRIBE])
                    responses << "${params.identifier}: ${params.message}. $unsubscribe"
                }
                break
            case TextResponse.SUBSCRIBED:
                responses << getMessage("twimlBuilder.text.subscribed",
                    [Constants.TEXT_TOGGLE_SUBSCRIBE])
                break
            case TextResponse.UNSUBSCRIBED:
                responses << getMessage("twimlBuilder.text.unsubscribed",
                    [Constants.TEXT_TOGGLE_SUBSCRIBE])
                break
            case TextResponse.BLOCKED:
                responses << getMessage("twimlBuilder.text.blocked")
                break
        }
        if (responses) {
            resultFactory.success(responses)
        }
        else {
            resultFactory.failWithCodeAndStatus('twimlBuilder.invalidCode',
                ResultStatus.BAD_REQUEST, [code])
        }
    }
    Result<Closure> translate(CallResponse code, Map params=[:]) {
        Closure callBody
        switch (code) {
            case CallResponse.SELF_GREETING:
                String directions = getMessage("twimlBuilder.call.selfGreeting")
                callBody = {
                    Gather(numDigits:10) { Say(directions) }
                    Redirect(getLink())
                }
                break
            case CallResponse.SELF_CONNECTING:
                if (params.displayedNumber instanceof String &&
                    params.numAsString instanceof String) {
                    String connecting = getMessage("twimlBuilder.call.selfConnecting",
                            [Helpers.formatNumberForSay(params.numAsString)]),
                        goodbye = getMessage("twimlBuilder.call.goodbye")
                    callBody = {
                        Say(connecting)
                        Dial(callerId:params.displayedNumber) { Number(params.numAsString) }
                        Say(goodbye)
                        Hangup()
                    }
                }
                break
            case CallResponse.SELF_INVALID_DIGITS:
                if (params.digits instanceof String) {
                    String error = getMessage("twimlBuilder.call.selfInvalidDigits",
                        [Helpers.formatNumberForSay(params.digits)])
                    callBody = {
                        Say(error)
                        Redirect(getLink())
                    }
                }
                break
            case CallResponse.CONNECT_INCOMING:
                if (params.displayedNumber instanceof String &&
                    params.numsToCall instanceof Collection &&
                    params.linkParams instanceof Map &&
                    params.screenParams instanceof Map) {
                    String checkVoicemailWebhook = getLink(params.linkParams)
                    callBody = {
                        // have a short timeout here because we want to avoid having one
                        // of the TextUp user's personal phone voicemails pick up and
                        // take the voicemail instead of TextUp storing the voicemail
                        Dial(callerId:params.displayedNumber, timeout:"15", answerOnBridge:true,
                            action:checkVoicemailWebhook) {
                            for (num in params.numsToCall) {
                                Number(url:getLink(params.screenParams), num)
                            }
                        }
                    }
                }
                break
            case CallResponse.SCREEN_INCOMING:
                if (params.callerId instanceof String &&
                    params.linkParams instanceof Map) {
                    String goodbye = getMessage("twimlBuilder.call.goodbye"),
                        finishScreenWebhook = getLink(params.linkParams),
                        directions = getMessage("twimlBuilder.call.screenIncoming",
                            [Helpers.formatForSayIfPhoneNumber(params.callerId)])
                    callBody = {
                        Gather(numDigits:"1", action:finishScreenWebhook) {
                            Pause(length:"1")
                            // say twice just to make sure the user hears the message
                            Say(directions)
                            Say(directions)
                        }
                        Say(goodbye)
                        Hangup()
                    }
                }
                break
            case CallResponse.CHECK_IF_VOICEMAIL:
                if (params.awayMessage instanceof String &&
                    params.linkParams instanceof Map &&
                    params.callbackParams instanceof Map &&
                    params.voice instanceof VoiceType) {
                    String directions = getMessage("twimlBuilder.call.voicemailDirections"),
                        goodbye = getMessage("twimlBuilder.call.goodbye"),
                        actionWebhook = getLink(params.linkParams),
                        callbackWebhook = getLink(params.callbackParams),
                        twimlVoice = params.voice.toTwimlValue()
                    callBody = {
                        Pause(length:"1")
                        Say(voice:twimlVoice, params.awayMessage)
                        Say(directions)
                        Record(action:actionWebhook, maxLength:160,
                            recordingStatusCallback:callbackWebhook)
                        Say(goodbye)
                        Hangup()
                    }
                }
                break
            case CallResponse.VOICEMAIL_DONE:
                callBody = {}
                break
            case CallResponse.FINISH_BRIDGE:
                if (params.contact instanceof Contact) {
                    Contact c1 = params.contact
                    List<ContactNumber> nums = c1.sortedNumbers ?: []
                    int lastIndex = nums.size() - 1
                    String nameOrNum = c1.getNameOrNumber(true)
                    callBody = {
                        Pause(length:"1")
                        if (nums) {
                            nums.eachWithIndex { ContactNumber num, int index ->
                                String numForSay = Helpers.formatNumberForSay(num.number)
                                Say(getMessage("twimlBuilder.call.bridgeNumberStart",
                                    [numForSay, index + 1, lastIndex + 1]))
                                if (index != lastIndex) {
                                    Say(getMessage("twimlBuilder.call.bridgeNumberSkip"))
                                }
                                // increase the timeout a bit to allow a longer window
                                // for the called party's voicemail to answer
                                Dial(timeout:"60", hangupOnStar:"true") {
                                    Number(num.e164PhoneNumber)
                                }
                                Say(getMessage("twimlBuilder.call.bridgeNumberFinish", [numForSay]))
                            }
                            Pause(length:"5")
                            Say(getMessage("twimlBuilder.call.bridgeDone", [nameOrNum]))
                        }
                        else {
                            Say(getMessage("twimlBuilder.call.bridgeNoNumbers", [nameOrNum]))
                        }
                        Hangup()
                    }
                }
                break
            case CallResponse.ANNOUNCEMENT_GREETING:
                if (params.name instanceof String && params.isSubscribed != null) {
                    String welcome = getMessage("twimlBuilder.call.announcementGreetingWelcome",
                            [params.name, Constants.CALL_HEAR_ANNOUNCEMENTS]),
                        sAction = params.isSubscribed ?
                            getMessage("twimlBuilder.call.announcementUnsubscribe",
                                [Constants.CALL_TOGGLE_SUBSCRIBE]) :
                            getMessage("twimlBuilder.call.announcementSubscribe",
                                [Constants.CALL_TOGGLE_SUBSCRIBE]),
                        connectToStaff = getMessage("twimlBuilder.call.connectToStaff")
                    callBody = {
                        Gather(numDigits:1) {
                            Say(welcome)
                            Say(sAction)
                            Say(connectToStaff)
                        }
                        Redirect(getLink())
                    }
                }
                break
            case CallResponse.HEAR_ANNOUNCEMENTS:
                if (params.announcements instanceof Collection &&
                        params.isSubscribed != null) {
                    String sAction = params.isSubscribed ?
                            getMessage("twimlBuilder.call.announcementUnsubscribe",
                                [Constants.CALL_TOGGLE_SUBSCRIBE]) :
                            getMessage("twimlBuilder.call.announcementSubscribe",
                                [Constants.CALL_TOGGLE_SUBSCRIBE]),
                        connectToStaff = getMessage("twimlBuilder.call.connectToStaff")
                    callBody = {
                        Gather(numDigits:1) {
                            formatAnnouncements(params.announcements)
                                .each { Say(it) }
                            Say(sAction)
                            Say(connectToStaff)
                        }
                        Redirect(getLink())
                    }
                }
                break
            case CallResponse.ANNOUNCEMENT_AND_DIGITS:
                if (params.message instanceof String &&
                        params.identifier instanceof String) {
                    String announcementIntro = getMessage("twimlBuilder.call.announcementIntro",
                            [params.identifier]),
                        unsubscribe = getMessage("twimlBuilder.call.announcementUnsubscribe",
                            [Constants.CALL_ANNOUNCEMENT_UNSUBSCRIBE]),
                        // must have same handle or else from and to numbers are still
                        // reversed and will result in a "no phone for that number" error
                        repeatWebhook = getLink(handle:CallResponse.ANNOUNCEMENT_AND_DIGITS,
                            identifier:params.identifier, message:params.message)
                    callBody = {
                        Say(announcementIntro)
                        Gather(numDigits:1) {
                            Say(formatAnnouncement(DateTime.now(), params.identifier,
                                params.message))
                            Pause(length:"1")
                            Say(unsubscribe)
                        }
                        Redirect(repeatWebhook)
                    }
                }
                break
            case CallResponse.DIRECT_MESSAGE:
                // cannot have language be of type VoiceLanguage because this hook is called
                // after the the TextUp user picks up the call and we must serialize the
                // parameters that are then passed back to TextUp by Twilio after pickup
                if (params.message instanceof String && params.language instanceof String) {
                    VoiceLanguage lang = Helpers.convertEnum(VoiceLanguage, params.language)
                    int repeatCount = Helpers.to(Integer, params.repeatCount) ?: 0
                    Map linkParams = [handle:CallResponse.DIRECT_MESSAGE,
                        message:params.message, repeatCount:repeatCount + 1]
                    if (params.identifier) {
                        linkParams.identifier = params.identifier
                    }

                    if (repeatCount < Constants.MAX_REPEATS) {
                        String ident = params.identifier ?
                                Helpers.to(String, params.identifier) : null,
                            messageIntro = ident ?
                                getMessage("twimlBuilder.call.messageIntro", [ident]) :
                                getMessage("twimlBuilder.call.anonymousMessageIntro"),
                            repeatWebhook = getLink(linkParams)
                        callBody = {
                            Say(messageIntro)
                            Pause(length:"1")
                            if (lang) {
                                Say(language:lang, params.message)
                            }
                            else { Say(params.message) }
                            Redirect(repeatWebhook)
                        }
                    }
                    else { callBody = { Hangup() } }
                }
                break
            case CallResponse.UNSUBSCRIBED:
                String unsubscribed = getMessage("twimlBuilder.call.unsubscribed"),
                    goodbye = getMessage("twimlBuilder.call.goodbye")
                callBody = {
                    Say(unsubscribed)
                    Say(goodbye)
                    Hangup()
                }
                break
            case CallResponse.SUBSCRIBED:
                String subscribed = getMessage("twimlBuilder.call.subscribed"),
                    goodbye = getMessage("twimlBuilder.call.goodbye")
                callBody = {
                    Say(subscribed)
                    Say(goodbye)
                    Hangup()
                }
                break
            case CallResponse.END_CALL:
                callBody = { Hangup() }
                break
            case CallResponse.DO_NOTHING:
                callBody = {}
                break
            case CallResponse.BLOCKED:
                callBody = {
                    Reject(reason:"rejected")
                }
                break
        }
        if (callBody) {
            resultFactory.success(callBody)
        }
        else {
            resultFactory.failWithCodeAndStatus('twimlBuilder.invalidCode',
                ResultStatus.BAD_REQUEST, [code])
        }
    }
}
