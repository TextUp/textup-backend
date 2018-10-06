package org.textup.rest

import grails.compiler.GrailsTypeChecked
import groovy.transform.TypeCheckingMode
import org.codehaus.groovy.grails.web.mapping.LinkGenerator
import org.joda.time.DateTime
import org.ocpsoft.prettytime.PrettyTime
import org.springframework.context.i18n.LocaleContextHolder as LCH
import org.textup.*
import org.textup.type.CallResponse
import org.textup.type.TextResponse
import org.textup.type.VoiceLanguage
import org.textup.type.VoiceType

class TwimlBuilder {

    // manually wired by reference in `resources.groovy` to enable mocking during testing
    LinkGenerator linkGenerator
    ResultFactory resultFactory
    TokenService tokenService

    // Errors
    // ------

    Result<Closure> invalidNumberForText() {
        String invalidNumber = Helpers.getMessage("twimlBuilder.invalidNumber")
        resultFactory.success({
            Response { Message(invalidNumber) }
        })
    }
    Result<Closure> invalidNumberForCall() {
        String invalidNumber = Helpers.getMessage("twimlBuilder.invalidNumber")
        resultFactory.success({
            Response {
                Say(invalidNumber)
                Hangup()
            }
        })
    }

    Result<Closure> notFoundForText() {
        String notFound = Helpers.getMessage("twimlBuilder.notFound")
        resultFactory.success({
            Response { Message(notFound) }
        })
    }
    Result<Closure> notFoundForCall() {
        String notFound = Helpers.getMessage("twimlBuilder.notFound")
        resultFactory.success({
            Response {
                Say(notFound)
                Hangup()
            }
        })
    }
    Result<Closure> errorForText() {
        String error = Helpers.getMessage("twimlBuilder.error")
        resultFactory.success({
            Response { Message(error) }
        })
    }
    Result<Closure> errorForCall() {
        String error = Helpers.getMessage("twimlBuilder.error")
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
    Result<Closure> buildTexts(Collection<String> responses) {
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

    @GrailsTypeChecked
    protected String getLink(Map linkParams=[:]) {
        linkGenerator.link(namespace:"v1", resource:"publicRecord", action:"save",
            params:linkParams, absolute:true)
    }
    @GrailsTypeChecked
    protected String buildChildCallStatus(String number) {
        getLink([
            handle:Constants.CALLBACK_STATUS,
            (Constants.CALLBACK_CHILD_CALL_NUMBER_KEY): number
        ])
    }
    @GrailsTypeChecked
    protected List<String> formatAnnouncements(List<FeaturedAnnouncement> announces) {
        if (announces) {
            announces.collect { FeaturedAnnouncement announce ->
                formatAnnouncement(announce.whenCreated, announce.owner.name,
                    announce.message)
            }
        }
        else { [Helpers.getMessage("twimlBuilder.noAnnouncements")] }
    }
    @GrailsTypeChecked
    protected String formatAnnouncement(DateTime dt, String identifier, String msg) {
        String timeAgo = new PrettyTime(LCH.getLocale()).format(dt.toDate())
        Helpers.getMessage("twimlBuilder.announcement", [timeAgo, identifier, msg])
    }

    // Translate responses
    // -------------------

    Result<List<String>> translate(TextResponse code, Map params=[:]) {
        List<String> responses = []
        switch (code) {
            case TextResponse.INSTRUCTIONS_UNSUBSCRIBED:
                responses << Helpers.getMessage("twimlBuilder.text.instructionsUnsubscribed",
                    [Constants.TEXT_SEE_ANNOUNCEMENTS, Constants.TEXT_TOGGLE_SUBSCRIBE])
                break
            case TextResponse.INSTRUCTIONS_SUBSCRIBED:
                responses << Helpers.getMessage("twimlBuilder.text.instructionsSubscribed",
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
                    String unsubscribe = Helpers.getMessage("twimlBuilder.text.announcementUnsubscribe",
                        [Constants.TEXT_TOGGLE_SUBSCRIBE])
                    responses << "${params.identifier}: ${params.message}. $unsubscribe"
                }
                break
            case TextResponse.SUBSCRIBED:
                responses << Helpers.getMessage("twimlBuilder.text.subscribed",
                    [Constants.TEXT_TOGGLE_SUBSCRIBE])
                break
            case TextResponse.UNSUBSCRIBED:
                responses << Helpers.getMessage("twimlBuilder.text.unsubscribed",
                    [Constants.TEXT_TOGGLE_SUBSCRIBE])
                break
            case TextResponse.BLOCKED:
                responses << Helpers.getMessage("twimlBuilder.text.blocked")
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
                String directions = Helpers.getMessage("twimlBuilder.call.selfGreeting")
                callBody = {
                    Gather(numDigits:10) { Say(directions) }
                    Redirect(getLink())
                }
                break
            case CallResponse.SELF_CONNECTING:
                if (params.displayedNumber instanceof String &&
                    params.numAsString instanceof String) {
                    String connecting = Helpers.getMessage("twimlBuilder.call.selfConnecting",
                            [Helpers.formatNumberForSay(params.numAsString)]),
                        goodbye = Helpers.getMessage("twimlBuilder.call.goodbye")
                    callBody = {
                        Say(connecting)
                        Dial(callerId:params.displayedNumber) {
                            Number(statusCallback: buildChildCallStatus(params.numAsString),
                                params.numAsString)
                        }
                        Say(goodbye)
                        Hangup()
                    }
                }
                break
            case CallResponse.SELF_INVALID_DIGITS:
                if (params.digits instanceof String) {
                    String error = Helpers.getMessage("twimlBuilder.call.selfInvalidDigits",
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
                                Number(statusCallback: buildChildCallStatus(num),
                                    url:getLink(params.screenParams), num)
                            }
                        }
                    }
                }
                break
            case CallResponse.SCREEN_INCOMING:
                if (params.callerId instanceof String &&
                    params.linkParams instanceof Map) {
                    String goodbye = Helpers.getMessage("twimlBuilder.call.goodbye"),
                        finishScreenWebhook = getLink(params.linkParams),
                        directions = Helpers.getMessage("twimlBuilder.call.screenIncoming",
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
                    String directions = Helpers.getMessage("twimlBuilder.call.voicemailDirections"),
                        goodbye = Helpers.getMessage("twimlBuilder.call.goodbye"),
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
                                Say(Helpers.getMessage("twimlBuilder.call.bridgeNumberStart",
                                    [numForSay, index + 1, lastIndex + 1]))
                                if (index != lastIndex) {
                                    Say(Helpers.getMessage("twimlBuilder.call.bridgeNumberSkip"))
                                }
                                // increase the timeout a bit to allow a longer window
                                // for the called party's voicemail to answer
                                Dial(timeout:"60", hangupOnStar:"true") {
                                    Number(statusCallback: buildChildCallStatus(num.e164PhoneNumber),
                                        num.e164PhoneNumber)
                                }
                                Say(Helpers.getMessage("twimlBuilder.call.bridgeNumberFinish", [numForSay]))
                            }
                            Pause(length:"5")
                            Say(Helpers.getMessage("twimlBuilder.call.bridgeDone", [nameOrNum]))
                        }
                        else {
                            Say(Helpers.getMessage("twimlBuilder.call.bridgeNoNumbers", [nameOrNum]))
                        }
                        Hangup()
                    }
                }
                break
            case CallResponse.ANNOUNCEMENT_GREETING:
                if (params.name instanceof String && params.isSubscribed != null) {
                    String welcome = Helpers.getMessage("twimlBuilder.call.announcementGreetingWelcome",
                            [params.name, Constants.CALL_HEAR_ANNOUNCEMENTS]),
                        sAction = params.isSubscribed ?
                            Helpers.getMessage("twimlBuilder.call.announcementUnsubscribe",
                                [Constants.CALL_TOGGLE_SUBSCRIBE]) :
                            Helpers.getMessage("twimlBuilder.call.announcementSubscribe",
                                [Constants.CALL_TOGGLE_SUBSCRIBE]),
                        connectToStaff = Helpers.getMessage("twimlBuilder.call.connectToStaff")
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
                            Helpers.getMessage("twimlBuilder.call.announcementUnsubscribe",
                                [Constants.CALL_TOGGLE_SUBSCRIBE]) :
                            Helpers.getMessage("twimlBuilder.call.announcementSubscribe",
                                [Constants.CALL_TOGGLE_SUBSCRIBE]),
                        connectToStaff = Helpers.getMessage("twimlBuilder.call.connectToStaff")
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
                    String announcementIntro = Helpers.getMessage("twimlBuilder.call.announcementIntro",
                            [params.identifier]),
                        unsubscribe = Helpers.getMessage("twimlBuilder.call.announcementUnsubscribe",
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
                callBody = tokenService.buildCallDirectMessageBody(this.&getLink,
                    Helpers.to(String, params.token),
                    Helpers.to(Integer, params.repeatCount))
                break
            case CallResponse.UNSUBSCRIBED:
                String unsubscribed = Helpers.getMessage("twimlBuilder.call.unsubscribed"),
                    goodbye = Helpers.getMessage("twimlBuilder.call.goodbye")
                callBody = {
                    Say(unsubscribed)
                    Say(goodbye)
                    Hangup()
                }
                break
            case CallResponse.SUBSCRIBED:
                String subscribed = Helpers.getMessage("twimlBuilder.call.subscribed"),
                    goodbye = Helpers.getMessage("twimlBuilder.call.goodbye")
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
