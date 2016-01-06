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
    def staffCallService
    def teamCallService

    def textService
    def staffTextService
    def teamTextService

    def recordService
    def staffService
    def twimlBuilder

    ////////////////////////
    // Prohibited methods //
    ////////////////////////

    def index() { notAllowed() }
    def show() { notAllowed() }
    def update() { notAllowed() }
    def delete() { notAllowed() }

    /////////////////////
    // Handle webhooks //
    /////////////////////

    @RestApiMethod(description="Handles webhooks")
    @RestApiParams(params=[
        @RestApiParam(name="handle", type="String", required=true,
            paramType=RestApiParamType.QUERY, description='''If is a text (POST body has MessageSid),
            then one of "incoming" or "status". If is a call (POST has CallSid), then one of "incoming",
            "status", "voicemail", "digitsFromPublicForTeam" or "digitsFromStaffForStaff."'''),
        @RestApiParam(name="contactId", type="Number", required=true,
            paramType=RestApiParamType.QUERY, description='''If updating the status for a Twilio Client
            call made directly from the frontend Javascript, then you must include the id of the contact
            that was called so we know which contact to update the status for.''')
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
        log.debug("PUBLIC RECORD CONTROLLER -> handle ${params.handle}")
        if (authService.authenticateRequest(request, params)) {
            if (params.CallSid) {
                switch (params.handle) {
                    case Constants.CALL_STATUS:
                        return callStatus(params)
                    case Constants.CALL_INCOMING:
                        return incomingCall(params)
                    case Constants.CALL_VOICEMAIL:
                        return voicemail(params)
                    case Constants.CALL_SEND_TO_VOICEMAIL:
                        return sendToVoicemail(params)
                    case Constants.CALL_PUBLIC_TEAM_DIGITS:
                        return incomingTeamDigitsForCall(params)
                    case Constants.CALL_STAFF_STAFF_DIGITS:
                        return incomingDigitsForSelfCall(params)
                    case Constants.CONFIRM_CALL_BRIDGE:
                        return confirmBridgeCall(params)
                    case Constants.CALL_BRIDGE:
                        return startBridgeCall(params)
                    case Constants.CALL_ANNOUNCEMENT:
                        return startCallAnnouncement(params)
                    case Constants.CALL_TEAM_ANNOUNCEMENT_DIGITS:
                        return incomingDigitsForAnnouncement(params)
                }
            }
            else if (params.MessageSid) {
                switch (params.handle) {
                    case Constants.TEXT_INCOMING:
                        return incomingText(params)
                    case Constants.TEXT_STATUS:
                        return textStatus(params)
                }
            }
            else { badRequest() }
        }
    }

    ////////////
    // Status //
    ////////////

    protected def callStatus(GrailsParameterMap params) {
        String sid = recordService.receiptExistsForApiId(params.CallSid) ? params.CallSid :
            (recordService.receiptExistsForApiId(params.ParentCallSid) ? params.ParentCallSid : null),
            status = Helpers.translateCallStatus(params.CallStatus)
        Integer duration = Helpers.toInteger(params.CallDuration)

        if (sid) {
            handleResultWithStatus(recordService.updateStatus(sid, status, duration), OK)
        }
        else if (params.contactId) { // if a Twilio Client call
            TransientPhoneNumber from = new TransientPhoneNumber(number:params.From),
                to = new TransientPhoneNumber(number:params.To)
            handleResultWithStatus(recordService.createRecordCallForContact(params.contactId, from,
                to, duration, [apiId:params.CallSid, status:status]), OK)
        }
        else {
            String error = g.message(code:"publicRecordController.clientCallStatus.noContactId")
            respondWithError(error, BAD_REQUEST)
        }
    }
    protected def textStatus(GrailsParameterMap params) {
        if (recordService.receiptExistsForApiId(params.MessageSid)) {
            String status = Helpers.translateTextStatus(params.MessageStatus)
            Result<List<RecordItemReceipt>> res = recordService.updateStatus(params.MessageSid, status)
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
                log.error("PublicRecordController.textStatus: $res")
                handleResultFailure(res)
            }
        }
    }

    /*
     * TEXT
     */

    protected def incomingText(GrailsParameterMap params) {
        TransientPhoneNumber from = new TransientPhoneNumber(number:params.From),
            to = new TransientPhoneNumber(number:params.To)
        if (callService.staffPhoneExistsForNum(to)) {
            handleXmlResult(staffTextService.handleIncoming(from, to, params.MessageSid, params.Body))
        }
        else if (callService.teamPhoneExistsForNum(to)) {
            handleXmlResult(teamTextService.handleIncomingToTeam(from, to, params.MessageSid, params.Body))
        }
        else { //phone not found
            handleXmlResult(twimlBuilder.buildXmlFor(TextResponse.NOT_FOUND))
        }
    }

    /*
     * CALL
     */

    //////////////////
    // General call //
    //////////////////

    protected def incomingCall(GrailsParameterMap params) {
        TransientPhoneNumber from = new TransientPhoneNumber(number:params.From),
            to = new TransientPhoneNumber(number:params.To)
        if (callService.staffPhoneExistsForNum(to)) {
            handleXmlResult(staffCallService.handleIncoming(from, to, params.CallSid))
        }
        else if (callService.teamPhoneExistsForNum(to)) {
            handleXmlResult(teamCallService.handleIncoming(from, to, params.CallSid))
        }
        else { //phone not found
            handleXmlResult(twimlBuilder.buildXmlFor(CallResponse.DEST_NOT_FOUND, [num:to]))
        }
    }

    //incoming call to a TextUp phone results in a voicemail
    protected def voicemail(GrailsParameterMap params) {
        if (recordService.receiptExistsForApiId(params.CallSid)) {
            String status = Helpers.translateCallStatus(params.CallStatus)
            Integer callDuration = Helpers.toInteger(params.CallDuration),
                recordingDuration = Helpers.toInteger(params.RecordingDuration)
            handleResultWithStatus(callService.storeVoicemail(params.CallSid, status,
                callDuration, params.RecordingUrl, recordingDuration), OK)
        }
        else { notFound() }
    }

    protected def sendToVoicemail(GrailsParameterMap params) {
        if (recordService.receiptExistsForApiId(params.CallSid)) {
            TransientPhoneNumber to = new TransientPhoneNumber(number:params.To)
            handleXmlResult(callService.playVoicemail(to))
        }
        else { notFound() }
    }

    protected def confirmBridgeCall(GrailsParameterMap params) {
        if (params.long("contactToBridge")) {
            handleXmlResult(callService.confirmBridgeCallForContact(params.long("contactToBridge")))
        }
        else { badRequest() }
    }

    protected def startBridgeCall(GrailsParameterMap params) {
        if (params.long("contactToBridge")) {
            handleXmlResult(callService.completeBridgeCallForContact(params.long("contactToBridge")))
        }
        else { badRequest() }
    }

    ///////////////////
    // Call for Team //
    ///////////////////

    protected def incomingTeamDigitsForCall(GrailsParameterMap params) {
        TransientPhoneNumber from = new TransientPhoneNumber(number:params.From),
                to = new TransientPhoneNumber(number:params.To)
        handleXmlResult(teamCallService.handleIncomingDigits(from, to, params.CallSid))
    }

    protected def startCallAnnouncement(GrailsParameterMap params) {
        if (params.long("teamContactTagId") && params.long("recordTextId")) {
            Long ctId = params.long("teamContactTagId"),
                rtId = params.long("recordTextId")
            Result<Closure> res = teamCallService.completeCallAnnouncement(ctId, rtId)
            if (res.success) { renderAsXml(res.payload) }
            else { handleResultFailure(res) }
        }
        else { badRequest() }
    }

    protected def incomingDigitsForAnnouncement(GrailsParameterMap params) {
        if (params.long("teamContactTagId") && params.long("recordTextId")) {
            TransientPhoneNumber from = new TransientPhoneNumber(number:params.From),
                to = new TransientPhoneNumber(number:params.To)
            Long ctId = params.long("teamContactTagId"), rtId = params.long("recordTextId")
            handleXmlResult(teamCallService.handleAnnouncementDigits(from, to, params.Digits, ctId, rtId))
        }
        else { badRequest() }
    }

    ////////////////////
    // Call for Staff //
    ////////////////////

    protected def incomingDigitsForSelfCall(GrailsParameterMap params) {
        TransientPhoneNumber to = new TransientPhoneNumber(number:params.To)
        handleXmlResult(staffCallService.handleIncomingDigitsFromSelf(params.CallSid, to, params.Digits))
    }
}
