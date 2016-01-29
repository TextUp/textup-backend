package org.textup

import grails.transaction.Transactional
import javax.servlet.http.HttpServletRequest
import org.codehaus.groovy.grails.web.servlet.mvc.GrailsParameterMap

@Transactional
class CallbackService {

	def resultFactory
	def twimlBuilder

	// Validate request
	// ----------------

    Result validate(HttpServletRequest request, GrailsParameterMap params) {
        String browserURL = (request.requestURL.toString() - request.requestURI) +
        		request.forwardURI,
            authToken = grailsApplication.config.textup.apiKeys.twilio.authToken,
            authHeaderName = "x-twilio-signature",
            authHeader = request.getHeader(authHeaderName)
        Result invalidResult = resultFactory.failWithMessageAndStatus(BAD_REQUEST,
    		"callbackService.validate.invalid")
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
        allParams.each { String key, String val ->
            if (keepParamKeys.contains(key) && !ignoreParamKeys.contains(key)) {
                twilioParams[key] = val
            }
        }
        twilioParams
    }

    // Process request
    // ---------------

    Result<Closure> process(GrailsParameterMap params) {
    	String apiId = params.CallSid ?: params.MessageSid,
    		digits = params.Digits
    	PhoneNumber fromNum = new PhoneNumber(number:params.From),
    		toNum = new PhoneNumber(number:params.To)
    	Phone phone = Phone.findByNumberAsString(toNum.number)
    	if (!phone) {
    		return params.CallSid ? twimlBuilder.notFoundForCall() :
    			twimlBuilder.notFoundForText()
    	}
    	IncomingSession session = IncomingSession.findByPhoneAndNumberAsString(phone,
    		fromNum.number)
    	// create session for this phone if one doesn't exist yet
    	if (!session) {
    		session = new IncomingSession(phone:phone, numberAsString:fromNum.number)
    		if (!session.save()) {
    			return resultFactory.failWithValidationErrors(session.errors)
    		}
    	}
    	// process request
    	if (params.CallSid) {
            switch(params.handle) {
                case CallResponse.VOICEMAIL.toString():
                	Integer voicemailDuration = Helpers.toInteger(params.RecordingDuration)
                    phone.receiveVoicemail(apiId, voicemailDuration, session)
                    break
                case CallResponse.CONFIRM_BRIDGE.toString():
                	Contact c1 = Contact.get(params.contactId)
                    phone.confirmBridgeCall(c1)
                    break
                case CallResponse.FINISH_BRIDGE.toString():
                	Contact c1 = Contact.get(params.contactId)
                    phone.finishBridgeCall(c1)
                    break
                case CallResponse.ANNOUNCEMENT_AND_DIGITS.toString():
                    phone.completeCallAnnouncement(digits, params.message,
                    	params.identifier, session)
                    break
                default:
                    phone.receiveCall(apiId, digits, session)
            }
        }
        else if (params.MessageSid) {
        	IncomingText text = new IncomingText(apiId:apiId, message:params.Body)
            phone.receiveText(text, session)
        }
        else {
        	resultFactory.failWithMessageAndStatus(BAD_REQUEST, "callbackService.process.invalid")
        }
    }
}
