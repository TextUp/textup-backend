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
import org.textup.types.CallResponse
import org.textup.types.PhoneOwnershipType
import org.textup.types.RecordItemType
import org.textup.types.ResultType
import org.textup.types.StaffStatus
import org.textup.types.TextResponse
import org.textup.util.CustomSpec
import org.textup.validator.IncomingText
import spock.lang.Ignore
import spock.lang.Shared
import static org.springframework.http.HttpStatus.*

@TestFor(CallbackService)
@Domain([Contact, Phone, ContactTag, ContactNumber, Record, RecordItem, RecordText,
	RecordCall, RecordItemReceipt, SharedContact, Staff, Team, Organization, Schedule,
	Location, WeeklySchedule, PhoneOwnership, FeaturedAnnouncement, IncomingSession,
	AnnouncementReceipt, Role, StaffRole])
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
			new Result(type:ResultType.SUCCESS, success:true, payload:"receiveText")
		}
		Phone.metaClass.receiveVoicemail = { String apiId, Integer voicemailDuration,
        	IncomingSession session ->
			new Result(type:ResultType.SUCCESS, success:true, payload:"receiveVoicemail")
		}
		Phone.metaClass.confirmBridgeCall = { Contact c1 ->
			new Result(type:ResultType.SUCCESS, success:true, payload:"confirmBridgeCall")
		}
		Phone.metaClass.finishBridgeCall = { Contact c1 ->
			new Result(type:ResultType.SUCCESS, success:true, payload:"finishBridgeCall")
		}
		Phone.metaClass.completeCallAnnouncement = { String digits, String message,
        	String identifier, IncomingSession session ->
			new Result(type:ResultType.SUCCESS, success:true, payload:"completeCallAnnouncement")
		}
		Phone.metaClass.receiveCall = { String apiId, String digits, IncomingSession session ->
			new Result(type:ResultType.SUCCESS, success:true, payload:"receiveCall")
		}
    }

    def cleanup() {
    	cleanupData()
    }

    protected TwimlBuilder getTwimlBuilder() {
        [build:{ code, params=[:] ->
            new Result(type:ResultType.SUCCESS, success:true, payload:code)
        }, noResponse: { ->
            new Result(type:ResultType.SUCCESS, success:true, payload:"noResponse")
        }, notFoundForText: { ->
        	new Result(type:ResultType.SUCCESS, success:true, payload:"notFoundForText")
    	}, notFoundForCall: { ->
    		new Result(type:ResultType.SUCCESS, success:true, payload:"notFoundForCall")
    	}] as TwimlBuilder
    }

    // Validate
    // --------

    void "test extracting twilio params"() {
    	when:
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
        request.metaClass.getProperties = { ["forwardURI":""] }
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
    	GrailsParameterMap params = new GrailsParameterMap([To:"invalid"], request)
    	Result<Closure> res = service.process(params)

    	then:
    	res.success == true
    	res.payload == "notFoundForText"

    	when: "neither messageSid nor callSid specified"
    	params = new GrailsParameterMap([To:p1.numberAsString, From:"1112223333"], request)
		res = service.process(params)

    	then:
    	res.success == false
    	res.type == ResultType.MESSAGE_STATUS
    	res.payload.status == BAD_REQUEST
    	res.payload.code == "callbackService.process.invalid"
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
		res.payload == "receiveText"
    }

    void "test process for calls"() {
    	when: "voicemail"
    	HttpServletRequest request = [:] as HttpServletRequest
    	String fromNum = "1233834920"
    	GrailsParameterMap params = new GrailsParameterMap([To:p1.numberAsString,
    		From:fromNum, CallSid:"iamasid!!", handle:CallResponse.VOICEMAIL.toString()],
    		request)
    	Result<Closure> res = service.process(params)

    	then:
    	res.success == true
		res.payload == "receiveVoicemail"

    	when: "confirm bridge"
    	params.handle = CallResponse.CONFIRM_BRIDGE.toString()
		res = service.process(params)

    	then:
    	res.success == true
		res.payload == "confirmBridgeCall"

    	when: "finish bridge"
    	params.handle = CallResponse.FINISH_BRIDGE.toString()
		res = service.process(params)

    	then:
    	res.success == true
		res.payload == "finishBridgeCall"

		when: "announcement and digits"
		params.handle = CallResponse.ANNOUNCEMENT_AND_DIGITS.toString()
		res = service.process(params)

		then:
		res.success == true
		res.payload == "completeCallAnnouncement"

		when: "unspecified or invalid"
		params.handle = "blahblahinvalid"
		res = service.process(params)

		then: "receive call"
		res.success == true
		res.payload == "receiveCall"
    }
}
