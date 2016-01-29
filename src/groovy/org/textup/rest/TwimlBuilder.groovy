package org.textup.rest

import org.codehaus.groovy.grails.web.mapping.LinkGenerator
import org.joda.time.DateTime
import org.ocpsoft.prettytime.PrettyTime
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.i18n.LocaleContextHolder as LCH
import org.springframework.context.MessageSource
import org.textup.*
import static org.springframework.http.HttpStatus.*

class TwimlBuilder {

    @Autowired
    LinkGenerator linkGenerator
    @Autowired
    MessageSource messageSource
    @Autowired
    ResultFactory resultFactory

    // Errors
    // ------

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
        this.translateTextResponse(code, params).then({ List<String> responses ->
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
        this.translate(code, params).then({ Closure callBody ->
            resultFactory.success({
                Response { callBody() }
            })
        })
    }

    // Utility methods
    // ---------------

    protected String getMessage(String code, Collection<String> args=[]) {
        messageSource.getMessage(code, args as Object[], LCH.getLocale())
    }
    protected String getLink(Map linkParams) {
        linkGenerator.link(namespace:"v1", resource:"publicRecord", action:"save",
            params:linkParams, absolute:true)
    }
    protected List<String> formatAnnouncements(List<FeaturedAnnouncement> announces) {
        if (announces) {
            announces.collect { FeaturedAnnouncement announce ->
                this.formatAnnouncement(announce.dateCreated, announce.owner.name,
                    announce.message)
            }
        }
        else { getMessage("twimlBuilder.noAnnouncements") }
    }
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
                    [Constants.TEXT_SEE_ANNOUNCEMENTS, Constants.TEXT_SUBSCRIBE])
                break
            case TextResponse.INSTRUCTIONS_SUBSCRIBED:
                responses << getMessage("twimlBuilder.text.instructionsSubscribed",
                    [Constants.TEXT_SEE_ANNOUNCEMENTS, Constants.TEXT_UNSUBSCRIBE])
                break
            case TextResponse.ANNOUNCEMENTS:
                if (params.announcements instanceof List) {
                    responses += this.formatAnnouncements(params.announcements)
                }
                break
            case TextResponse.SUBSCRIBED:
                responses << getMessage("twimlBuilder.text.subscribed",
                    [Constants.TEXT_UNSUBSCRIBE])
                break
            case TextResponse.UNSUBSCRIBED:
                responses << getMessage("twimlBuilder.text.unsubscribed",
                    [Constants.TEXT_SUBSCRIBE])
                break
        }
        if (responses) {
            resultFactory.success(responses)
        }
        else {
            resultFactory.failWithMessageAndStatus(BAD_REQUEST,
                'twimlBuilder.invalidCode.', [code])
        }
    }
    Result<Closure> translate(CallResponse code, Map params=[:]) {
        Closure callBody
        switch (code) {
            case CallResponse.SELF_GREETING:
                String directions = getMessage("twimlBuilder.call.selfGreeting")
                callBody = {
                    Gather(numDigits:11) { Say(directions) }
                    Redirect(".")
                }
                break
            case CallResponse.SELF_CONNECTING:
                if (params.numAsString instanceof String) {
                    String connecting = getMessage("twimlBuilder.call.selfConnecting",
                            [Helpers.formatNumberForSay(params.numAsString)]),
                        goodbye = getMessage("twimlBuilder.call.goodbye")
                    callBody = {
                        Say(connecting)
                        Dial { Number(params.numAsString) }
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
                        Redirect(".")
                    }
                }
                break
            case CallResponse.CONNECT_INCOMING:
                if (params.nameOrNumber instanceof String &&
                        params.numsToCall instanceof Collection &&
                        params.linkParams instanceof Map) {
                    String connecting = getMessage("twimlBuilder.call.connectIncoming",
                            [params.nameOrNumber]),
                        voicemailWebhook = getLink(params.linkParams)
                    callBody = {
                        Say(connecting)
                        Dial(timeout:"15") {
                            for (num in params.numsToCall) { Number(num) }
                        }
                        Redirect(voicemailWebhook)
                    }
                }
                break
            case CallResponse.VOICEMAIL:
                String directions = getMessage("twimlBuilder.call.voicemail"),
                    goodbye = getMessage("twimlBuilder.call.goodbye")
                callBody = {
                    Say(directions)
                    Record(maxLength:160)
                    Say(goodbye)
                    Hangup()
                }
                break
            case CallResponse.CONFIRM_BRIDGE:
                if (params.contact instanceof Contact &&
                        params.linkParams instanceof Map) {
                    Contact c1 = params.contact
                    String confirmation = getMessage("twimlBuilder.call.confirmBridge",
                            [c1.getNameOrNumber(true)]),
                        noResponse = getMessage("twimlBuilder.call.noConfirmBridge"),
                        digitsWebhook = getLink(params.linkParams)
                    callBody = {
                        Gather(action:digitsWebhook, numDigits:1) {
                            Say(confirmation)
                        }
                        Say(noResponse)
                        Hangup()
                    }
                }
                break
            case CallResponse.FINISH_BRIDGE:
                if (params.contact instanceof Contact) {
                    Contact c1 = params.contact
                    String confirmation = getMessage("twimlBuilder.call.finishBridge",
                            [c1.getNameOrNumber(true)]),
                        done = getMessage("twimlBuilder.call.finishBridgeDone")
                    callBody = {
                        Say(confirmation)
                        c1.numbers?.each { ContactNumber num ->
                            Say(getMessage("twimlBuilder.call.bridgeNumber",
                                [Helpers.formatNumberForSay(num.number)]))
                            Dial(num.e164PhoneNumber)
                        }
                        Pause(length:"5")
                        Say(done)
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
                                [Constants.CALL_GREETING_UNSUBSCRIBE]) :
                            getMessage("twimlBuilder.call.announcementSubscribe",
                                [Constants.CALL_SUBSCRIBE]),
                        connectToStaff = getMessage("twimlBuilder.call.connectToStaff")
                    result = {
                        Response {
                            Gather(numDigits:1) {
                                Say(welcome)
                                Say(sAction)
                                Say(connectToStaff)
                            }
                            Redirect(".")
                        }
                    }
                }
                break
            case CallResponse.HEAR_ANNOUNCEMENTS:
                if (params.announcements instanceof Collection) {
                    String sAction = params.isSubscribed ?
                            getMessage("twimlBuilder.call.announcementUnsubscribe",
                                [Constants.CALL_GREETING_UNSUBSCRIBE]) :
                            getMessage("twimlBuilder.call.announcementSubscribe",
                                [Constants.CALL_SUBSCRIBE]),
                        connectToStaff = getMessage("twimlBuilder.call.connectToStaff")
                    callBody = {
                        Gather(numDigits:1) {
                            this.formatAnnouncements(params.announcements).each {
                                Say(it)
                            }
                            Say(sAction)
                            Say(connectToStaff)
                        }
                        Redirect(".")
                    }
                }
                break
            case CallResponse.ANNOUNCEMENT_AND_DIGITS:
                if (params.message instanceof String &&
                        params.identifier instanceof String) {
                    String announcementIntro = getMessage("twimlBuilder.call.announcementIntro",
                            [params.identifier]),
                        unsubscribe = getMessage("twimlBuilder.call.announcementUnsubscribe",
                            [Constants.CALL_ANNOUNCEMENT_UNSUBSCRIBE])
                    callBody = {
                        Say(announcementIntro)
                        Gather(numDigits:1) {
                            formatAnnouncement(DateTime.now(), params.identifier,
                                params.message)
                            Say(unsubscribe)
                        }
                        Redirect(".")
                    }
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
                String subscribed = getMessage("twimlBuilder.call.subscribed")
                callBody = {
                    Say(subscribed)
                    Say(goodbye)
                    Hangup()
                }
                break
        }
        if (callBody) {
            resultFactory.success(callBody)
        }
        else {
            resultFactory.failWithMessageAndStatus(BAD_REQUEST,
                'twimlBuilder.invalidCode.', [code])
        }
    }
}
