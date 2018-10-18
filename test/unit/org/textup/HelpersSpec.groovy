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

@Domain([Contact, Phone, ContactTag, ContactNumber, Record, RecordItem, RecordText,
    RecordCall, RecordItemReceipt, SharedContact, Staff, Team, Organization,
    Schedule, Location, WeeklySchedule, PhoneOwnership, Role, StaffRole, NotificationPolicy,
    MediaInfo, MediaElement, MediaElementVersion])
@TestMixin(HibernateTestMixin)
class HelpersSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        Helpers.metaClass.'static'.getMessageSource = { -> TestHelpers.mockMessageSource() }
    }

    void "test converting enums"() {
        expect: "an invalid enum returns null"
        Helpers.<CallResponse>convertEnum(CallResponse, "invalid") == null

        and: "a valid enum (case insensitive) returns that enum"
        Helpers.<CallResponse>convertEnum(CallResponse,
            "SelF_COnnECTing") == CallResponse.SELF_CONNECTING
    }

    void "test converting list of enums"() {
        expect: "we pass in not a list and get null"
        Helpers.<CallResponse>toEnumList(CallResponse, "hello").isEmpty()

        and: "we have an invalid list get null at invalid positions"
        Helpers.<CallResponse>toEnumList(CallResponse, ["SelF_COnnECTing",
            "AnnounceMENT_GREE"]) == [CallResponse.SELF_CONNECTING, null]

        and: "we have a mixed valid list get all enums returned"
        Helpers.<CallResponse>toEnumList(CallResponse, ["SelF_COnnECTing",
            "AnnounceMENT_GREEting"]) == [CallResponse.SELF_CONNECTING,
            CallResponse.ANNOUNCEMENT_GREETING]
    }

    void "test with default"() {
        expect:
        "hello" == Helpers.withDefault(null, "hello")
        "wut" == Helpers.withDefault("wut", "hello")
        22L == Helpers.withDefault(null, 22L)
        8L == Helpers.withDefault(8L, 22L)
    }

    void "test take right"() {
        given: "a list"
        List data = [0, 1, 2, 3, 4]

        when: "list is null"
        List taken = Helpers.takeRight(null, 2)

        then:
        taken == []

        when: "index is too small"
        taken = Helpers.takeRight(data, -2)

        then:
        taken == []

        when: "index is too big"
        taken = Helpers.takeRight(data, data.size() + 4)

        then:
        taken == []

        when: "take nothing"
        taken = Helpers.takeRight(data, 0)

        then:
        taken == []

        when: "take all"
        taken = Helpers.takeRight(data, data.size())

        then:
        taken == [0, 1, 2, 3, 4]

        when: "index is item in the middle of the list"
        taken = Helpers.takeRight(data, data.size() - 1)

        then:
        taken == [1, 2, 3, 4]

        when: "index is item in the middle of the list"
        taken = Helpers.takeRight(data, 3)

        then:
        taken == [2, 3, 4]
    }

    void "test in list ignoring case"() {
        expect:
        Helpers.inListIgnoreCase("toBeFound", ["hello", "yes"]) == false
        Helpers.inListIgnoreCase("toBeFound", ["tobefound"]) == true
        Helpers.inListIgnoreCase("toBeFound", []) == false
        Helpers.inListIgnoreCase(null, ["hello", "yes"]) == false
        Helpers.inListIgnoreCase("toBeFound", null) == false
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
        assert Helpers.<Boolean>doWithoutFlush { Staff.exists(-88L) } == false

        then: "saved but not persisted instance still isn't flushed"
        rec.isDirty()
    }

    void "test finding highest value"() {
        given:
        Date dt1 = new Date()
        Date dt2 = new Date(dt1.time * 2)

        expect:
        Helpers.findHighestValue(["okay":88, "value":1, "yas":2]).key == "okay"
        Helpers.findHighestValue(["okay":dt2, "value":dt1, "yas":dt1]).key == "okay"
        Helpers.findHighestValue(["okay":"c", "value":"b", "yas":"a"]).key == "okay"
    }

    void "test type conversion for single types, including primitives"() {
        given:
        DateTime dt = DateTime.now()

        expect:
        Helpers.to(boolean, "true") == true
        Helpers.to(boolean, "false") == false
        Helpers.to(boolean, "hello") == null
        Helpers.to(Boolean, "true") == true
        Helpers.to(Boolean, "false") == false
        Helpers.to(Boolean, "hello") == null
        Helpers.to(Collection, "hello") == null
        Helpers.to(Collection, ["hello"]) instanceof Collection
        Helpers.to(ArrayList, new HashSet<String>()) == null
        Helpers.to(int, 1234.92) == 1234
        Helpers.to(Integer, 1234.92) == 1234
        Helpers.to(long, 1231231231290.92) == 1231231231290
        Helpers.to(Long, 1231231231290.92) == 1231231231290
        Helpers.to(Long, "1231231231290.92") == 1231231231290

        and: "no special behavior for PhoneNumber"
        false == (Helpers.to(PhoneNumber, "1231231231290.92") instanceof PhoneNumber)
        Helpers.to(PhoneNumber, null) == null

        and: "string conversions fall back to toString()"
        Helpers.to(String, dt) == dt.toString()
    }

    void "test type conversion of all types in a collection"() {
        expect:
        Helpers.allTo(Boolean, [true, "false", "yes", [:]]) == [true, false, null, null]
        Helpers.allTo(Boolean, [true, "false", "yes", [:]], false) == [true, false, false, false]
        Helpers.allTo(Integer, [1, "2", false, "32.5"]) == [1, 2, null, 32]
        Helpers.allTo(Integer, null) == []
    }

    void "test join with different last"() {
        expect:
        Helpers.joinWithDifferentLast([], ", ", " and ") == ""
        Helpers.joinWithDifferentLast([1], ", ", " and ") == "1"
        Helpers.joinWithDifferentLast([1, 2], ", ", " and ") == "1 and 2"
        Helpers.joinWithDifferentLast([1, 2, 3], ", ", " and ") == "1, 2 and 3"
    }

    void "test appending strings while guaranteeing a max resulting length"() {
        expect:
        Helpers.appendGuaranteeLength("hello", null, 1) == "h"
        Helpers.appendGuaranteeLength(null, "yes", 1) == null
        Helpers.appendGuaranteeLength("hello", "yes", -1) == "hello"
        Helpers.appendGuaranteeLength("hello", "yes", 1) == "h"
        Helpers.appendGuaranteeLength("hello", "yes", 6) == "helyes"
        Helpers.appendGuaranteeLength("hello", "yes", 7) == "hellyes"
        Helpers.appendGuaranteeLength("hello", "yes", 8) == "helloyes"
        Helpers.appendGuaranteeLength("hello", "yes", 10) == "helloyes"
    }

    void "test json operations"() {
        given:
        Map seedData = [hello:[1, 2, 3], goodbye: "hello"]

        when: "from object to json string"
        String jsonString = Helpers.toJsonString(seedData)

        then:
        jsonString == '{"hello":[1,2,3],"goodbye":"hello"}'

        when: "from json string to obj"
        Object reconstructed = Helpers.toJson(jsonString)

        then:
        reconstructed == seedData
    }

    void "test do asynchronously processing list in batches"() {
        given: "a list of items, an action to execute, and a batch size"
        int _numTimesCalled = 0
        int batchSize = 2
        List<Integer> before = [1, 2, 3, 4]
        Closure<Integer> action = { Integer beforeNum ->
            _numTimesCalled++
            beforeNum + 1
        }

        when: "calling async helper method"
        List<Integer> after = Helpers.<Integer, Integer>doAsyncInBatches(before, action, batchSize)

        then: "all items in the list are processed"
        before.size() == _numTimesCalled
        after.eachWithIndex { Integer afterNum, int index ->
            assert before[index] + 1 == afterNum
        }
    }

    void "test error handling on request operations"() {
        when: "no request"
        Result<Void> res = Helpers.trySetOnRequest("hello", "world")

        then: "IllegalStateException is caught and gracefully returned -- see mock"
        res.status == ResultStatus.INTERNAL_SERVER_ERROR
        res.errorMessages[0].contains("No thread-bound request found")
    }

    void "test getting notification number"() {
        when: "missing notification number"
        Holders.metaClass.'static'.getFlatConfig = { ->
            ["textup.apiKeys.twilio.notificationNumber": null]
        }
        Result<PhoneNumber> res = Helpers.tryGetNotificationNumber()

        then:
        res.status == ResultStatus.INTERNAL_SERVER_ERROR
        res.errorMessages[0] == "helpers.getNotificationNumber.missing"

        when: "invalid notification number"
        Holders.metaClass.'static'.getFlatConfig = { ->
            ["textup.apiKeys.twilio.notificationNumber": "not a phone number"]
        }
        res = Helpers.tryGetNotificationNumber()

        then:
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages.size() > 0

        when: "valid notification number"
        String notifNum = TestHelpers.randPhoneNumber()
        Holders.metaClass.'static'.getFlatConfig = { ->
            ["textup.apiKeys.twilio.notificationNumber": notifNum]
        }
        res = Helpers.tryGetNotificationNumber()

        then:
        res.status == ResultStatus.OK
        res.payload instanceof PhoneNumber
        notifNum == res.payload.number
    }

    void "testing getting webhook link"() {
        given:
        Helpers.metaClass.'static'.getLinkGenerator = { ->
            [link: { Map m -> m.toString() }] as LinkGenerator
        }
        String handle = TestHelpers.randString()

        when:
        String link = Helpers.getWebhookLink(handle: handle)

        then: "test the stringified map"
        link.contains(handle)
        link.contains("handle")
        link.contains("publicRecord")
        link.contains("save")
        link.contains("v1")
    }

    void "test resolving message"() {
        given:
        Helpers.metaClass.'static'.getMessageSource = { -> TestHelpers.mockMessageSource() }

        when: "from code"
        String code = TestHelpers.randString()
        String msg = Helpers.getMessage(code, [1, 2, 3])

        then:
        msg == code

        when: "from resolvable object"
        Location emptyLoc1 = new Location()
        assert emptyLoc1.validate() == false
        msg = Helpers.getMessage(emptyLoc1.errors.allErrors[0])

        then:
        msg instanceof String
        msg != ""
    }

    void "test generating a no-op Future object"() {
        when: "null payload"
        Future<?> fut1 = Helpers.noOpFuture()

        then:
        fut1.cancel(true) == true
        fut1.get() == null
        fut1.get(1, TimeUnit.SECONDS) == null
        fut1.isCancelled() == false
        fut1.isDone() == true

        when: "specified payload"
        String msg = TestHelpers.randString()
        Future<String> fut2 = Helpers.noOpFuture(msg)

        then:
        fut2.cancel(true) == true
        fut2.get() == msg
        fut2.get(1, TimeUnit.SECONDS) == msg
        fut2.isCancelled() == false
        fut2.isDone() == true
    }

    void "test executing basic auth request"() {
        given:
        String root = Constants.TEST_HTTP_ENDPOINT
        String un = TestHelpers.randString()
        String pwd = TestHelpers.randString()
        HttpGet request = new HttpGet("${root}/basic-auth/${un}/${pwd}")
        Integer statusCode

        when: "body throws an exception"
        Result<?> res = Helpers.executeBasicAuthRequest(null, null, request) { }

        then: "exception is gracefully handled"
        res.status == ResultStatus.INTERNAL_SERVER_ERROR

        when: "invalid credentials"
        res = Helpers.executeBasicAuthRequest("incorrect", "incorrect", request) { HttpResponse resp ->
            statusCode = resp.statusLine.statusCode
            new Result()
        }

        then:
        res.status == ResultStatus.OK
        statusCode >= 400

        when: "valid credentials"
        res = Helpers.executeBasicAuthRequest(un, pwd, request) { HttpResponse resp ->
            statusCode = resp.statusLine.statusCode
            new Result()
        }

        then:
        res.status == ResultStatus.OK
        statusCode < 400
    }
}
