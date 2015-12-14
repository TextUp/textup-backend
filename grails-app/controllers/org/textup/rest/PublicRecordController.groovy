package org.textup.rest

import grails.converters.JSON
import javax.servlet.http.HttpServletRequest
import org.codehaus.groovy.grails.web.servlet.mvc.GrailsParameterMap
import org.restapidoc.annotation.*
import org.restapidoc.pojo.*
import org.springframework.security.access.annotation.Secured
import org.textup.*
import static org.springframework.http.HttpStatus.*

@RestApi(name="[Public] Record", description = "Webhook endpoint for Twilio.")
@Secured("permitAll")
class PublicRecordController extends BaseController {

    static namespace = "v1"
    //grailsApplication from superclass
    //authService from superclass
    def callService
    def textService
    def recordService
    def staffService
    def twimlBuilder

    ////////////////////////
    // Prohibited methods //
    ////////////////////////

    def index() {
        println "1"
        notAllowed()
    }
    def show() {
        println "2"
        notAllowed() }
    def update() {
        println "3"
        notAllowed() }
    def delete() {
        println "4"
        notAllowed() }

    /////////////////////
    // Handle webhooks //
    /////////////////////

    @RestApiMethod(description="Handles webhooks")
    @RestApiParams(params=[
        @RestApiParam(name="handle", type="String", required=true,
            paramType=RestApiParamType.QUERY, description='''If is a text (POST body has MessageSid),
            then one of "incoming" or "status". If is a call (POST has CallSid), then one of "incoming",
            "status", "voicemail", "digitsFromPublicForTeam" or "digitsFromStaffForStaff." If you want to
            repeat a previous response, you can specify the handle as "repeat", and then pass in the
            code of the response to repeat in the "for" query param'''),
        @RestApiParam(name="contactId", type="Number", required=true,
            paramType=RestApiParamType.QUERY, description='''If updating the status for a Twilio Client
            call made directly from the frontend Javascript, then you must include the id of the contact
            that was called so we know which contact to update the status for.'''),
        @RestApiParam(name="for", type="String", required=true,
            paramType=RestApiParamType.QUERY, description='''The code of the response you would like
            repeated. This is mandatory when the handle is specified as "repeat."''')
    ])
    @RestApiErrors(apierrors=[
        @RestApiError(code="400", description="No records found that match the Sid provided."),
        @RestApiError(code="403", description='''The signature header does not match or you specified
            the id of a contact not owned by the From number.'''),
        @RestApiError(code="404", description="Not found. Could not find a matching phone or receipt."),
        @RestApiError(code="422", description='''MessageSid or CallSid not provided in the POST
            body, or invalid status parameter, or contactId not provided with Twilio Client call.''')
    ])
    def save() {
        println "PUBLIC RECORD CONTROLLER -> handle ${params.handle}"

        if (params.handle == Constants.CALL_TEXT_REPEAT) { handleRepeat(params) }
        else {
            //confirm that request is from Twilio
            if (authenticateRequest(request, params)) {
                if (params.CallSid) {
                    switch (params.handle) {
                        case Constants.CALL_INCOMING:
                            return handleIncomingForCall(params)
                        case Constants.CALL_STATUS:
                            return handleStatusForCall(params)
                        case Constants.CALL_VOICEMAIL:
                            return handleVoicemailForCall(params)
                        case Constants.CALL_PUBLIC_TEAM_DIGITS:
                            return handlePublicTeamDigitsForCall(params)
                        case Constants.CALL_STAFF_STAFF_DIGITS:
                            return handleStaffStaffDigitsForCall(params)
                        case Constants.CONFIRM_CALL_BRIDGE:
                            return confirmBridgeCall(params)
                        case Constants.CALL_BRIDGE:
                            return doBridgeCall(params)
                        case Constants.CALL_ANNOUNCEMENT:
                            return doCallAnnouncement(params)
                        case Constants.CALL_TEAM_ANNOUNCEMENT_DIGITS:
                            return handleTeamAnnouncementDigits(params)
                    }
                }
                else if (params.MessageSid) {
                    switch (params.handle) {
                        case Constants.TEXT_INCOMING:
                            return handleIncomingForText(params)
                        case Constants.TEXT_STATUS:
                            return handleStatusForText(params)
                    }
                }
                badRequest()
            }
            else { forbidden() }
        }
    }

    ////////////////////////////////////////////
    // Helper methods for repeating responses //
    ////////////////////////////////////////////

    protected def handleRepeat(GrailsParameterMap params) {
        if (params.for) {
            Result res = twimlBuilder.buildXmlFor(params.for)
            if (res.success) { renderAsXml(res.payload) }
            else { handleResultFailure(res) }
        }
        else {
            String error = g.message(code:"publicRecordController.handleRepeat.noRepeatFor")
            respondWithError(error, BAD_REQUEST)
        }
    }

    ////////////////////
    // Authentication //
    ////////////////////

    protected boolean authenticateRequest(HttpServletRequest request, GrailsParameterMap params) {
        String browserURL = (request.requestURL.toString() - request.requestURI) + request.forwardURI,
            authToken = grailsApplication.config.textup.apiKeys.twilio.authToken,
            authHeaderName = "x-twilio-signature",
            authHeader = request.getHeader(authHeaderName)
        if (authHeader) {
            if (request.queryString) { browserURL = "$browserURL?${request.queryString}" }
            Map<String,String> twilioParams = extractTwilioParams(request, params)

            StringBuilder data = new StringBuilder(browserURL)
            List<String> sortedKeys = twilioParams.keySet().toList().sort() //sort keys lexicographically
            sortedKeys.each { String key -> data << key << twilioParams[key] }
            //first check https then check http if fails. Scheme detection in request object is spotty
            String dataString = data.toString(), httpsData, httpData
            if (dataString.startsWith("http://")) {
                httpData = dataString
                httpsData = dataString.replace("http://", "https://")
            }
            else if (dataString.startsWith("https://")) {
                httpData = dataString.replace("https://", "http://")
                httpsData = dataString
            }
            else {
                log.error("PublicRecordController.authenticateRequest: invalid data: $dataString")
                return false
            }
            //first try https
            String encoded = Helpers.getBase64HmacSHA1(httpsData.toString(), authToken)
            boolean isAuth = (encoded == authHeader)
            //then fallback to checking http if needed
            if (!isAuth) {
                encoded = Helpers.getBase64HmacSHA1(httpData.toString(), authToken)
                isAuth = (encoded == authHeader)
            }
            isAuth
        }
        else { false }
    }

    protected Map<String,String> extractTwilioParams(HttpServletRequest request, GrailsParameterMap allParams) {
        Collection<String> requestParamKeys = request.parameterMap.keySet(), queryParams = []
        request.queryString?.tokenize("&")?.each { queryParams << it.tokenize("=")[0] }
        HashSet<String> ignoreParamKeys = new HashSet<>(queryParams),
            keepParamKeys = new HashSet<>(requestParamKeys)
        Map<String,String> twilioParams = [:]
        allParams.each { String key, String val ->
            if (keepParamKeys.contains(key) && !ignoreParamKeys.contains(key)) {
                twilioParams[key] = val
            }
        }
        twilioParams
    }

    /////////////////////////////
    // Helper methods for call //
    /////////////////////////////

    protected def handleStatusForCall(GrailsParameterMap params) {
        if (recordService.receiptExistsForApiId(params.CallSid)) {
            String status = Helpers.translateCallStatus(params.CallStatus)
            Result<List<RecordItemReceipt>> res = recordService.updateStatus(params.CallSid,
                status, Helpers.toInteger(params.CallDuration))
            if (res.success) {
                if (status == Constants.RECEIPT_FAILED) {
                    for (receipt in res.payload) {
                        res = callService.retry(receipt.item.id)
                        if (res.success) { break }
                    }
                }
                ok()
            }
            else {
                log.error("PublicRecordController.handleStatusForCall: $res")
                handleResultFailure(res)
            }
        }
        //we're updating the status where we connected the call (say for incoming client calls)
        else if (recordService.receiptExistsForApiId(params.ParentCallSid)) {
            Result<List<RecordItemReceipt>> res = recordService.updateStatus(params.ParentCallSid,
                Helpers.translateCallStatus(params.CallStatus), Helpers.toInteger(params.CallDuration))
            if (res.success) { ok() }
            else { handleResultFailure(res) }
        }
        //if not found this is a Twilio Client call initiated by the frontend
        else { handleStatusForClientCall(params) }
    }

    //if this is a Twilio client call, then we directly initiate
    //the call from the javascript without first making a request
    //to create a RecordCall in the backend
    protected def handleStatusForClientCall(GrailsParameterMap params) {
        if (params.contactId) {
            Map receiptParams = [apiId:params.CallSid, status:Helpers.translateCallStatus(params.CallStatus)]
            Result res = recordService.createRecordCallForContact(params.contactId, params.From,
                params.To, Helpers.toInteger(params.CallDuration), receiptParams)
            if (res.success) { ok() }
            else { handleResultFailure(res) }
        }
        else {
            String error = g.message(code:
                "publicRecordController.handleStatusForClientCall.noContactId")
            respondWithError(error, BAD_REQUEST)
        }
    }

    protected def handleIncomingForCall(GrailsParameterMap params) {
        //case 1: staff member is calling from personal phone to TextUp phone
        String from = Helpers.cleanNumber(params.From),
            to = Helpers.cleanNumber(params.To)
        if (staffService.staffExistsForPersonalAndWorkPhoneNums(from, to)) {
            Result res = twimlBuilder.buildXmlFor(CallResponse.SELF_GREETING)
            if (res.success) { renderAsXml(res.payload) }
            else { handleResultFailure(res) }
        }
        //case 2: someone is calling a TextUp phone
        else {
            if (callService.staffPhoneExistsForNum(to)) {
                Result<Closure> res = callService.connectToPhone(from, to, params.CallSid)
                if (res.success) { renderAsXml(res.payload) }
                else { handleResultFailure(res) }
            }
            else if (callService.teamPhoneExistsForNum(to)) {
                Result<Closure> res = callService.handleCallToTeamPhone(from, to, params.CallSid)
                if (res.success) { renderAsXml(res.payload) }
                else { handleResultFailure(res) }
            }
            //phone not found
            else {
                Result res = twimlBuilder.buildXmlFor(CallResponse.DEST_NOT_FOUND, [num:to])
                if (res.success) { renderAsXml(res.payload) }
                else { handleResultFailure(res) }
            }
        }
    }

    //incoming call to a TextUp phone results in a voicemail
    protected def handleVoicemailForCall(GrailsParameterMap params) {
        if (recordService.receiptExistsForApiId(params.CallSid)) {
            Result res = callService.storeVoicemail(params.CallSid, Helpers.translateCallStatus(params.CallStatus),
                Helpers.toInteger(params.CallDuration), params.RecordingUrl, Helpers.toInteger(params.RecordingDuration))
            if (res.success) { ok() }
            else { handleResultFailure(res) }
        }
        else { notFound() }
    }

    protected def handlePublicTeamDigitsForCall(GrailsParameterMap params) {
        String from = Helpers.cleanNumber(params.From),
            to = Helpers.cleanNumber(params.To)
        if (params.Digits == Constants.CALL_GREETING_CONNECT_TO_STAFF) {
            Result<Closure> res = callService.connectToPhone(from, to, params.CallSid)
            if (res.success) { renderAsXml(res.payload) }
            else { handleResultFailure(res) }
        }
        else {
            Result<Closure> res = callService.handleDigitsToTeamPhone(from, to, params.Digits)
            if (res.success) { renderAsXml(res.payload) }
            else { handleResultFailure(res) }
        }
    }

    protected def handleStaffStaffDigitsForCall(GrailsParameterMap params) {
        Result<String> res = callService.handleOutgoingCallOrContactCode(params.CallSid,
            params.To, params.Digits)
        if (res.success) {
            res = twimlBuilder.buildXmlFor(CallResponse.SELF_CONNECTING, [num:res.payload])
        }
        else {
            res = twimlBuilder.buildXmlFor(CallResponse.SELF_ERROR, [digits:params.Digits])
        }
        if (res.success) { renderAsXml(res.payload) }
        else { handleResultFailure(res) }
    }

    protected def confirmBridgeCall(GrailsParameterMap params) {
        if (params.long("contactToBridge")) {
            Result<Closure> res = callService.confirmBridgeCallForContact(params.long("contactToBridge"))
            if (res.success) { renderAsXml(res.payload) }
            else { handleResultFailure(res) }
        }
        else { badRequest() }
    }

    protected def doBridgeCall(GrailsParameterMap params) {
        if (params.long("contactToBridge")) {
            Result<Closure> res = callService.completeBridgeCallForContact(params.long("contactToBridge"))
            if (res.success) { renderAsXml(res.payload) }
            else { handleResultFailure(res) }
        }
        else { badRequest() }
    }

    protected def doCallAnnouncement(GrailsParameterMap params) {
        if (params.long("teamContactTagId") && params.long("recordTextId")) {
            Long ctId = params.long("teamContactTagId"),
                rtId = params.long("recordTextId")
            Result<Closure> res = callService.completeCallAnnouncement(ctId, rtId)
            if (res.success) { renderAsXml(res.payload) }
            else { handleResultFailure(res) }
        }
        else { badRequest() }
    }

    protected def handleTeamAnnouncementDigits(GrailsParameterMap params) {
        if (params.long("teamContactTagId") && params.long("recordTextId")) {
            String from = Helpers.cleanNumber(params.From),
                to = Helpers.cleanNumber(params.To)
            Long ctId = params.long("teamContactTagId"),
                rtId = params.long("recordTextId")
            Result<Closure> res
            switch (params.Digits) {
                case Constants.CALL_ANNOUNCE_UNSUBSCRIBE_ONE:
                    res = callService.handleCallAnnouncementUnsubscribeOne(from, ctId)
                    break
                case Constants.CALL_ANNOUNCE_UNSUBSCRIBE_ALL:
                    res = callService.handleCallAnnouncementUnsubscribeAll(from, to)
                    break
                default:
                    res = callService.completeCallAnnouncement(ctId, rtId)
                    break
            }
            if (res.success) { renderAsXml(res.payload) }
            else { handleResultFailure(res) }
        }
        else { badRequest() }
    }

    /////////////////////////////
    // Helper methods for text //
    /////////////////////////////

    protected def handleIncomingForText(GrailsParameterMap params) {
        //case 1: staff member is texting from personal phone to TextUp phone
        String from = Helpers.cleanNumber(params.From),
            to = Helpers.cleanNumber(params.To)
        if (staffService.staffExistsForPersonalAndWorkPhoneNums(from, to)) {
            Result res = textService.handleIncomingToSelf(from, to)
            if (res.success) { renderAsXml(res.payload) }
            else { handleResultFailure(res) }
        }
        //case 2: someone is texting a TextUp phone
        else {
            if (callService.staffPhoneExistsForNum(to)) {
                Result<Closure> res = textService.handleIncomingToStaff(from, to, params.MessageSid, params.Body)
                if (res.success) { renderAsXml(res.payload) }
                else { handleResultFailure(res) }
            }
            else if (MessageService.teamPhoneExistsForNum(to)) {
                Result<Closure> res = textService.handleIncomingToTeam(from, to, params.MessageSid, params.Body)
                if (res.success) { renderAsXml(res.payload) }
                else { handleResultFailure(res) }
            }
            else { //phone not found
                Result res = twimlBuilder.buildXmlFor(TextResponse.NOT_FOUND)
                if (res.success) { renderAsXml(res.payload) }
                else { handleResultFailure(res) }
            }
        }
    }

    protected def handleStatusForText(GrailsParameterMap params) {
        if (recordService.receiptExistsForApiId(params.MessageSid)) {
            String status = Helpers.translateTextStatus(params.MessageStatus)
            Result<List<RecordItemReceipt>> res = recordService.updateStatus(params.MessageSid,
                Helpers.translateTextStatus(params.MessageStatus))
            if (res.success) {
                if (status == Constants.RECEIPT_FAILED) {
                    for (receipt in res.payload) {
                        res = textService.retry(receipt.item.id)
                        if (res.success) { break }
                    }
                }
                ok()
            }
            else {
                log.error("PublicRecordController.handleStatusForText: $res")
                handleResultFailure(res)
            }
        }
    }
}
