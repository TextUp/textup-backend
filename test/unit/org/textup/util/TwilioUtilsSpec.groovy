package org.textup.util

import com.twilio.security.RequestValidator
import grails.test.mixin.support.GrailsUnitTestMixin
import grails.test.mixin.TestFor
import grails.test.mixin.TestMixin
import grails.test.runtime.DirtiesRuntime
import grails.util.Holders
import javax.servlet.http.HttpServletRequest
import org.codehaus.groovy.grails.web.util.TypeConvertingMap
import org.joda.time.DateTime
import org.textup.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*
import spock.lang.*
import spock.util.mop.ConfineMetaClassChanges

@TestFor(CustomAccountDetails)
@TestMixin(GrailsUnitTestMixin)
class TwilioUtilsSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    // Validating Twilio request
    // -------------------------

    void "test extracting twilio params"() {
        when:
        HttpServletRequest request = [
            getParameterMap: { [test1:"hello", test2:"bye"] },
            getQueryString: { "test3=bye&" }
        ] as HttpServletRequest
        request.metaClass.getProperties = { ["forwardURI":""] }
        TypeConvertingMap allParams = new TypeConvertingMap([test1:"hello", test2:"bye", test3:"kiki"])
        Map<String,String> params = TwilioUtils.extractTwilioParams(request, allParams)

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
        String result = TwilioUtils.getBrowserURL(mockRequest)

        then:
        1 * mockRequest.requestURL >> new StringBuffer("https://www.example.com/a.html")
        1 * mockRequest.requestURI >> "/a.html"
        1 * mockRequest.forwardURI >> "/b.html"
        2 * mockRequest.queryString >> "test3=bye&"
        result == "https://www.example.com/b.html?test3=bye&"
    }

    @DirtiesRuntime
    void "test getting auth token for varying accounts"() {
        given:
        String masterId = TestUtils.randString()
        String masterToken = TestUtils.randString()
        String subId = TestUtils.randString()
        CustomAccountDetails cad1 = GroovyStub() { getAuthToken() >> TestUtils.randString() }

        TestUtils.mock(Holders, "getFlatConfig") {
            ["textup.apiKeys.twilio.sid": masterId, "textup.apiKeys.twilio.authToken": masterToken]
        }

        when: "master account"
        String result = TwilioUtils.getAuthToken(masterId)

        then:
        result == masterToken

        when: "valid subaccount"
        CustomAccountDetails.metaClass."static".findByAccountId = { String sid, Map opts -> cad1 }
        result = TwilioUtils.getAuthToken(subId)

        then:
        result == cad1.authToken

        when: "invalid account id"
        CustomAccountDetails.metaClass."static".findByAccountId = { String sid, Map opts -> null }
        result = TwilioUtils.getAuthToken(null)

        then:
        result == ""
    }

    // see global mock cleanup bug: https://github.com/spockframework/spock/issues/445
    @ConfineMetaClassChanges([RequestValidator])
    @DirtiesRuntime
    void "test validating request from twilio"() {
        given:
        IOCUtils.metaClass."static".getMessageSource = { -> TestUtils.mockMessageSource() }
        HttpServletRequest mockRequest = GroovyMock(HttpServletRequest) // to mock forwardURI

        String authToken = TestUtils.randString()
        MockedMethod getAuthToken = TestUtils.mock(TwilioUtils, "getAuthToken") { authToken }
        GroovyMock(RequestValidator, global: true)

        TypeConvertingMap allParams = new TypeConvertingMap([:])

        when: "missing auth header"
        Result<Void> res = TwilioUtils.validate(mockRequest, allParams)

        then:
        1 * mockRequest.getHeader(*_) >> null
        res.status == ResultStatus.BAD_REQUEST
        res.errorMessages[0] == "twilioUtils.validate.invalid"

        when: "invalid"
        res = TwilioUtils.validate(mockRequest, allParams)

        then:
        1 * mockRequest.getHeader("x-twilio-signature") >> "a valid auth header"
        1 * mockRequest.requestURL >> new StringBuffer("https://www.example.com/a.html")
        1 * mockRequest.requestURI
        1 * mockRequest.forwardURI
        (1.._) * mockRequest.queryString
        1 * mockRequest.parameterMap >> [:]

        getAuthToken.callCount == 1
        1 * new RequestValidator(authToken) >> Stub(RequestValidator) { validate(*_) >> false }

        res.status == ResultStatus.BAD_REQUEST
        res.errorMessages[0] == "twilioUtils.validate.invalid"

        when: "valid"
        res = TwilioUtils.validate(mockRequest, allParams)

        then:
        1 * mockRequest.getHeader("x-twilio-signature") >> "a valid auth header"
        1 * mockRequest.requestURL >> new StringBuffer("https://www.example.com/a.html")
        1 * mockRequest.requestURI
        1 * mockRequest.forwardURI
        (1.._) * mockRequest.queryString
        1 * mockRequest.parameterMap >> [:]

        getAuthToken.callCount == 2
        1 * new RequestValidator(authToken) >> Stub(RequestValidator) { validate(*_) >> true }

        res.status == ResultStatus.NO_CONTENT

        cleanup:
        getAuthToken.restore()
    }

    // Incoming media
    // --------------

    void "test extracting media id from url"() {
        expect:
        TwilioUtils.extractMediaIdFromUrl("") == ""
        TwilioUtils.extractMediaIdFromUrl(null) == ""
        TwilioUtils.extractMediaIdFromUrl("hellothere/yes") == "yes"
        TwilioUtils.extractMediaIdFromUrl("hello") == "hello"
        TwilioUtils.extractMediaIdFromUrl("/") == ""
        TwilioUtils.extractMediaIdFromUrl("    /") == ""
        TwilioUtils.extractMediaIdFromUrl("  e  /  ") == "  "
        TwilioUtils.extractMediaIdFromUrl(" / e  /  ") == "  "
    }

    void "test building incoming media"() {
        given:
        String accountId = TestUtils.randString()
        String messageId = TestUtils.randString()
        String mediaId = TestUtils.randString()
        TwilioUtils.metaClass."static".extractMediaIdFromUrl = { String url -> mediaId }

        TypeConvertingMap params = new TypeConvertingMap([AccountSid: accountId])
        List<String> urls = []
        int numMedia = 2
        numMedia.times {
            String mockUrl = UUID.randomUUID().toString()
            urls << mockUrl
            params["MediaUrl${it}"] = mockUrl
            params["MediaContentType${it}"] = MediaType.IMAGE_JPEG.mimeType
        }

        when: "no media"
        List<IncomingMediaInfo> mediaList = TwilioUtils.buildIncomingMedia(0, messageId, params)

        then:
        mediaList == []

        when:
        mediaList = TwilioUtils.buildIncomingMedia(numMedia, messageId, params)

        then:
        numMedia == mediaList.size()
        mediaList.every { it instanceof IncomingMediaInfo }
        mediaList.every { it.accountId == accountId }
        mediaList.every { it.messageId == messageId }
        mediaList.every { it.mimeType == MediaType.IMAGE_JPEG.mimeType }
        mediaList.every { it.mediaId == mediaId }
        mediaList.every { it.url in urls}
    }

    void "test building incoming recording"() {
        given:
        String accountId = TestUtils.randString()
        String url = TestUtils.randString()
        String sid = TestUtils.randString()
        TypeConvertingMap params = new TypeConvertingMap(AccountSid: accountId,
            RecordingUrl: url,
            RecordingSid: sid)

        when:
        IncomingRecordingInfo rInfo = TwilioUtils.buildIncomingRecording(params)

        then:
        MediaType.AUDIO_MP3.mimeType == rInfo.mimeType
        accountId == rInfo.accountId
        url == rInfo.url
        sid == rInfo.mediaId
    }

    // Updating status
    // ---------------

    void "test if should update receipt status"() {
        expect:
        TwilioUtils.shouldUpdateStatus(null, null) == false
        TwilioUtils.shouldUpdateStatus(ReceiptStatus.SUCCESS, null) == false
        TwilioUtils.shouldUpdateStatus(ReceiptStatus.SUCCESS, ReceiptStatus.PENDING) == false

        and:
        TwilioUtils.shouldUpdateStatus(null, ReceiptStatus.SUCCESS) == true
        TwilioUtils.shouldUpdateStatus(ReceiptStatus.PENDING, ReceiptStatus.SUCCESS) == true
        TwilioUtils.shouldUpdateStatus(ReceiptStatus.SUCCESS, ReceiptStatus.BUSY) == true
        TwilioUtils.shouldUpdateStatus(ReceiptStatus.SUCCESS, ReceiptStatus.FAILED) == true
    }

    void "test if should update receipt call duration"() {
        expect:
        TwilioUtils.shouldUpdateDuration(null, null) == false
        TwilioUtils.shouldUpdateDuration(88, null) == false
        TwilioUtils.shouldUpdateDuration(88, 88) == false

        and:
        TwilioUtils.shouldUpdateDuration(null, 88) == true
        TwilioUtils.shouldUpdateDuration(88, 21) == true
    }

    // Twiml
    // -----

    void "test responding to invalid twiml inputs"() {
        given:
        IOCUtils.metaClass."static".getMessageSource = { -> TestUtils.mockMessageSource() }

        when:
        Result<Closure> res = TwilioUtils.invalidTwimlInputs("hi")

        then:
        res.status == ResultStatus.BAD_REQUEST
        res.errorMessages.size() == 1
        res.errorMessages[0] == "twimlBuilder.invalidCode"
    }

    void "test no response Twiml"() {
        when:
        Result<Closure> res = TwilioUtils.noResponseTwiml()

        then:
        res.success == true
        res.status == ResultStatus.OK
        TestUtils.buildXml(res.payload) == TestUtils.buildXml({ Response { } })
    }

    void "test wrappng twiml closure"() {
        given:
        String msg = TestUtils.randString()

        when:
        Result<Closure> res = TwilioUtils.wrapTwiml { Say(msg) }

        then:
        res.success == true
        res.status == ResultStatus.OK
        TestUtils.buildXml(res.payload) == TestUtils.buildXml({ Response { Say(msg) } })
    }

    void "test inserting space between digits in strings passed to Say Twiml verb"() {
        expect:
        TwilioUtils.cleanForSay(null) == ""
        TwilioUtils.cleanForSay("") == ""
        TwilioUtils.cleanForSay("    hi ") == "hi"
        TwilioUtils.cleanForSay("This is a 24/7 phone line.") == "This is a 2 4 7 phone line."
        TwilioUtils.cleanForSay("The number is (626) 222-8888") == "The number is ( 6 2 6 ) 2 2 2 8 8 8 8"
    }

    void "test saying code with args"() {
        given:
        IOCUtils.metaClass."static".getMessageSource = { -> TestUtils.mockMessageSource() }

        when:
        String code = "hi"
        String formatted = TwilioUtils.say(code)

        then:
        "hi" == formatted

        when:
        code = "no h8"
        formatted = TwilioUtils.say(code, [1, 2, 3])

        then:
        "no h 8" == formatted
    }

    void "test saying phone number"() {
        given:
        PhoneNumber invalidNum = new PhoneNumber(number: "not a number")
        PhoneNumber validNum = new PhoneNumber(number: "111 222 3333")
        assert invalidNum.validate() == false
        assert validNum.validate() == true

        when: "invalid phone number"
        String formatted = TwilioUtils.say(invalidNum)

        then:
        "" == formatted

        when: "valid phone number"
        formatted = TwilioUtils.say(validNum)

        then:
        "1 1 1 2 2 2 3 3 3 3" == formatted
    }

    void "test formatting announcements for Twiml"() {
        given:
        IOCUtils.metaClass."static".getMessageSource = { -> TestUtils.mockMessageSource() }
        DateTime dt = DateTime.now()
        String id = TestUtils.randString()
        String msg = TestUtils.randString()
        Phone mockPhone = Mock(Phone)

        when: "no announcements"
        List<String> formattedList = TwilioUtils.formatAnnouncementsForRequest(null)

        then:
        ["twimlBuilder.noAnnouncements"] == formattedList

        when: "one announcement"
        String formatted = TwilioUtils.formatAnnouncementForRequest(dt, id, msg)

        then:
        "twimlBuilder.announcement" == formatted

        when: "many announcements"
        FeaturedAnnouncement a1 = new FeaturedAnnouncement(whenCreated: dt, owner: mockPhone, message: msg)
        formattedList = TwilioUtils.formatAnnouncementsForRequest([a1])

        then:
        1 * mockPhone.name >> "hi"
        ["twimlBuilder.announcement"] == formattedList
    }

    void "test building announcement for sending via text"() {
        given:
        IOCUtils.metaClass."static".getMessageSource = { -> TestUtils.mockMessageSource() }
        String id = TestUtils.randString()
        String msg = TestUtils.randString()

        when:
        String formatted = TwilioUtils.formatAnnouncementForSend(id, msg)

        then:
        "${id}: ${msg}. twimlBuilder.text.announcementUnsubscribe" == formatted
    }
}
