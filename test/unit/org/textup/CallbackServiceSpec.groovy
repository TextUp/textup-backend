package org.textup

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import grails.validation.ValidationErrors
import javax.servlet.http.HttpServletRequest
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.codehaus.groovy.grails.web.servlet.mvc.GrailsParameterMap
import org.joda.time.DateTime
import org.springframework.context.MessageSource
import org.textup.rest.TwimlBuilder
import org.textup.type.CallResponse
import org.textup.type.PhoneOwnershipType
import org.textup.type.ReceiptStatus
import org.textup.type.RecordItemType
import org.textup.type.StaffStatus
import org.textup.type.TextResponse
import org.textup.util.CustomSpec
import org.textup.validator.IncomingText
import org.textup.validator.PhoneNumber
import spock.lang.Ignore
import spock.lang.Shared
import static org.springframework.http.HttpStatus.*

@TestFor(CallbackService)
@Domain([Contact, Phone, ContactTag, ContactNumber, Record, RecordItem, RecordText,
	RecordCall, RecordItemReceipt, SharedContact, Staff, Team, Organization, Schedule,
	Location, WeeklySchedule, PhoneOwnership, FeaturedAnnouncement, IncomingSession,
	AnnouncementReceipt, Role, StaffRole, NotificationPolicy])
@TestMixin(HibernateTestMixin)
class CallbackServiceSpec extends CustomSpec {

	static doWithSpring = {
		resultFactory(ResultFactory)
	}

	String _authToken = "iamsosososecret!!"

    def setup() {
    	setupData()
    	service.resultFactory = getResultFactory()
    	service.twimlBuilder = getTwimlBuilder()
    	service.grailsApplication = [getFlatConfig:{
    		["textup.apiKeys.twilio.authToken":_authToken]
		}] as GrailsApplication
		Phone.metaClass.receiveText = { IncomingText text, IncomingSession session ->
			new Result(status:ResultStatus.OK, payload:"receiveText")
		}
        Phone.metaClass.receiveCall = { String apiId, String digits, IncomingSession session ->
            new Result(status:ResultStatus.OK, payload:"receiveCall")
        }
        Phone.metaClass.screenIncomingCall = { IncomingSession session ->
            new Result(status:ResultStatus.OK, payload:"screenIncomingCall")
        }
		Phone.metaClass.tryStartVoicemail = { PhoneNumber fromNum, PhoneNumber toNum, ReceiptStatus status ->
			new Result(status:ResultStatus.OK, payload:"tryStartVoicemail")
		}
        Phone.metaClass.completeVoicemail = { String callId, String recordingId, String voicemailUrl,
            Integer voicemailDuration ->
            new Result(status:ResultStatus.OK, payload:"completeVoicemail")
        }
		Phone.metaClass.finishBridgeCall = { Contact c1 ->
			new Result(status:ResultStatus.OK, payload:"finishBridgeCall")
		}
		Phone.metaClass.completeCallAnnouncement = { String digits, String message,
        	String identifier, IncomingSession session ->
			new Result(status:ResultStatus.OK, payload:"completeCallAnnouncement")
		}
    }

    def cleanup() {
    	cleanupData()
    }

    protected TwimlBuilder getTwimlBuilder() {
        [build:{ code, params=[:] ->
            new Result(status:ResultStatus.OK, payload:code)
        }, noResponse: { ->
            new Result(status:ResultStatus.OK, payload:"noResponse")
        }, notFoundForText: { ->
        	new Result(status:ResultStatus.OK, payload:"notFoundForText")
    	}, notFoundForCall: { ->
    		new Result(status:ResultStatus.OK, payload:"notFoundForCall")
    	}, invalidNumberForText: { ->
            new Result(status:ResultStatus.OK, payload:"invalidNumberForText")
        }, invalidNumberForCall: { ->
            new Result(status:ResultStatus.OK, payload:"invalidNumberForCall")
        }] as TwimlBuilder
    }

    // Validate
    // --------

    void "test extracting twilio params"() {
    	when:
        addToMessageSource("callbackService.validate.invalid")
    	HttpServletRequest request = [
    		getParameterMap: { [test1:"hello", test2:"bye"] },
    		getQueryString: { "test3=bye&" }
    	] as HttpServletRequest
        request.metaClass.getProperties = { ["forwardURI":""] }
    	GrailsParameterMap allParams = new GrailsParameterMap([test1:"hello",
    		test2:"bye", test3:"kiki"], request)
    	Map<String,String> params = service.extractTwilioParams(request, allParams)

    	then:
    	params.size() == 2
    	params.test1 == allParams.test1
    	params.test2 == allParams.test2
    	params.test3 == null
    }

    void "test validating request from twilio"() {
    	when:
    	String url = "https://www.example.com"
    	String queryString = "test3=bye&"
    	String toMatch = Helpers.getBase64HmacSHA1(
    		"${url}?${queryString}test1hellotest2bye", _authToken)
    	HttpServletRequest request = [
    		getParameterMap: { [test1:"hello", test2:"bye"] },
    		getQueryString: { queryString },
    		getRequestURL: { new StringBuffer(url) },
			getRequestURI: { "" },
			getAttribute: { String n -> "" },
			getHeader: { String n -> toMatch }
    	] as HttpServletRequest
        request.metaClass.forwardURI = ""
    	GrailsParameterMap allParams = new GrailsParameterMap([test1:"hello",
    		test2:"bye", test3:"kiki"], request)
    	Result res = service.validate(request, allParams)

    	then:
    	res.success == true
    }

    // Process
    // -------

    void "test process"() {
    	when: "'to' maps to nonexistent phone"
    	HttpServletRequest request = [:] as HttpServletRequest
        String nonexistentNumber = "8888888888"
        assert Phone.countByNumberAsString(nonexistentNumber) == 0
    	GrailsParameterMap params = new GrailsParameterMap([From: nonexistentNumber, To:nonexistentNumber], request)
    	Result<Closure> res = service.process(params)

    	then:
    	res.success == true
    	res.payload == "notFoundForText"

    	when: "neither messageSid nor callSid specified"
        addToMessageSource("callbackService.process.invalid")
    	params = new GrailsParameterMap([To:p1.numberAsString, From:"1112223333"], request)
		res = service.process(params)

    	then:
    	res.success == false
    	res.status == ResultStatus.BAD_REQUEST
    	res.errorMessages[0] == "callbackService.process.invalid"
    }

    void "test process for invalid (non-US) numbers"() {
        when: "incoming is non-US number for text"
        HttpServletRequest request = [:] as HttpServletRequest
        GrailsParameterMap params = new GrailsParameterMap([To:"blah", From:"invalid", MessageSid:"ok"], request)
        Result<Closure> res = service.process(params)

        then:
        res.success == true
        res.payload == "invalidNumberForText"

        when: "incoming is non-US number for call"
        params = new GrailsParameterMap([To: "blah", From:"invalid", CallSid:"ok"], request)
        res = service.process(params)

        then:
        res.success == true
        res.payload == "invalidNumberForCall"
    }

    void "test process for utility call responses"() {
        when: "retrieving a outgoing direct message delivered through call"
        HttpServletRequest request = [:] as HttpServletRequest
        GrailsParameterMap params = new GrailsParameterMap(
            [handle:CallResponse.DIRECT_MESSAGE.toString()], request)
        Result<Closure> res = service.process(params)

        then:
        res.status == ResultStatus.OK
        res.payload == CallResponse.DIRECT_MESSAGE

        when: "no-op"
        params = new GrailsParameterMap([handle:CallResponse.DO_NOTHING.toString()], request)
        res = service.process(params)

        then:
        res.status == ResultStatus.OK
        res.payload == CallResponse.DO_NOTHING

        when: "hanging up"
        params = new GrailsParameterMap([handle:CallResponse.END_CALL.toString()], request)
        res = service.process(params)

        then:
        res.status == ResultStatus.OK
        res.payload == CallResponse.END_CALL
    }

    void "test process for texts"() {
    	given:
    	int iBaseline = IncomingSession.count()

    	when: "receive text for nonexistent session"
    	HttpServletRequest request = [:] as HttpServletRequest
    	String fromNum = "1233834920"
    	GrailsParameterMap params = new GrailsParameterMap([To:p1.numberAsString,
    		From:fromNum, MessageSid:"iamasid!!", Body:"u r awsum"], request)
    	assert IncomingSession.findByNumberAsString(fromNum) == null
    	Result<Closure> res = service.process(params)

    	then: "new session is created"
		IncomingSession.count() == iBaseline + 1
		res.success == true
		res.payload == "receiveText"

   		when: "receive for existing session"
   		res = service.process(params)

   		then: "no duplicate session is created"
   		IncomingSession.count() == iBaseline + 1
		res.success == true
        res.status == ResultStatus.OK
		res.payload == "receiveText"
    }

    void "test process for incoming calls and voicemail"() {
        when: "starting voicemail"
        HttpServletRequest request = [:] as HttpServletRequest
        String clientNum = "1233834920"
        GrailsParameterMap params = new GrailsParameterMap([CallSid:"iamasid!!",
            handle:CallResponse.SCREEN_INCOMING.toString()],
            request)
        // voicemail is inbound so from client to TextUp phone
        // but we use a relayed call to allow for screening so we store the
        // originalFrom and use the From of the second bridged call to keep track
        //  of the "from" client and the "to" TextUp phone number
        params.originalFrom = clientNum
        params.From = p1.numberAsString
        params.To = "1112223333"
        Result<Closure> res = service.process(params)

        then:
        res.success == true
        res.status == ResultStatus.OK
        res.payload == "screenIncomingCall"

        when: "in the status callback, check to see if the call was answered and if voicemail should start"
        params.From = clientNum
        params.To = p1.numberAsString
        params.DialCallStatus = "in-progress"
        params.handle = CallResponse.CHECK_IF_VOICEMAIL.toString()
        res = service.process(params)

        then:
        res.success == true
        res.status == ResultStatus.OK
        res.payload == "tryStartVoicemail"

        when: "completing voicemail"
        params.handle = CallResponse.VOICEMAIL_DONE.toString()
        params.RecordingSid = "recording id"
        params.RecordingDuration = 88
        params.RecordingUrl = "https://www.example.com"
        res = service.process(params)

        then:
        res.success == true
        res.status == ResultStatus.OK
        res.payload == "completeVoicemail"

        when: "unspecified or invalid"
        params.handle = "blahblahinvalid"
        res = service.process(params)

        then: "receive call"
        res.success == true
        res.status == ResultStatus.OK
        res.payload == "receiveCall"
    }

    void "test process for outbound calls"() {
    	when: "voicemail"
    	HttpServletRequest request = [:] as HttpServletRequest
    	String clientNum = "1233834920"
    	GrailsParameterMap params = new GrailsParameterMap([CallSid:"iamasid!!",
            handle:CallResponse.FINISH_BRIDGE.toString()],
    		request)
        // outbound so from TextUp phone to client
        params.From = p1.numberAsString
        params.To = clientNum
    	Result<Closure> res = service.process(params)

    	then:
    	res.success == true
        res.status == ResultStatus.OK
		res.payload == "finishBridgeCall"

		when: "announcement and digits"
		params.handle = CallResponse.ANNOUNCEMENT_AND_DIGITS.toString()
		res = service.process(params)

		then:
		res.success == true
        res.status == ResultStatus.OK
		res.payload == "completeCallAnnouncement"
    }
}
