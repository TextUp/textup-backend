package org.textup

import com.twilio.security.RequestValidator
import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import groovy.transform.TypeCheckingMode
import javax.servlet.http.HttpServletRequest
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.codehaus.groovy.grails.web.servlet.mvc.GrailsParameterMap
import org.textup.rest.TwimlBuilder
import org.textup.type.CallResponse
import org.textup.type.ReceiptStatus
import org.textup.util.OptimisticLockingRetry
import org.textup.util.RollbackOnResultFailure
import org.textup.validator.BasePhoneNumber
import org.textup.validator.IncomingText
import org.textup.validator.PhoneNumber

@GrailsTypeChecked
@Transactional
class CallbackService {

    ResultFactory resultFactory
    TwimlBuilder twimlBuilder
    GrailsApplication grailsApplication
    MediaService mediaService

	// Validate request
	// ----------------

    Result<Void> validate(HttpServletRequest request, GrailsParameterMap params) {
        // step 1: try to extract auth header
        String errCode = "callbackService.validate.invalid",
            authHeader = request.getHeader("x-twilio-signature")
        if (!authHeader) {
            return resultFactory.failWithCodeAndStatus(errCode, ResultStatus.BAD_REQUEST)
        }
        // step 2: build browser url and extract Twilio params
        String url = getBrowserURL(request)
        Map<String, String> twilioParams = extractTwilioParams(request, params)
        // step 3: build and run request validator
        String authToken = grailsApplication.flatConfig["textup.apiKeys.twilio.authToken"]
        RequestValidator validator = new RequestValidator(authToken)
        validator.validate(url, twilioParams, authHeader) ?
            resultFactory.success() :
            resultFactory.failWithCodeAndStatus(errCode, ResultStatus.BAD_REQUEST)
    }

    protected String getBrowserURL(HttpServletRequest request) {
        String browserURL = (request.requestURL.toString() - request.requestURI) + getForwardURI(request)
        request.queryString ? "$browserURL?${request.queryString}" : browserURL
    }
    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    protected String getForwardURI(HttpServletRequest request) {
        request.getForwardURI()
    }

    protected Map<String,String> extractTwilioParams(HttpServletRequest request,
    	GrailsParameterMap allParams) {
        // step 1: build list of what to ignore. Params that should be ignored are query params
        // that we append to the callback functions that should be factored into the validation
        Collection<String> requestParamKeys = request.parameterMap.keySet(),
            queryParams = []
        request.queryString?.tokenize("&")?.each { queryParams << it.tokenize("=")[0] }
        HashSet<String> ignoreParamKeys = new HashSet<>(queryParams),
            keepParamKeys = new HashSet<>(requestParamKeys)
        // step 2: collect params
        Map<String,String> twilioParams = [:]
        allParams.each {
            String key = it.key, val = it.value
            if (keepParamKeys.contains(key) && !ignoreParamKeys.contains(key)) {
                twilioParams[key] = val
            }
        }
        twilioParams
    }

    // Process request
    // ---------------

    @OptimisticLockingRetry
    @RollbackOnResultFailure
    Result<Closure> process(GrailsParameterMap params) {
        // if a call direct messaage or other direct actions that we do
        // not need to do further processing for, handle right here
        if (params.handle == CallResponse.DIRECT_MESSAGE.toString()) {
            return twimlBuilder.build(CallResponse.DIRECT_MESSAGE, params)
        }
        else if (params.handle == CallResponse.DO_NOTHING.toString()) {
            return twimlBuilder.build(CallResponse.DO_NOTHING)
        }
        else if (params.handle == CallResponse.END_CALL.toString()) {
            return twimlBuilder.build(CallResponse.END_CALL)
        }
        else { processForNumbers(params) }
    }

    protected Result<Closure> processForNumbers(GrailsParameterMap params) {
        // step 1: check that both to and from numbers are valid US phone numbers
        PhoneNumber fromNum = new PhoneNumber(number:params.From as String),
            toNum = new PhoneNumber(number:params.To as String)
        if (!fromNum.validate() || !toNum.validate()) {
            return params.CallSid ? twimlBuilder.invalidNumberForCall() : twimlBuilder.invalidNumberForText()
        }
        // step 2: get phone
        BasePhoneNumber pNum = getNumberForPhone(fromNum, toNum, params)
        Phone p1 = Phone.findByNumberAsString(pNum.number)
        if (!p1) {
            return params.CallSid ? twimlBuilder.notFoundForCall() : twimlBuilder.notFoundForText()
        }
        // step 3: get session
        Result<IncomingSession> iRes = getOrCreateIncomingSession(p1, fromNum, toNum, params)
        IncomingSession is1
        if (iRes.success) {is1 = iRes.payload }
        else { return iRes }
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

    protected Result<IncomingSession> getOrCreateIncomingSession(Phone p1, BasePhoneNumber fromNum,
        BasePhoneNumber toNum, GrailsParameterMap params) {

        //usually handle incoming from session (client) to phone (staff)
        BasePhoneNumber sessionNum = fromNum
        // finish bridge is call from phone to personal phone
        // announcements are from phone to session (client)
        if (params.handle == CallResponse.FINISH_BRIDGE.toString() ||
            params.handle == CallResponse.ANNOUNCEMENT_AND_DIGITS.toString()) {
            sessionNum = toNum
        }
        // when screening incoming calls, the From number is the TextUp phone,
        // the original caller is stored in the originalFrom parameter and the
        // To number is actually the staff member's personal phone number
        else if (params.handle == CallResponse.SCREEN_INCOMING.toString()) {
            sessionNum = new PhoneNumber(number:params.originalFrom as String)
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
        GrailsParameterMap params) {

        //usually handle incoming from session (client) to phone (staff)
        BasePhoneNumber phoneNum = toNum
        // finish bridge is call from phone to personal phone
        // announcements are from phone to session (client)
        if (params.handle == CallResponse.FINISH_BRIDGE.toString() ||
            params.handle == CallResponse.ANNOUNCEMENT_AND_DIGITS.toString()) {
            phoneNum = fromNum
        }
        // when screening incoming calls, the From number is the TextUp phone,
        // the original caller is stored in the originalFrom parameter and the
        // To number is actually the staff member's personal phone number
        else if (params.handle == CallResponse.SCREEN_INCOMING.toString()) {
            phoneNum = fromNum
        }
        phoneNum
    }

    protected Result<Closure> processCall(Phone p1, IncomingSession is1, String apiId,
        GrailsParameterMap params) {

        String digits = params.Digits
        switch(params.handle) {
            case CallResponse.SCREEN_INCOMING.toString():
                p1.screenIncomingCall(is1)
                break
            case CallResponse.CHECK_IF_VOICEMAIL.toString():
                ReceiptStatus rStatus = ReceiptStatus.translate(params.DialCallStatus as String)
                p1.tryStartVoicemail(is1.number, p1.number, rStatus)
                break
            case CallResponse.VOICEMAIL_DONE.toString():
                Integer voicemailDuration = Helpers.to(Integer, params.RecordingDuration)
                String callId = Helpers.to(String, params.CallSid),
                    recordingId = Helpers.to(String, params.RecordingSid),
                    voicemailUrl = Helpers.to(String, params.RecordingUrl)
                p1.completeVoicemail(callId, recordingId, voicemailUrl, voicemailDuration)
                break
            case CallResponse.FINISH_BRIDGE.toString():
                Contact c1 = Contact.get(params.long("contactId"))
                p1.finishBridgeCall(c1)
                break
            case CallResponse.ANNOUNCEMENT_AND_DIGITS.toString():
                String msg = params.message,
                    ident = params.identifier
                p1.completeCallAnnouncement(digits, msg, ident, is1)
                break
            default:
                p1.receiveCall(apiId, digits, is1)
        }
    }

    protected Result<Closure> processText(Phone p1, IncomingSession is1, String apiId,
        GrailsParameterMap params) {

        // step 1: handle media, if applicable
        MediaInfo mInfo
        Set<String> mediaIdsToDelete = new HashSet<>()
        Integer numMedia = Helpers.to(Integer, params.NumMedia)
        if (numMedia > 0) {
            Map<String, String> urlToMimeType = [:]
            for (int i = 0; i < numMedia; ++i) {
                String contentUrl = params["MediaUrl${i}"],
                    contentType = params["MediaContentType${i}"]
                urlToMimeType[contentUrl] = contentType
            }
            Result<MediaInfo> mediaRes = mediaService
                .buildFromIncomingMedia(urlToMimeType, mediaIdsToDelete.&add)
                .logFail("CallbackService.process: building incoming media")
            if (mediaRes.success) {
                mInfo = mediaRes.payload
            }
            else { return mediaRes }
        }
        // step 2: relay text
        IncomingText text = new IncomingText(apiId:apiId, message:params.Body as String)
        if (text.validate()) { //validate text
            Result<Closure> res = p1.receiveText(text, is1, mInfo)
            if (res.success && mediaIdsToDelete) {
                mediaService.deleteMedia(apiId, mediaIdsToDelete)
                    .logFail("CallbackService.processText: deleting media")
            }
            res
        }
        else { resultFactory.failWithValidationErrors(text.errors) }
    }
}
