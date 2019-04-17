package org.textup.util

import grails.test.mixin.support.GrailsUnitTestMixin
import grails.test.mixin.TestMixin
import javax.servlet.http.HttpServletRequest
import org.codehaus.groovy.grails.web.servlet.mvc.GrailsWebRequest
import org.springframework.web.context.request.RequestContextHolder
import org.textup.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.domain.*
import org.textup.validator.*
import spock.lang.*

@TestMixin(GrailsUnitTestMixin)
class RequestUtilsSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test request operations when no request"() {
        when: "setting -- no request"
        Result res = RequestUtils.trySet("hello", "world")

        then: "silently does nothing"
        res.status == ResultStatus.NO_CONTENT

        when: "getting -- no request"
        res = RequestUtils.tryGet("hello")

        then: "nothing found"
        res.status == ResultStatus.NOT_FOUND
        res.errorMessages[0] == "requestUtils.notFound"
    }

    void "test setting and getting on request"() {
        given:
        HttpServletRequest mockRequest = Mock()
        MockedMethod getRequestAttributes = MockedMethod.create(RequestContextHolder, "getRequestAttributes") {
            Stub(GrailsWebRequest) { getCurrentRequest() >> mockRequest }
        }
        String key1 = TestUtils.randString()
        PhoneNumber pNum = TestUtils.randPhoneNumber()

        when: "set"
        Result res = RequestUtils.trySet(key1, pNum)

        then:
        1 * mockRequest.setAttribute(key1, pNum)
        res.status == ResultStatus.NO_CONTENT

        when: "get nonexistent"
        res = RequestUtils.tryGet("nonexistent key")

        then:
        res.status == ResultStatus.NOT_FOUND
        res.errorMessages == ["requestUtils.notFound"]

        when: "get"
        res = RequestUtils.tryGet(key1)

        then:
        1 * mockRequest.getAttribute(key1) >> pNum
        res.status == ResultStatus.OK
        res.payload == pNum

        cleanup:
        getRequestAttributes?.restore()
    }

    void "test getting browser url"() {
        given:
        String forwardUri = TestUtils.randString()
        String reqUri = TestUtils.randString()
        String reqUrl = TestUtils.randString()
        String queryString = TestUtils.randString()
        HttpServletRequest mockRequest = GroovyMock()

        when:
        String returnedUrl = RequestUtils.getBrowserURL(mockRequest)

        then:
        1 * mockRequest.requestURL >> new StringBuffer(reqUrl + reqUri)
        1 * mockRequest.requestURI >> reqUri
        1 * mockRequest.forwardURI >> forwardUri
        (1.._) * mockRequest.queryString >> queryString
        returnedUrl.contains(reqUrl + forwardUri)
        returnedUrl.contains(queryString)
    }

    void "test getting browser URL with more realistic inputs"() {
        given:
        HttpServletRequest mockRequest = GroovyMock() // to mock forwardURI

        when:
        String result = RequestUtils.getBrowserURL(mockRequest)

        then:
        1 * mockRequest.requestURL >> new StringBuffer("https://www.example.com/a.html")
        1 * mockRequest.requestURI >> "/a.html"
        1 * mockRequest.forwardURI >> "/b.html"
        2 * mockRequest.queryString >> "test3=bye&"
        result == "https://www.example.com/b.html?test3=bye&"
    }

    void "try getting json body"() {
        given:
        String mandatoryKey = TestUtils.randString()
        Map body = [(TestUtils.randString()): (TestUtils.randString())]
        HttpServletRequest mockRequest = GroovyMock()

        when: "no mandatory key needed"
        Result res = RequestUtils.tryGetJsonBody(mockRequest)

        then:
        1 * mockRequest.JSON >> body
        res.status == ResultStatus.CREATED
        res.payload == body

        when: "missing mandatory key"
        res = RequestUtils.tryGetJsonBody(mockRequest, mandatoryKey)

        then:
        1 * mockRequest.JSON >> body
        res.status == ResultStatus.CREATED
        res.payload == [:]

        when: "has mandatory key"
        res = RequestUtils.tryGetJsonBody(mockRequest, mandatoryKey)

        then:
        1 * mockRequest.JSON >> [(MarshallerUtils.resolveCodeToSingular(mandatoryKey)): body]
        res.status == ResultStatus.CREATED
        res.payload == body
    }
}
