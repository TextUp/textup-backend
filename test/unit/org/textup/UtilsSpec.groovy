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
class UtilsSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        IOCUtils.metaClass."static".getMessageSource = { -> TestUtils.mockMessageSource() }
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
        Record rec = new Record()
        rec.save(flush:true, failOnError:true)

        rec.lastRecordActivity = DateTime.now().plusHours(12)
        assert rec.isDirty()
        rec.save()
        assert rec.isDirty()

        when: "we do an existence that would normally flush"
        assert Utils.<Boolean>doWithoutFlush { Staff.exists(-88L) } == false

        then: "saved but not persisted instance still isn't flushed"
        rec.isDirty()
    }

    void "test error handling on request operations"() {
        when: "no request"
        Result<Void> res = Utils.trySetOnRequest("hello", "world")

        then: "IllegalStateException is caught and gracefully returned -- see mock"
        res.status == ResultStatus.INTERNAL_SERVER_ERROR
        res.errorMessages[0].contains("No thread-bound request found")
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
}
