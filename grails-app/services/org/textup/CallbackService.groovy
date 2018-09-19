package org.textup

import com.twilio.security.RequestValidator
import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import groovy.transform.TypeCheckingMode
import java.util.concurrent.TimeUnit
import javax.servlet.http.HttpServletRequest
import org.apache.commons.lang3.tuple.Pair
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.codehaus.groovy.grails.web.servlet.mvc.GrailsParameterMap
import org.textup.rest.TwimlBuilder
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*

@GrailsTypeChecked
@Transactional
class CallbackService {

    GrailsApplication grailsApplication
    MediaService mediaService
    ResultFactory resultFactory
    SocketService socketService
    StorageService storageService
    ThreadService threadService
    TwimlBuilder twimlBuilder

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
                // in about 5% of cases, when the RecordingStatusCallback is called, the recording
                // at the provided url still isn't ready and a request to that url returns a
                // NOT_FOUND. Therefore, we wait a few seconds to ensure that the voicemail
                // is completely done being stored before attempting to fetch it.
                threadService.submit {
                    TimeUnit.SECONDS.sleep(5)
                    RecordCall.withNewTransaction {
                        p1.completeVoicemail(callId, recordingId, voicemailUrl, voicemailDuration)
                            .logFail("CallbackService.processCall: VOICEMAIL_DONE voicemailUrl: ${voicemailUrl}")
                    }
                }
                twimlBuilder.build(CallResponse.VOICEMAIL_DONE)
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
        Collection<UploadItem> itemsToUpload = []
        Set<String> mediaIdsToDelete = new HashSet<>()
        Integer numMedia = Helpers.to(Integer, params.NumMedia)
        if (numMedia > 0) {
            Result<MediaInfo> mediaRes = handleMedia(numMedia, itemsToUpload.&addAll,
                mediaIdsToDelete.&add, params)
            if (mediaRes.success) {
                mInfo = mediaRes.payload
            }
            else { return mediaRes }
        }
        // step 2: relay text
        IncomingText text = new IncomingText(apiId:apiId, message:params.Body as String,
            numSegments: Helpers.to(Integer, params.NumSegments))
        if (text.validate()) { //validate text
            p1
                .receiveText(text, is1, mInfo)
                .then { Pair<Closure, List<RecordText>> payload ->
                    // don't want the twilio request to time out so we first return a response
                    // to twilio and then deal with remaining more time-intensive tasks
                    threadService.submit {
                        handleMediaAndSocket(apiId, itemsToUpload, payload.right, mediaIdsToDelete)
                    }
                    resultFactory.success(payload.left)
                }
        }
        else { resultFactory.failWithValidationErrors(text.errors) }
    }
    protected Result<MediaInfo> handleMedia(int numMedia, Closure<Void> collectUploads,
        Closure<Void> collectMediaIds, GrailsParameterMap params) {

        Map<String, String> urlToMimeType = [:]
        for (int i = 0; i < numMedia; ++i) {
            String contentUrl = params["MediaUrl${i}"],
                contentType = params["MediaContentType${i}"]
            urlToMimeType[contentUrl] = contentType
        }
        mediaService
            .buildFromIncomingMedia(urlToMimeType, collectUploads, collectMediaIds)
            .logFail("CallbackService.handleMedia: building incoming media")
    }
    protected void handleMediaAndSocket(String apiId, Collection<UploadItem> itemsToUpload,
        List<RecordText> textsToSendThroughSocket, Set<String> mediaIdsToDelete) {

        // step 1: upload our processed copies
        ResultGroup<?> resGroup = storageService.uploadAsync(itemsToUpload)
            .logFail("CallbackService.handleMediaAndSocket: uploading processed media")

        // step 2: after uploading, send texts to frontend
        // For outgoing messages and all calls, we rely on status callbacks
        // to push record items to the frontend. However, for incoming texts
        // no status callback happens so we need to push the item here
        socketService.sendItems(textsToSendThroughSocket)

        // step 3: delete media only if no upload errors after a delay
        // need a delay here because try to delete immediately results in an error saying
        // that the incoming message hasn't finished delivering yet. For incoming message
        // no status callbacks so we have to wait a fixed period of time then attempt to delete
        if (itemsToUpload && !resGroup.anyFailures) {
            TimeUnit.SECONDS.sleep(20)
            mediaService.deleteMedia(apiId, mediaIdsToDelete)
                .logFail("CallbackService.handleMediaAndSocket: deleting media")
        }
    }
}
