package org.textup

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import org.joda.time.DateTime
import org.textup.type.CallResponse
import org.textup.validator.ImageInfo
import org.textup.validator.PhoneNumber
import spock.lang.Specification

@Domain([Contact, Phone, ContactTag, ContactNumber, Record, RecordItem, RecordText,
    RecordCall, RecordItemReceipt, SharedContact, Staff, Team, Organization,
    Schedule, Location, WeeklySchedule, PhoneOwnership, Role, StaffRole, NotificationPolicy])
@TestMixin(HibernateTestMixin)
class HelpersSpec extends Specification {

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

    void "test building images from image keys"() {
        given:
        String url = "https://www.example.com"
        StorageService mockServ = [
            generateAuthLink: { String objectKey ->
                new Result(status:ResultStatus.OK, payload:new URL(url))
            }
        ] as StorageService

        when:
        Long noteId = 88L
        Collection<String> imageKeys = [UUID.randomUUID().toString()]
        Collection<ImageInfo> imageInfoList = Helpers.buildImagesFromImageKeys(mockServ,
            noteId, imageKeys)

        then:
        imageInfoList.size() == 1
        imageInfoList[0] instanceof ImageInfo
        imageInfoList[0].key == imageKeys[0]
        imageInfoList[0].link == url
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

    void "test type conversion for single types"() {
        given:
        DateTime dt = DateTime.now()

        expect:
        Helpers.to(Boolean, "true") == true
        Helpers.to(Boolean, "false") == false
        Helpers.to(Boolean, "hello") == null
        Helpers.to(Collection, "hello") == null
        Helpers.to(Collection, ["hello"]) instanceof Collection
        Helpers.to(ArrayList, new HashSet<String>()) == null
        Helpers.to(Long, 1231231231290.92) == 1231231231290
        Helpers.to(Long, "1231231231290.92") == 1231231231290
        Helpers.to(PhoneNumber, "1231231231290.92") instanceof PhoneNumber
        Helpers.to(PhoneNumber, null) == null
        Helpers.to(String, dt) == dt.toString() // string conversions fall back to toString()
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
}
