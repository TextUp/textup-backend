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
class TypeConversionUtilsSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        IOCUtils.metaClass."static".getMessageSource = { -> TestUtils.mockMessageSource() }
    }

    void "test converting enums"() {
        expect: "an invalid enum returns null"
        TypeConversionUtils.convertEnum(CallResponse, "invalid") == null

        and: "a valid enum (case insensitive) returns that enum"
        TypeConversionUtils.convertEnum(CallResponse,
            "SelF_COnnECTing") == CallResponse.SELF_CONNECTING
    }

    void "test converting list of enums"() {
        expect: "we pass in not a list and get null"
        TypeConversionUtils.toEnumList(CallResponse, "hello").isEmpty()

        and: "we have an invalid list get null at invalid positions"
        TypeConversionUtils.toEnumList(CallResponse, ["SelF_COnnECTing",
            "AnnounceMENT_GREE"]) == [CallResponse.SELF_CONNECTING, null]

        and: "we have a mixed valid list get all enums returned"
        TypeConversionUtils.toEnumList(CallResponse, ["SelF_COnnECTing",
            "AnnounceMENT_GREEting"]) == [CallResponse.SELF_CONNECTING,
            CallResponse.ANNOUNCEMENT_GREETING]
    }

    void "test type conversion for single types, including primitives"() {
        given:
        DateTime dt = DateTime.now()

        expect:
        TypeConversionUtils.to(boolean, "true") == true
        TypeConversionUtils.to(boolean, "false") == false
        TypeConversionUtils.to(boolean, "hello") == null
        TypeConversionUtils.to(Boolean, "true") == true
        TypeConversionUtils.to(Boolean, "false") == false
        TypeConversionUtils.to(Boolean, "hello") == null
        TypeConversionUtils.to(Collection, "hello") == null
        TypeConversionUtils.to(Collection, ["hello"]) instanceof Collection
        TypeConversionUtils.to(ArrayList, new HashSet<String>()) == null
        TypeConversionUtils.to(int, 1234.92) == 1234
        TypeConversionUtils.to(Integer, 1234.92) == 1234
        TypeConversionUtils.to(long, 1231231231290.92) == 1231231231290
        TypeConversionUtils.to(Long, 1231231231290.92) == 1231231231290
        TypeConversionUtils.to(Long, "1231231231290.92") == 1231231231290

        and: "no special behavior for PhoneNumber"
        false == (TypeConversionUtils.to(PhoneNumber, "1231231231290.92") instanceof PhoneNumber)
        TypeConversionUtils.to(PhoneNumber, null) == null

        and: "string conversions fall back to toString()"
        TypeConversionUtils.to(String, dt) == dt.toString()
    }

    void "test type conversion of all types in a collection"() {
        expect:
        TypeConversionUtils.allTo(Boolean, [true, "false", "yes", [:]]) == [true, false, null, null]
        TypeConversionUtils.allTo(Boolean, [true, "false", "yes", [:]], false) == [true, false, false, false]
        TypeConversionUtils.allTo(Integer, [1, "2", false, "32.5"]) == [1, 2, null, 32]
        TypeConversionUtils.allTo(Integer, null) == []
    }
}
