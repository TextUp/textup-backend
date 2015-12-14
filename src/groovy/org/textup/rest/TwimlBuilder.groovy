package org.textup.rest

import org.codehaus.groovy.grails.web.mapping.LinkGenerator
import org.joda.time.DateTime
import org.ocpsoft.prettytime.PrettyTime
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.i18n.LocaleContextHolder as LCH
import org.springframework.context.MessageSource
import org.textup.*
import static org.springframework.http.HttpStatus.*
import static org.textup.Helpers.*

class TwimlBuilder {

    @Autowired
    LinkGenerator linkGenerator
    @Autowired
    MessageSource messageSource
    @Autowired
    ResultFactory resultFactory

    Result<Closure> noResponse() { resultFactory.success({ Response { } }) }
    Result<Closure> buildMessageFor(String msg) {
        resultFactory.success({ Response { Message(msg) } })
    }
    Result<Closure> buildXmlFor(TextResponse code, Map params=[:]) {
        List messages = []
        switch (code) {
            case TextResponse.STAFF_SELF_GREETING:
                if (params.staff instanceof Staff) {
                    Staff s1 = params.staff
                    String availNow = translateIsAvailable(s1.isAvailableNow()),
                        predicate = getMessage("twimlBuilder.textSelfManualSchedule")
                    if (!s1.manualSchedule) {
                        ScheduleChange sChange = s1.nextChange().payload
                        String nextChange = translateScheduleChange(sChange.type),
                            whenChange = new PrettyTime(LCH.getLocale()).format(sChange.when.toDate())
                        predicate = getMessage("twimlBuilder.textSelfAutoSchedule", [nextChange, whenChange])
                    }
                    messages << getMessage("twimlBuilder.textSelf", [s1.name, availNow, predicate])
                }
                break
            case TextResponse.TEAM_INSTRUCTIONS:
                messages << getMessage("twimlBuilder.teamInstructions", [Constants.ACTION_SEE_ANNOUNCEMENTS, Constants.ACTION_SUBSCRIBE])
                break
            case TextResponse.TEAM_INSTRUCTIONS_SUBSCRIBED:
                messages << getMessage("twimlBuilder.teamInstructionsSubscribed", [Constants.ACTION_SEE_ANNOUNCEMENTS, Constants.ACTION_UNSUBSCRIBE_ALL])
                break
            case TextResponse.TEAM_ANNOUNCEMENTS:
                if (params.features instanceof List) {
                    messages += formatAnnouncements(params.features)
                }
                break
            case TextResponse.TEAM_NO_ANNOUNCEMENTS:
                messages << getMessage("twimlBuilder.noTeamAnnouncements")
                break
            case TextResponse.TEAM_SUBSCRIBE_ALL:
                messages << getMessage("twimlBuilder.teamSubscribeAll")
                break
            case TextResponse.TEAM_UNSUBSCRIBE_ALL:
                messages << getMessage("twimlBuilder.teamUnsubscribeAll")
                break
            case TextResponse.TEAM_UNSUBSCRIBE_ONE:
                if (params.tagName) {
                    messages << getMessage("twimlBuilder.teamUnsubscribeOne", [params.tagName])
                }
                break
            case TextResponse.NOT_FOUND:
                messages << getMessage("twimlBuilder.phoneNotFound")
                break
            case TextResponse.SERVER_ERROR:
                messages << getMessage("twimlBuilder.textServerError")
                break
        }
        if (messages) {
            Closure response = { Response { messages.each { Message(it) } } }
            resultFactory.success(response)
        }
        else {
            resultFactory.failWithMessageAndStatus(BAD_REQUEST,
                "twimlBuilder.buildXmlFor.codeNotFound", [code])
        }
    }
    Result<Closure> buildXmlFor(CallResponse code, Map params=[:]) {
        Closure result = null
        switch (code) {
            case CallResponse.SELF_GREETING:
                String welcome = getMessage("twimlBuilder.staffToSelfWelcome"),
                    directions = getMessage("twimlBuilder.staffToSelfDirections"),
                    digitsWebhook = getLink(handle:Constants.CALL_STAFF_STAFF_DIGITS),
                    repeatWebhook = getLink(handle:Constants.CALL_TEXT_REPEAT, for:CALL_SELF_GREETING)
                result = {
                    Response {
                        Gather(action:digitsWebhook, numDigits:11) {
                            Say(welcome)
                            Say(directions)
                        }
                        Redirect(repeatWebhook)
                    }
                }
                break
            case CallResponse.SELF_ERROR:
                if (params.digits) {
                    String formatted = formatNumberForSay(params.digits),
                        error = getMessage("twimlBuilder.staffToSelfError", [formatted]),
                        repeatWebhook = getLink(handle:Constants.CALL_TEXT_REPEAT, for:CALL_SELF_GREETING)
                    result = {
                        Response {
                            Say(error)
                            Redirect(repeatWebhook)
                        }
                    }
                }
                break
            case CallResponse.SELF_CONNECTING:
                if (params.num) {
                    String number = formatNumberForSay(params.num),
                        connecting = getMessage("twimlBuilder.staffToSelfConnecting", [number])
                    result = {
                        Response {
                            Say(connecting)
                            Dial { Number(params.num) }
                        }
                    }
                }
                break
            case CallResponse.BRIDGE_CONFIRM_CONNECT:
                if (params.contactToBridge instanceof Contact) {
                    Contact c1 = params.contactToBridge
                    String attrib = c1.name ?: formatNumberForSay(c1.numbers?.number),
                        confirmation = getMessage("twimlBuilder.confirmCallBridge", [attrib]),
                        noResponse = getMessage("twimlBuilder.noResponseConfirmCallBridge"),
                        digitsWebhook = getLink(contactToBridge:c1.contactId, handle:Constants.CALL_BRIDGE)
                    result = {
                        Response {
                            Gather(action:digitsWebhook, numDigits:1) {
                                Say(confirmation)
                            }
                            Say(noResponse)
                        }
                    }
                }
                break
            case CallResponse.BRIDGE_CONNECT:
                if (params.contactToBridge instanceof Contact) {
                    String couldNotConnect = getMessage("twimlBuilder.callBridgeDone")
                    Contact c1 = params.contactToBridge
                    result = {
                        Response {
                            if (c1.name) {
                                Say(getMessage("twimlBuilder.callBridge", [c1.name]))
                            }
                            c1.numbers?.each { ContactNumber num ->
                                Say(getMessage("twimlBuilder.callBridgeNumber", [formatNumberForSay(num.number)]))
                                Dial(num.e164PhoneNumber)
                            }
                            Say(couldNotConnect)
                        }
                    }
                }
                break
            case CallResponse.VOICEMAIL:
                String directions = getMessage("twimlBuilder.voicemailDirections"),
                    voicemailWebhook = getLink(handle:Constants.CALL_VOICEMAIL),
                    repeatWebhook = getLink(handle:Constants.CALL_TEXT_REPEAT, for:Constants.CALL_VOICEMAIL)
                result = {
                    Response {
                        Say(directions)
                        Record(action:voicemailWebhook, maxLength:160)
                        Redirect(repeatWebhook)
                    }
                }
                break
            case CallResponse.CONNECTING:
                if (params.numsToCall instanceof Collection) {
                    String connecting = getMessage("twimlBuilder.connectingCall"),
                        voicemailWebhook = getLink(handle:Constants.CALL_VOICEMAIL)
                    result = {
                        Response {
                            Say(connecting)
                            Dial {
                                for (num in params.numsToCall) {
                                    Number(num)
                                }
                            }
                            Redirect(voicemailWebhook)
                        }
                    }
                }
                break
            case CallResponse.DEST_NOT_FOUND:
                if (params.num) {
                    String number = formatNumberForSay(params.num),
                        notFound = getMessage("twimlBuilder.phoneNotFound", [number])
                    result = {
                        Response {
                            Say(notFound)
                            Hangup()
                        }
                    }
                }
                break
            case CallResponse.TEAM_GREETING:
                if (params.teamName && params.isSubscribed != null) {
                    String welcome = getMessage("twimlBuilder.teamWelcome", [params.teamName, Constants.CALL_GREETING_HEAR_ANNOUNCEMENTS]),
                        connectToStaff = getMessage("twimlBuilder.teamConnectToStaff", [Constants.CALL_GREETING_CONNECT_TO_STAFF]),
                        digitsWebhook = getLink(handle:Constants.CALL_PUBLIC_TEAM_DIGITS),
                        repeatWebhook = getLink(handle:Constants.CALL_INCOMING)
                    String sAction
                    if (params.isSubscribed) {
                        sAction = getMessage("twimlBuilder.teamWelcomeUnsubscribe", [Constants.CALL_GREETING_UNSUBSCRIBE_ALL])
                    }
                    else {
                        sAction = getMessage("twimlBuilder.teamWelcomeSubscribe", [Constants.CALL_GREETING_SUBSCRIBE_ALL])
                    }
                    result = {
                        Response {
                            Gather(action:digitsWebhook, numDigits:1) {
                                Say(welcome)
                                Say(sAction)
                                Say(connectToStaff)
                            }
                            Redirect(repeatWebhook)
                        }
                    }
                }
                break
            case CallResponse.TEAM_SUBSCRIBE_ALL:
                String subscribed = getMessage("twimlBuilder.callSubscribeAll"),
                    redirectWebhook = getLink(handle:Constants.CALL_INCOMING)
                result = {
                    Response {
                        Say(subscribed)
                        Redirect(redirectWebhook)
                    }
                }
                break
            case CallResponse.TEAM_UNSUBSCRIBE_ALL:
                String unsubscribed = getMessage("twimlBuilder.callUnsubscribeAll"),
                    redirectWebhook = getLink(handle:Constants.CALL_INCOMING)
                result = {
                    Response {
                        Say(unsubscribed)
                        Redirect(redirectWebhook)
                    }
                }
                break
            case CallResponse.TEAM_ANNOUNCEMENTS:
                if (params.features instanceof List) {
                    String redirectWebhook = getLink(handle:Constants.CALL_INCOMING)
                    result = {
                        Response {
                            formatAnnouncements(params.features).each { Say(it) }
                            Redirect(redirectWebhook)
                        }
                    }
                }
                break
            case CallResponse.TEAM_NO_ANNOUNCEMENTS:
                String noMsgs = getMessage("twimlBuilder.noTeamAnnouncements"),
                    redirectWebhook = getLink(handle:Constants.CALL_INCOMING)
                result = {
                    Response {
                        Say(noMsgs)
                        Redirect(redirectWebhook)
                    }
                }
                break
            case CallResponse.TEAM_ERROR:
                if (params.digits) {
                    String formatted = formatNumberForSay(params.digits),
                        error = getMessage("twimlBuilder.teamError", [formatted]),
                        repeatWebhook = getLink(handle:Constants.CALL_INCOMING)
                    result = {
                        Response {
                            Say(error)
                            Redirect(repeatWebhook)
                        }
                    }
                }
                break
            case CallResponse.ANNOUNCEMENT:
                if (params.contents && params.teamName && params.tagName && params.tagId && params.textId) {
                    String announce = getMessage("twimlBuilder.callAnnouncement", [params.teamName, params.tagName, params.contents]),
                        announceActions = getMessage("twimlBuilder.callAnnouncementActions", [Constants.CALL_ANNOUNCE_UNSUBSCRIBE_ONE, params.tagName, Constants.CALL_ANNOUNCE_UNSUBSCRIBE_ALL]),
                        digitsWebhook = getLink(handle:Constants.CALL_TEAM_ANNOUNCEMENT_DIGITS, teamContactTagId:params.tagId, recordTextId:params.textId),
                        repeatWebhook = getLink(handle:Constants.CALL_ANNOUNCEMENT, teamContactTagId:params.tagId, recordTextId:params.textId)
                    result = {
                        Response {
                            Gather(action:digitsWebhook, numDigits:1) {
                                Say(announce)
                                Say(announceActions)
                            }
                            Redirect(repeatWebhook)
                        }
                    }
                }
                break
            case CallResponse.ANNOUNCEMENT_UNSUBSCRIBE_ONE:
                if (params.tagName) {
                    String unsubOne = getMessage("twimlBuilder.callUnsubscribeOne", [params.tagName])
                    result = {
                        Response {
                            Say(unsubOne)
                            Hangup()
                        }
                    }
                }
                break
            case CallResponse.ANNOUNCEMENT_UNSUBSCRIBE_ALL:
                String unsubAll = getMessage("twimlBuilder.callUnsubscribeAll")
                result = {
                        Response {
                            Say(unsubAll)
                            Hangup()
                        }
                    }
                break
            case CallResponse.SERVER_ERROR:
                String serverError = getMessage("twimlBuilder.cannotCompleteCall")
                result = {
                    Response {
                        Say(serverError)
                        Hangup()
                    }
                }
                break
        }
        if (result) { resultFactory.success(result) }
        else {
            resultFactory.failWithMessageAndStatus(BAD_REQUEST,
                "twimlBuilder.buildXmlFor.codeNotFound", [code])
        }
    }

    ////////////////////
    // Helper methods //
    ////////////////////

    protected String getMessage(String code, Collection<String> args=[]) {
        messageSource.getMessage(code, args as Object[], LCH.getLocale())
    }

    protected String getLink(Map linkParams) {
        linkGenerator.link(namespace:"v1", resource:"publicRecord", action:"save",
            params:linkParams, absolute:true)
    }

    protected List<String> formatAnnouncements(List<FeaturedAnnouncement> features) {
        features.collect { FeaturedAnnouncement fa ->
            RecordText t1 = fa.featured
            String timeAgo = new PrettyTime(LCH.getLocale()).format(t1.dateCreated.toDate())
            getMessage("twimlBuilder.teamAnnouncement", [timeAgo, t1.contents])
        }
    }

    protected String translateIsAvailable(boolean isAvailable) {
        if (isAvailable) { getMessage("twimlBuilder.available") }
        else { getMessage("twimlBuilder.unavailable") }
    }
    protected String translateScheduleChange(String sChangeType) {
        if (sChangeType == Constants.SCHEDULE_AVAILABLE) {
            getMessage("twimlBuilder.available")
        }
        else { getMessage("twimlBuilder.unavailable") }
    }
}
