package org.textup

import com.twilio.security.RequestValidator
import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import grails.validation.ValidationErrors
import java.util.concurrent.TimeUnit
import javax.servlet.http.HttpServletRequest
import org.apache.commons.lang3.tuple.Pair
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.codehaus.groovy.grails.web.servlet.mvc.GrailsParameterMap
import org.joda.time.DateTime
import org.springframework.context.MessageSource
import org.textup.rest.TwimlBuilder
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*
import spock.lang.Ignore
import spock.lang.Shared

@TestFor(CallbackService)
@Domain([Contact, Phone, ContactTag, ContactNumber, Record, RecordItem, RecordText,
	RecordCall, RecordItemReceipt, SharedContact, Staff, Team, Organization, Schedule,
	Location, WeeklySchedule, PhoneOwnership, FeaturedAnnouncement, IncomingSession,
	AnnouncementReceipt, Role, StaffRole, NotificationPolicy,
    MediaInfo, MediaElement, MediaElementVersion])
@TestMixin(HibernateTestMixin)
class CallbackServiceSpec extends CustomSpec {

	static doWithSpring = {
		resultFactory(ResultFactory)
	}

	String _authToken = "iamsosososecret!!"

    def setup() {
    	setupData()
    	service.resultFactory = TestHelpers.getResultFactory(grailsApplication)
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

    void "test getting browser URL"() {
        given:
        HttpServletRequest mockRequest = GroovyMock(HttpServletRequest) // to mock forwardURI

        when:
        String result = service.getBrowserURL(mockRequest)

        then:
        1 * mockRequest.requestURL >> new StringBuffer("https://www.example.com/a.html")
        1 * mockRequest.requestURI >> "/a.html"
        1 * mockRequest.forwardURI >> "/b.html"
        2 * mockRequest.queryString >> "test3=bye&"
        result == "https://www.example.com/b.html?test3=bye&"
    }

    void "test validating request from twilio"() {
        given:
        HttpServletRequest mockRequest = GroovyMock(HttpServletRequest) // to mock forwardURI
        RequestValidator allValidators = GroovySpy(RequestValidator,
            constructorArgs: ["valid auth token"], global: true)
        service.grailsApplication = Mock(GrailsApplication)
        GrailsParameterMap allParams = new GrailsParameterMap([:], mockRequest)

        when: "missing auth header"
        Result<Void> res = service.validate(mockRequest, allParams)

        then:
        1 * mockRequest.getHeader(*_) >> null
        res.status == ResultStatus.BAD_REQUEST
        res.errorMessages[0] == "callbackService.validate.invalid"

        when: "invalid"
        res = service.validate(mockRequest, allParams)

        then:
        1 * mockRequest.getHeader("x-twilio-signature") >> "a valid auth header"
        1 * mockRequest.requestURL >> new StringBuffer("https://www.example.com/a.html")
        1 * mockRequest.requestURI
        1 * mockRequest.forwardURI
        (1.._) * mockRequest.queryString
        1 * mockRequest.parameterMap >> [:]
        1 * service.grailsApplication.flatConfig >> ["textup.apiKeys.twilio.authToken": "token"]
        1 * allValidators.validate(*_) >> false
        res.status == ResultStatus.BAD_REQUEST
        res.errorMessages[0] == "callbackService.validate.invalid"

        when: "valid"
        res = service.validate(mockRequest, allParams)

        then:
        1 * mockRequest.getHeader("x-twilio-signature") >> "a valid auth header"
        1 * mockRequest.requestURL >> new StringBuffer("https://www.example.com/a.html")
        1 * mockRequest.requestURI
        1 * mockRequest.forwardURI
        (1.._) * mockRequest.queryString
        1 * mockRequest.parameterMap >> [:]
        1 * service.grailsApplication.flatConfig >> ["textup.apiKeys.twilio.authToken": "token"]
        1 * allValidators.validate(*_) >> true
        res.status == ResultStatus.NO_CONTENT
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
    	params = new GrailsParameterMap([To:p1.numberAsString, From:"1112223333"], request)
		res = service.process(params)

    	then:
    	res.success == false
    	res.status == ResultStatus.BAD_REQUEST
    	res.errorMessages[0] == "callbackService.process.invalid"
    }

    void "test process for non-US numbers"() {
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

    void "test handling sessions"() {
        given:
        PhoneNumber originalFromNumber = new PhoneNumber(number: TestHelpers.randPhoneNumber())
        PhoneNumber fromNumber = new PhoneNumber(number: TestHelpers.randPhoneNumber())
        PhoneNumber toNumber = new PhoneNumber(number: TestHelpers.randPhoneNumber())
        [originalFromNumber, fromNumber, toNumber].each { assert it.validate() }
        HttpServletRequest mockRequest = Mock(HttpServletRequest)
        GrailsParameterMap params = new GrailsParameterMap(
            [originalFrom: originalFromNumber.number], mockRequest)
        int iBaseline = IncomingSession.count()

        when: "CallResponse.FINISH_BRIDGE + new phone number"
        params.handle = CallResponse.FINISH_BRIDGE.toString()
        Result<IncomingSession> res = service.getOrCreateIncomingSession(p1, fromNumber,
            toNumber, params)

        then:
        IncomingSession.count() == iBaseline + 1
        res.status == ResultStatus.OK
        res.payload.numberAsString == toNumber.number

        when: "CallResponse.ANNOUNCEMENT_AND_DIGITS + existing phone number"
        params.handle = CallResponse.ANNOUNCEMENT_AND_DIGITS.toString()
        res = service.getOrCreateIncomingSession(p1, fromNumber, toNumber, params)

        then:
        IncomingSession.count() == iBaseline + 1
        res.status == ResultStatus.OK
        res.payload.numberAsString == toNumber.number

        when: "CallResponse.SCREEN_INCOMING + new phone number"
        params.handle = CallResponse.SCREEN_INCOMING.toString()
        res = service.getOrCreateIncomingSession(p1, fromNumber, toNumber, params)

        then:
        IncomingSession.count() == iBaseline + 2
        res.status == ResultStatus.OK
        res.payload.numberAsString == originalFromNumber.number

        when: "another valid call response + new phone number"
        params.handle = "another valid call response"
        res = service.getOrCreateIncomingSession(p1, fromNumber, toNumber, params)

        then:
        IncomingSession.count() == iBaseline + 3
        res.status == ResultStatus.OK
        res.payload.numberAsString == fromNumber.number
    }

    void "test handling incoming media"() {
        given: "this method assumes numMedia > 0"
        service.mediaService = Mock(MediaService)
        HttpServletRequest mockRequest = Mock(HttpServletRequest)
        GrailsParameterMap params = new GrailsParameterMap([:], mockRequest)
        List<String> urls = []
        int numMedia = 2
        numMedia.times {
            String mockUrl = UUID.randomUUID().toString()
            urls << mockUrl
            params["MediaUrl${it}"] = mockUrl
            params["MediaContentType${it}"] = MediaType.IMAGE_JPEG.mimeType
        }
        Map<String, String> urlToMimeType

        when:
        Result res = service.handleMedia(numMedia, { a1 -> }, { a1 -> }, params)

        then:
        1 * service.mediaService.buildFromIncomingMedia(*_) >> { builtMap, a1, a2 ->
            urlToMimeType = builtMap; new Result();
        }
        res.status == ResultStatus.OK
        urlToMimeType != null
        urls.every { it in urlToMimeType.keySet() }
        urlToMimeType.values().every { it == MediaType.IMAGE_JPEG.mimeType }
    }

    void "test uploading and delete media after relaying text"() {
        given:
        service.mediaService = Mock(MediaService)
        service.socketService = Mock(SocketService)
        service.storageService = Mock(StorageService)
        service.threadService = Mock(ThreadService)

        when: "missing items to upload"
        service.handleMediaAndSocket(null, null, null, null)

        then: "upload items"
        1 * service.storageService.uploadAsync(*_) >> new ResultGroup()

        then: "handle socket and delete if needed afterwards"
        1 * service.socketService.sendItems(*_)
        0 * service.threadService.submit(*_)
        0 * service.mediaService.deleteMedia(*_)

        when: "has items, but has some upload errors"
        service.handleMediaAndSocket(null, [new UploadItem()], [], null)

        then: "upload items"
        1 * service.storageService.uploadAsync(*_) >>
            new ResultGroup([new Result(status: ResultStatus.BAD_REQUEST)])

        then: "handle socket and delete if needed afterwards"
        1 * service.socketService.sendItems(*_)
        0 * service.threadService.submit(*_)
        0 * service.mediaService.deleteMedia(*_)

        when: "has items and all uploads successful"
        service.handleMediaAndSocket(null, [new UploadItem()], [], null)

        then: "upload items"
        1 * service.storageService.uploadAsync(*_) >> new ResultGroup()

        then: "handle socket and delete if needed afterwards"
        1 * service.socketService.sendItems(*_)
        1 * service.threadService.submit(_ as Long, _ as TimeUnit, _ as Closure) >> { args ->
            args[2](); return null;
        }
        1 * service.mediaService.deleteMedia(*_) >> new Result()
    }

    void "test processing text overall"() {
        given:
        service.mediaService = Mock(MediaService)
        service.threadService = Mock(ThreadService)
        Phone mockPhone = Mock(Phone)
        HttpServletRequest mockRequest = Mock(HttpServletRequest)
        GrailsParameterMap params = new GrailsParameterMap([
            Body: "hello",
            NumSegments: 88
        ], mockRequest)

        when: "no media"
        params.NumMedia = 0
        Result res = service.processText(mockPhone, null, "apiId", params)

        then:
        0 * service.mediaService.buildFromIncomingMedia(*_)
        1 * mockPhone.receiveText(*_) >> { args ->
            assert args[0] instanceof IncomingText
            assert args[0].numSegments == params.NumSegments
            new Result(payload: Pair.of({ -> }, []))
        }
        1 * service.threadService.submit(*_)
        res.status == ResultStatus.OK
        res.payload instanceof Closure

        when: "with media"
        params.NumMedia = 5
        res = service.processText(mockPhone, null, "apiId", params)

        then:
        1 * service.mediaService.buildFromIncomingMedia(*_) >> new Result()
        1 * mockPhone.receiveText(*_) >> { args ->
            assert args[0] instanceof IncomingText
            assert args[0].numSegments == params.NumSegments
            new Result(payload: Pair.of({ -> }, []))
        }
        1 * service.threadService.submit(*_)
        res.status == ResultStatus.OK
        res.payload instanceof Closure
    }

    void "test process for incoming calls and voicemail"() {
        given:
        service.threadService = Mock(ThreadService)

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
        0 * service.threadService._
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
        0 * service.threadService._
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
        1 * service.threadService.submit(*_)
        res.status == ResultStatus.OK
        res.payload == CallResponse.VOICEMAIL_DONE

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
