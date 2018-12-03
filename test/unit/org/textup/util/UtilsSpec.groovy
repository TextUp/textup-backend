package org.textup.util

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import grails.util.Holders
import java.util.concurrent.*
import org.apache.http.client.methods.*
import org.apache.http.HttpResponse
import org.codehaus.groovy.grails.web.mapping.LinkGenerator
import org.joda.time.DateTime
import org.textup.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*
import spock.lang.Specification

@Domain([Organization, Location])
@TestMixin(HibernateTestMixin)
class UtilsSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        IOCUtils.metaClass."static".getMessageSource = { -> TestUtils.mockMessageSource() }
    }

    void "test getting notification number"() {
        when: "missing notification number"
        Holders.metaClass."static".getFlatConfig = { ->
            ["textup.apiKeys.twilio.notificationNumber": null]
        }
        Result<PhoneNumber> res = Utils.tryGetNotificationNumber()

        then:
        res.status == ResultStatus.INTERNAL_SERVER_ERROR
        res.errorMessages[0] == "utils.getNotificationNumber.missing"

        when: "invalid notification number"
        Holders.metaClass."static".getFlatConfig = { ->
            ["textup.apiKeys.twilio.notificationNumber": "not a phone number"]
        }
        res = Utils.tryGetNotificationNumber()

        then:
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages.size() > 0

        when: "valid notification number"
        String notifNum = TestUtils.randPhoneNumber()
        Holders.metaClass."static".getFlatConfig = { ->
            ["textup.apiKeys.twilio.notificationNumber": notifNum]
        }
        res = Utils.tryGetNotificationNumber()

        then:
        res.status == ResultStatus.OK
        res.payload instanceof PhoneNumber
        notifNum == res.payload.number
    }

    void "test calling closure"() {
        when: "no args"
        boolean wasCalled = false
        Utils.callClosure({ -> wasCalled = true }, [] as Object[])

        then:
        true == wasCalled

        when: "one arg"
        wasCalled = false
        Utils.callClosure({ Integer a1 -> wasCalled = true }, [1] as Object[])

        then:
        true == wasCalled

        when: "many args"
        wasCalled = false
        Utils.callClosure({ Integer a1, Integer a2, Integer a3 -> wasCalled = true },
            [1, 2, 3] as Object[])

        then:
        true == wasCalled
    }

    void "test error handling on request operations"() {
        when: "setting -- no request"
        Result<Void> res = Utils.trySetOnRequest("hello", "world")

        then: "IllegalStateException is caught and gracefully returned -- see mock"
        res.status == ResultStatus.INTERNAL_SERVER_ERROR
        res.errorMessages[0].contains("No thread-bound request found")

        when: "getting -- no request"
        res = Utils.tryGetFromRequest("hello")

        then:
        res.status == ResultStatus.INTERNAL_SERVER_ERROR
        res.errorMessages[0].contains("No thread-bound request found")
    }

    void "test with default"() {
        expect:
        "hello" == Utils.withDefault(null, "hello")
        "wut" == Utils.withDefault("wut", "hello")
        22L == Utils.withDefault(null, 22L)
        8L == Utils.withDefault(8L, 22L)
    }

    void "test executing closures without flushing the session"() {
        given: "an saved but not persisted instance"
        Organization org1 = new Organization()
        org1.name = "hi"
        org1.location = TestUtils.buildLocation()
        org1.save(flush:true, failOnError:true)

        org1.name = TestUtils.randString()
        assert org1.isDirty()
        org1.save()
        assert org1.isDirty()

        when: "we do an existence that would normally flush"
        assert Utils.<Boolean>doWithoutFlush { Organization.exists(-88L) } == false

        then: "saved but not persisted instance still isn't flushed"
        org1.isDirty()
    }

    void "test normalizing pagination"() {
        when: "no inputs"
        List<Integer> normalized = Utils.normalizePagination(null, null)

        then:
        normalized.size() == 2
        normalized[0] == 0
        normalized[1] == Constants.DEFAULT_PAGINATION_MAX

        when: "negative inputs"
        normalized = Utils.normalizePagination(-8, -8)

        then:
        normalized.size() == 2
        normalized[0] == 0
        normalized[1] == Constants.DEFAULT_PAGINATION_MAX

        when: "max is too high"
        Integer offset = 888
        normalized = Utils.normalizePagination(offset, Constants.MAX_PAGINATION_MAX * 2)

        then:
        normalized.size() == 2
        normalized[0] == offset
        normalized[1] == Constants.MAX_PAGINATION_MAX
    }
}
