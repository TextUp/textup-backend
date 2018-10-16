package org.textup

import com.twilio.security.RequestValidator
import grails.test.mixin.support.GrailsUnitTestMixin
import grails.test.mixin.TestMixin
import javax.servlet.http.HttpServletRequest
import org.codehaus.groovy.grails.web.util.TypeConvertingMap
import org.joda.time.DateTime
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*
import spock.lang.*

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

    void "test validating request from twilio"() {
        given:
        Helpers.metaClass."static".getMessageSource = { -> TestHelpers.mockMessageSource() }
        HttpServletRequest mockRequest = GroovyMock(HttpServletRequest) // to mock forwardURI
        RequestValidator allValidators = GroovySpy(RequestValidator,
            constructorArgs: ["valid auth token"], global: true)
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
        1 * allValidators.validate(*_) >> false
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
        1 * allValidators.validate(*_) >> true
        res.status == ResultStatus.NO_CONTENT
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
        String messageId = TestHelpers.randString()
        String mediaId = TestHelpers.randString()
        TwilioUtils.metaClass."static".extractMediaIdFromUrl = { String url -> mediaId }

        TypeConvertingMap params = new TypeConvertingMap([:])
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
        mediaList.every { it.messageId == messageId }
        mediaList.every { it.mimeType == MediaType.IMAGE_JPEG.mimeType }
        mediaList.every { it.mediaId == mediaId }
        mediaList.every { it.url in urls}
    }

    void "test building incoming recording"() {
        given:
        String url = TestHelpers.randString()
        String sid = TestHelpers.randString()
        TypeConvertingMap params = new TypeConvertingMap(RecordingUrl: url, RecordingSid: sid)

        when:
        IncomingRecordingInfo rInfo = TwilioUtils.buildIncomingRecording(params)

        then:
        MediaType.AUDIO_MP3.mimeType == rInfo.mimeType
        url == rInfo.url
        sid == rInfo.mediaId
    }

    // Twiml
    // -----

    void "test responding to invalid twiml inputs"() {
        given:
        Helpers.metaClass."static".getMessageSource = { -> TestHelpers.mockMessageSource() }

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
        TestHelpers.buildXml(res.payload) == TestHelpers.buildXml({ Response { } })
    }

    void "test wrappng twiml closure"() {
        given:
        String msg = TestHelpers.randString()

        when:
        Result<Closure> res = TwilioUtils.wrapTwiml { Say(msg) }

        then:
        res.success == true
        res.status == ResultStatus.OK
        TestHelpers.buildXml(res.payload) == TestHelpers.buildXml({ Response { Say(msg) } })
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
        Helpers.metaClass."static".getMessageSource = { -> TestHelpers.mockMessageSource() }

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
        Helpers.metaClass."static".getMessageSource = { -> TestHelpers.mockMessageSource() }
        DateTime dt = DateTime.now()
        String id = TestHelpers.randString()
        String msg = TestHelpers.randString()
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
        Helpers.metaClass."static".getMessageSource = { -> TestHelpers.mockMessageSource() }
        String id = TestHelpers.randString()
        String msg = TestHelpers.randString()

        when:
        String formatted = TwilioUtils.formatAnnouncementForSend(id, msg)

        then:
        "${id}: ${msg}. twimlBuilder.text.announcementUnsubscribe" == formatted
    }
}
