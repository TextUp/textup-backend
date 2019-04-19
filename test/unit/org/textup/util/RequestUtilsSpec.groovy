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

    void "test setting and getting on request"() {
        given:
        String key1 = TestUtils.randString()
        String key2 = TestUtils.randString()
        String value2 = TestUtils.randString()

        when: "setting nothing"
        Result res = RequestUtils.trySet(null, null)

        then:
        res.status == ResultStatus.NO_CONTENT

        when: "setting null value"
        res = RequestUtils.trySet(key1, null)

        then:
        res.status == ResultStatus.NO_CONTENT

        when: "setting non-null value"
        res = RequestUtils.trySet(key2, value2)

        then:
        res.status == ResultStatus.NO_CONTENT

        when: "getting nonexistent"
        res = RequestUtils.tryGet(null)

        then:
        res.status == ResultStatus.NOT_FOUND
        res.errorMessages == ["requestUtils.notFound"]

        when: "getting an existing key with a null value"
        res = RequestUtils.tryGet(key1)

        then: "null value is treated as not found"
        res.status == ResultStatus.NOT_FOUND
        res.errorMessages == ["requestUtils.notFound"]

        when: "getting an existing key with a non-null value"
        res = RequestUtils.tryGet(key2)

        then:
        res.status == ResultStatus.OK
        res.payload == value2
    }

    void "test when no request is present, transparently create and bind a mock request"() {
        given:
        HttpServletRequest mockRequest = Mock()
        MockedMethod getRequestAttributes = MockedMethod.create(RequestContextHolder, "getRequestAttributes") {
            Stub(GrailsWebRequest) { getCurrentRequest() >> mockRequest }
        }

        when: "has bound request"
        HttpServletRequest request = RequestUtils.tryGetRequest()

        then:
        getRequestAttributes.callCount == 1
        request != null

        when: "no thread-bound request"
        getRequestAttributes = MockedMethod.create(getRequestAttributes) { null }
        request = RequestUtils.tryGetRequest()

        then:
        getRequestAttributes.callCount == 1
        request != null

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
