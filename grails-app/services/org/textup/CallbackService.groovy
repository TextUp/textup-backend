package org.textup

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import javax.servlet.http.HttpServletRequest
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.codehaus.groovy.grails.web.servlet.mvc.GrailsParameterMap
import org.textup.rest.TwimlBuilder
import org.textup.type.CallResponse
import org.textup.type.ReceiptStatus
import org.textup.util.OptimisticLockingRetry
import org.textup.validator.IncomingText
import org.textup.validator.PhoneNumber

@GrailsTypeChecked
@Transactional
class CallbackService {

    GrailsApplication grailsApplication
	ResultFactory resultFactory
	TwimlBuilder twimlBuilder

	// Validate request
	// ----------------

    Result<Void> validate(HttpServletRequest request, GrailsParameterMap params) {
        String browserURL = (request.requestURL.toString() - request.requestURI) + request.properties.forwardURI,
            authToken = grailsApplication.flatConfig["textup.apiKeys.twilio.authToken"],
            authHeaderName = "x-twilio-signature",
            authHeader = request.getHeader(authHeaderName)
        Result invalidResult = resultFactory.failWithCodeAndStatus("callbackService.validate.invalid",
            ResultStatus.BAD_REQUEST)
        if (!authHeader) {
        	return invalidResult
        }
        if (request.queryString) { browserURL = "$browserURL?${request.queryString}" }
        Map<String,String> twilioParams = this.extractTwilioParams(request, params)
        StringBuilder data = new StringBuilder(browserURL)
        //sort keys lexicographically
        List<String> sortedKeys = twilioParams.keySet().toList().sort()
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
            log.error("CallbackService.authenticateRequest: invalid data: $dataString")
            return invalidResult
        }
        //first try https
        String encoded = Helpers.getBase64HmacSHA1(httpsData.toString(), authToken)
        boolean isAuth = (encoded == authHeader)
        //then fallback to checking http if needed
        if (!isAuth) {
            encoded = Helpers.getBase64HmacSHA1(httpData.toString(), authToken)
            isAuth = (encoded == authHeader)
        }
        isAuth ? resultFactory.success() : invalidResult
    }

    protected Map<String,String> extractTwilioParams(HttpServletRequest request,
    	GrailsParameterMap allParams) {
        Collection<String> requestParamKeys = request.parameterMap.keySet(), queryParams = []
        request.queryString?.tokenize("&")?.each { queryParams << it.tokenize("=")[0] }
        HashSet<String> ignoreParamKeys = new HashSet<>(queryParams),
            keepParamKeys = new HashSet<>(requestParamKeys)
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
        // otherwise, continue handling other possibilities
    	String apiId = params.CallSid ?: params.MessageSid,
    		digits = params.Digits
    	PhoneNumber fromNum = new PhoneNumber(number:params.From as String),
    		toNum = new PhoneNumber(number:params.To as String),
            //usually handle incoming from session (client) to phone (staff)
            phoneNum = toNum,
            sessionNum = fromNum
        // check that both to and from numbers are valid US phone numbers
        if (!fromNum.validate() || !toNum.validate()) {
            return params.CallSid ? twimlBuilder.invalidNumberForCall() :
                twimlBuilder.invalidNumberForText()
        }
        // finish bridge is call from phone to personal phone
        // announcements are from phone to session (client)
        if (params.handle == CallResponse.FINISH_BRIDGE.toString() ||
            params.handle == CallResponse.ANNOUNCEMENT_AND_DIGITS.toString()) {
            phoneNum = fromNum
            sessionNum = toNum
        }
        // when screening incoming calls, the From number is the TextUp phone,
        // the original caller is stored in the originalFrom parameter and the
        // To number is actually the staff member's personal phone number
        else if (params.handle == CallResponse.SCREEN_INCOMING.toString()) {
            phoneNum = fromNum
            sessionNum = new PhoneNumber(number:params.originalFrom as String)
        }
    	Phone phone = Phone.findByNumberAsString(phoneNum.number)
    	if (!phone) {
    		return params.CallSid ? twimlBuilder.notFoundForCall() :
    			twimlBuilder.notFoundForText()
    	}
    	IncomingSession session = IncomingSession.findByPhoneAndNumberAsString(phone,
    		sessionNum.number)
    	// create session for this phone if one doesn't exist yet
    	if (!session) {
    		session = new IncomingSession(phone:phone, numberAsString:sessionNum.number)
    		if (!session.save()) {
    			return resultFactory.failWithValidationErrors(session.errors)
    		}
    	}
    	// process request
    	if (params.CallSid) {
            switch(params.handle) {
                case CallResponse.SCREEN_INCOMING.toString():
                    phone.screenIncomingCall(session)
                    break
                case CallResponse.CHECK_IF_VOICEMAIL.toString():
                    ReceiptStatus rStatus = ReceiptStatus.translate(params.DialCallStatus as String)
                    phone.tryStartVoicemail(sessionNum, phoneNum, rStatus)
                    break
                case CallResponse.VOICEMAIL_DONE.toString():
                    Integer voicemailDuration = Helpers.to(Integer, params.RecordingDuration)
                    String callId = Helpers.to(String, params.CallSid),
                        recordingId = Helpers.to(String, params.RecordingSid),
                        voicemailUrl = Helpers.to(String, params.RecordingUrl)
                    phone.completeVoicemail(callId, recordingId, voicemailUrl, voicemailDuration)
                    break
                case CallResponse.FINISH_BRIDGE.toString():
                	Contact c1 = Contact.get(params.long("contactId"))
                    phone.finishBridgeCall(c1)
                    break
                case CallResponse.ANNOUNCEMENT_AND_DIGITS.toString():
                    String msg = params.message,
                        ident = params.identifier
                    phone.completeCallAnnouncement(digits, msg, ident, session)
                    break
                default:
                    phone.receiveCall(apiId, digits, session)
            }
        }
        else if (params.MessageSid) {
        	IncomingText text = new IncomingText(apiId:apiId, message:params.Body as String)
            phone.receiveText(text, session)
        }
        else {
        	resultFactory.failWithCodeAndStatus("callbackService.process.invalid",
                ResultStatus.BAD_REQUEST)
        }
    }
}
