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

    void "test executing basic auth request"() {
        given:
        String root = TestConstants.TEST_HTTP_ENDPOINT
        String un = TestUtils.randString()
        String pwd = TestUtils.randString()
        HttpGet request = new HttpGet("${root}/basic-auth/${un}/${pwd}")
        Integer statusCode

        when: "body throws an exception"
        Result<?> res = HttpUtils.executeBasicAuthRequest(null, null, request) { }

        then: "exception is gracefully handled"
        res.status == ResultStatus.INTERNAL_SERVER_ERROR

        when: "invalid credentials"
        res = HttpUtils.executeBasicAuthRequest("incorrect", "incorrect", request) { HttpResponse resp ->
            statusCode = resp.statusLine.statusCode
            new Result()
        }

        then:
        res.status == ResultStatus.OK
        statusCode >= 400

        when: "valid credentials"
        res = HttpUtils.executeBasicAuthRequest(un, pwd, request) { HttpResponse resp ->
            statusCode = resp.statusLine.statusCode
            new Result()
        }

        then:
        res.status == ResultStatus.OK
        statusCode < 400
    }
}
