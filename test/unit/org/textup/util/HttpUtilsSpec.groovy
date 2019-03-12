package org.textup.util

import grails.test.mixin.support.GrailsUnitTestMixin
import grails.test.mixin.TestMixin
import grails.util.Holders
import java.util.concurrent.*
import org.apache.http.client.methods.*
import org.apache.http.HttpResponse
import org.codehaus.groovy.grails.web.mapping.LinkGenerator
import org.joda.time.DateTime
import org.textup.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.domain.*
import org.textup.validator.*
import spock.lang.*

@TestMixin(GrailsUnitTestMixin)
class HttpUtilsSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test executing basic auth request"() {
        given:
        ByteArrayOutputStream stdErr = TestUtils.captureAllStreamsReturnStdErr()
        String root = TestConstants.TEST_HTTP_ENDPOINT
        String un = TestUtils.randString()
        String pwd = TestUtils.randString()
        HttpGet request = new HttpGet("${root}/basic-auth/${un}/${pwd}")
        Integer statusCode

        when: "body throws an exception"
        Result<?> res = HttpUtils.executeBasicAuthRequest(null, null, request) { }

        then: "exception is gracefully handled"
        res.status == ResultStatus.INTERNAL_SERVER_ERROR
        stdErr.toString().contains("Username may not be null")

        when: "invalid credentials"
        stdErr.reset()
        res = HttpUtils.executeBasicAuthRequest("incorrect", "incorrect", request) { HttpResponse resp ->
            new Result()
        }

        then:
        res.status == ResultStatus.UNAUTHORIZED
        stdErr.size() == 0

        when: "valid credentials"
        stdErr.reset()
        res = HttpUtils.executeBasicAuthRequest(un, pwd, request) { HttpResponse resp ->
            statusCode = resp.statusLine.statusCode
            Result.void()
        }

        then:
        res.status == ResultStatus.NO_CONTENT
        statusCode < 400
        stdErr.size() == 0

        cleanup:
        TestUtils.restoreAllStreams()
    }
}
