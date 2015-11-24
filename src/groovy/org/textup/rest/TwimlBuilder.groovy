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

    //Call codes
    static final String CALL_SELF_GREETING = "callSelfGreeting"
    static final String CALL_SELF_ERROR = "callSelfError"
    static final String CALL_SELF_CONNECTING = "callSelfConnecting"
    static final String CALL_VOICEMAIL = "callVoicemail"
    static final String CALL_CONNECTING = "callConnecting"
    static final String CALL_DEST_NOT_FOUND = "callDestNotFound"
    static final String CALL_TEAM_GREETING = "callTeamGreeting"
    static final String CALL_TEAM_ERROR = "callTeamError"
    static final String CALL_TEAM_TAG_MESSAGE = "callTeamTagMessage"
    static final String CALL_TEAM_TAG_NONE = "callTeamTagNone"
    static final String CALL_SERVER_ERROR = "callServerError"
    static final int CALL_TEAM_CONNECT = 0

    //Text codes
    static final String TEXT_STAFF_AWAY = "textStaffAway"

    static int convertTagIndexToOptionNumber(int index) {
        index + 1 //zero is CALL_TEAM_CONNECT
    }
    static int convertOptionNumberToTagIndex(int optionNum) {
        optionNum - 1 //reverse operation
    }

    Result<Closure> noResponse() { resultFactory.success({ Response { } }) }
    Result<Closure> buildXmlFor(String code, Map params=[:]) {
        Closure result = null
        switch (code) {

            ///////////
            // Calls //
            ///////////

            case CALL_SELF_GREETING:
                String welcome = getMessage("twimlBuilder.staffToSelfWelcome"),
                    directions = getMessage("twimlBuilder.staffToSelfDirections"),
                    digitsWebhook = getLink(handle:Constants.CALL_STAFF_STAFF_DIGITS),
                    repeatWebhook = getLink(handle:Constants.CALL_TEXT_REPEAT, for:CALL_SELF_GREETING)
                result = {
                    Response {
                        Say(welcome)
                        Gather(action:digitsWebhook, numDigits:11) {
                            Say(directions)
                        }
                        Redirect(repeatWebhook)
                    }
                }
                break
            case CALL_SELF_ERROR:
                if (params.digits) {
                    String formatted = formatNumberForSay(params.digits),
                        error = getMessage("twimlBuilder.staffToSelfError", [formatted]),
                        directions = getMessage("twimlBuilder.staffToSelfDirections"),
                        digitsWebhook = getLink(handle:Constants.CALL_STAFF_STAFF_DIGITS),
                        repeatWebhook = getLink(handle:Constants.CALL_TEXT_REPEAT, for:CALL_SELF_GREETING)
                    result = {
                        Response {
                            Say(error)
                            Gather(action:digitsWebhook, numDigits:11) {
                                Say(directions)
                            }
                            Redirect(repeatWebhook)
                        }
                    }
                }
                else {
                    return resultFactory.failWithMessageAndStatus(BAD_REQUEST,
                        "twimlBuilder.buildXmlFor.paramNotFound", [CALL_SELF_ERROR])
                }
                break
            case CALL_SELF_CONNECTING:
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
                else {
                    return resultFactory.failWithMessageAndStatus(BAD_REQUEST,
                        "twimlBuilder.buildXmlFor.paramNotFound", [CALL_SELF_ERROR])
                }
                break
            case CALL_VOICEMAIL:
                String directions = getMessage("twimlBuilder.voicemailDirections"),
                    voicemailWebhook = getLink(handle:Constants.CALL_VOICEMAIL),
                    repeatWebhook = getLink(handle:Constants.CALL_TEXT_REPEAT, for:CALL_VOICEMAIL)
                result = {
                    Response {
                        Say(directions)
                        Record(action:voicemailWebhook, maxLength:160)
                        Redirect(repeatWebhook)
                    }
                }
                break
            case CALL_CONNECTING:
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
                else {
                    return resultFactory.failWithMessageAndStatus(BAD_REQUEST,
                        "twimlBuilder.buildXmlFor.paramNotFound", [CALL_CONNECTING])
                }
                break
            case CALL_DEST_NOT_FOUND:
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
                else {
                    return resultFactory.failWithMessageAndStatus(BAD_REQUEST,
                        "twimlBuilder.buildXmlFor.paramNotFound", [CALL_DEST_NOT_FOUND])
                }
                break
            case CALL_TEAM_GREETING:
                if (params.teamName && params.numDigits) {
                    String d = params.teamDirections ?: getMessage("twimlBuilder.teamNoTags"),
                        welcome = getMessage("twimlBuilder.teamWelcome", [params.teamName]),
                        directions = getMessage("twimlBuilder.teamDirections",
                            [CALL_TEAM_CONNECT, d]),
                        digitsWebhook = getLink(handle:Constants.CALL_PUBLIC_TEAM_DIGITS),
                        repeatWebhook = getLink(handle:Constants.CALL_INCOMING)
                    result = {
                        Response {
                            Say(welcome)
                            Gather(action:digitsWebhook, numDigits:params.numDigits) {
                                Say(directions)
                            }
                            Redirect(repeatWebhook)
                        }
                    }
                }
                else {
                    return resultFactory.failWithMessageAndStatus(BAD_REQUEST,
                        "twimlBuilder.buildXmlFor.paramNotFound", [CALL_TEAM_GREETING])
                }
                break
            case CALL_TEAM_ERROR:
                if (params.digits && params.numDigits) {
                    String d = params.teamDirections ?: getMessage("twimlBuilder.teamNoTags"),
                        formatted = formatNumberForSay(params.digits),
                        error = getMessage("twimlBuilder.teamError", [formatted]),
                        directions = getMessage("twimlBuilder.teamDirections",
                            [CALL_TEAM_CONNECT, d]),
                        digitsWebhook = getLink(handle:Constants.CALL_PUBLIC_TEAM_DIGITS),
                        repeatWebhook = getLink(handle:Constants.CALL_INCOMING)
                    result = {
                        Response {
                            Say(error)
                            Gather(action:digitsWebhook, numDigits:params.numDigits) {
                                Say(directions)
                            }
                            Redirect(repeatWebhook)
                        }
                    }
                }
                else {
                    return resultFactory.failWithMessageAndStatus(BAD_REQUEST,
                        "twimlBuilder.buildXmlFor.paramNotFound", [CALL_TEAM_ERROR])
                }
                break
            case CALL_TEAM_TAG_MESSAGE:
                if (params.datePosted instanceof DateTime && params.message &&
                    params.teamDirections && params.numDigits) {
                    String formatted = new PrettyTime(LCH.getLocale()).format(params.datePosted.toDate()),
                        error = getMessage("twimlBuilder.teamTagMessage", [formatted, params.message]),
                        directions = getMessage("twimlBuilder.teamDirections",
                            [CALL_TEAM_CONNECT, params.teamDirections]),
                        digitsWebhook = getLink(handle:Constants.CALL_PUBLIC_TEAM_DIGITS),
                        repeatWebhook = getLink(handle:Constants.CALL_INCOMING)
                    result = {
                        Response {
                            Say(error)
                            Gather(action:digitsWebhook, numDigits:params.numDigits) {
                                Say(directions)
                            }
                            Redirect(repeatWebhook)
                        }
                    }
                }
                else {
                    return resultFactory.failWithMessageAndStatus(BAD_REQUEST,
                        "twimlBuilder.buildXmlFor.paramNotFound", [CALL_TEAM_TAG_MESSAGE])
                }
                break
            case CALL_TEAM_TAG_NONE:
                if (params.tagName && params.teamDirections && params.numDigits) {
                    String none = getMessage("twimlBuilder.teamTagNone", [params.tagName]),
                        directions = getMessage("twimlBuilder.teamDirections",
                            [CALL_TEAM_CONNECT, params.teamDirections]),
                        digitsWebhook = getLink(handle:Constants.CALL_PUBLIC_TEAM_DIGITS),
                        repeatWebhook = getLink(handle:Constants.CALL_INCOMING)
                    result = {
                        Response {
                            Say(none)
                            Gather(action:digitsWebhook, numDigits:params.numDigits) {
                                Say(directions)
                            }
                            Redirect(repeatWebhook)
                        }
                    }
                }
                else {
                    return resultFactory.failWithMessageAndStatus(BAD_REQUEST,
                        "twimlBuilder.buildXmlFor.paramNotFound", [CALL_TEAM_TAG_NONE])
                }
                break
            case CALL_SERVER_ERROR:
                String serverError = getMessage("twimlBuilder.cannotCompleteCall")
                result = {
                    Response {
                        Say(serverError)
                        Hangup()
                    }
                }
                break

            ///////////
            // Texts //
            ///////////

            case TEXT_STAFF_AWAY:
                String message = getMessage("twimlBuilder.textStaffAway")
                result = {
                    Response {
                        Message(message)
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

    protected String formatNumberForSay(String number) {
        number.replaceAll(/\D*/, "").replaceAll(/.(?!$)/, /$0 /)
    }

    protected String getMessage(String code, Collection<String> args=[]) {
        messageSource.getMessage(code, args as Object[], LCH.getLocale())
    }

    protected String getLink(Map linkParams) {
        linkGenerator.link(namespace:"v1", resource:"publicRecord", action:"save",
            params:linkParams, absolute:true)
    }
}
