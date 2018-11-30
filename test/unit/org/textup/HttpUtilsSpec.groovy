package org.textup

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import grails.util.Holders
import java.util.concurrent.*
import org.apache.http.client.methods.*
import org.apache.http.HttpResponse
import org.codehaus.groovy.grails.web.mapping.LinkGenerator
import org.joda.time.DateTime
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*
import spock.lang.Specification

// TODO check setup

@Domain([Contact, Phone, ContactTag, ContactNumber, Record, RecordItem, RecordText,
    RecordCall, RecordItemReceipt, SharedContact, Staff, Team, Organization,
    Schedule, Location, WeeklySchedule, PhoneOwnership, Role, StaffRole, NotificationPolicy,
    MediaInfo, MediaElement, MediaElementVersion])
@TestMixin(HibernateTestMixin)
class HttpUtilsSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        IOCUtils.metaClass."static".getMessageSource = { -> TestUtils.mockMessageSource() }
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
