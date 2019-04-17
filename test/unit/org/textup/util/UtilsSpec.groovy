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
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.domain.*
import org.textup.validator.*
import spock.lang.*

@Domain([CustomAccountDetails, Organization, Location])
@TestMixin(HibernateTestMixin)
class UtilsSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test getting notification number"() {
        when: "missing notification number"
        Holders.metaClass."static".getFlatConfig = { ->
            ["textup.apiKeys.twilio.notificationNumber": null]
        }
        Result<PhoneNumber> res = Utils.tryGetNotificationNumber()

        then:
        res.status == ResultStatus.INTERNAL_SERVER_ERROR
        res.errorMessages[0] == "utils.missingNotificationNumber"

        when: "invalid notification number"
        Holders.metaClass."static".getFlatConfig = { ->
            ["textup.apiKeys.twilio.notificationNumber": "not a phone number"]
        }
        res = Utils.tryGetNotificationNumber()

        then:
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages.size() > 0

        when: "valid notification number"
        String notifNum = TestUtils.randPhoneNumberString()
        Holders.metaClass."static".getFlatConfig = { ->
            ["textup.apiKeys.twilio.notificationNumber": notifNum]
        }
        res = Utils.tryGetNotificationNumber()

        then:
        res.status == ResultStatus.OK
        res.payload instanceof PhoneNumber
        notifNum == res.payload.number
    }

    void "test with default"() {
        expect:
        "hello" == Utils.withDefault(null, "hello")
        "wut" == Utils.withDefault("wut", "hello")
        22L == Utils.withDefault(null, 22L)
        8L == Utils.withDefault(8L, 22L)
    }

    void "test inclusive bound"() {
        expect:
        Utils.inclusiveBound(8, 5, 10) == 8
        Utils.inclusiveBound(8, 9, 10) == 9
        Utils.inclusiveBound(8, 2, 5) == 5

        and: "when initial value is null, bounding is skipped"
        Utils.inclusiveBound(null, null, null) == null
        Utils.inclusiveBound(null, 2, 5) == null
        Utils.inclusiveBound(null, null, 5) == null

        and: "invalid inputs where max is NOT greater than min cause bound operation to be skipped"
        Utils.inclusiveBound(8, null, 5) == 8
        Utils.inclusiveBound(8, 10, null) == 8
        Utils.inclusiveBound(8, null, null) == 8
        Utils.inclusiveBound(8, 5, 2) == 8
        Utils.inclusiveBound(8, 10, 2) == 8
        Utils.inclusiveBound(8, 12, 9) == 8
    }

    void "test executing closures without flushing the session"() {
        given: "an saved but not persisted instance"
        Organization org1 = TestUtils.buildOrg()

        org1.name = TestUtils.randString()
        assert org1.isDirty()
        org1.save()
        assert org1.isDirty()

        when: "we do an existence that would normally flush"
        assert Utils.<Boolean>doWithoutFlush { Organization.exists(-88L) } == false

        then: "saved but not persisted instance still isn't flushed"
        org1.isDirty()
    }
}
