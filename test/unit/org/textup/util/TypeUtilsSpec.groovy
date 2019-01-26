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
import spock.lang.Specification

@TestMixin(GrailsUnitTestMixin)
class TypeUtilsSpec extends Specification {

    void "test converting enums"() {
        expect: "an invalid enum returns null"
        TypeUtils.convertEnum(CallResponse, "invalid") == null

        and: "a valid enum (case insensitive) returns that enum"
        TypeUtils.convertEnum(CallResponse,
            "SelF_COnnECTing") == CallResponse.SELF_CONNECTING
    }

    void "test converting list of enums"() {
        expect: "we pass in not a list and get null"
        TypeUtils.toEnumList(CallResponse, "hello").isEmpty()

        and: "we have an invalid list get null at invalid positions"
        TypeUtils.toEnumList(CallResponse, ["SelF_COnnECTing",
            "AnnounceMENT_GREE"]) == [CallResponse.SELF_CONNECTING, null]

        and: "we have a mixed valid list get all enums returned"
        TypeUtils.toEnumList(CallResponse, ["SelF_COnnECTing",
            "AnnounceMENT_GREEting"]) == [CallResponse.SELF_CONNECTING,
            CallResponse.ANNOUNCEMENT_GREETING]
    }

    void "test type conversion for single types, including primitives"() {
        given:
        DateTime dt = DateTime.now()

        expect:
        TypeUtils.to(boolean, "true") == true
        TypeUtils.to(boolean, "false") == false
        TypeUtils.to(boolean, "hello") == null
        TypeUtils.to(Boolean, "true") == true
        TypeUtils.to(Boolean, "false") == false
        TypeUtils.to(Boolean, "hello") == null
        TypeUtils.to(Collection, "hello") == null
        TypeUtils.to(Collection, ["hello"]) instanceof Collection
        TypeUtils.to(ArrayList, new HashSet<String>()) == null
        TypeUtils.to(int, 1234.92) == 1234
        TypeUtils.to(Integer, 1234.92) == 1234
        TypeUtils.to(long, 1231231231290.92) == 1231231231290
        TypeUtils.to(Long, 1231231231290.92) == 1231231231290
        TypeUtils.to(Long, "1231231231290.92") == 1231231231290

        and: "no special behavior for PhoneNumber"
        false == (TypeUtils.to(PhoneNumber, "1231231231290.92") instanceof PhoneNumber)
        TypeUtils.to(PhoneNumber, null) == null

        and: "string conversions fall back to toString()"
        TypeUtils.to(String, dt) == dt.toString()
    }

    void "test type conversion of all types in a collection"() {
        expect:
        TypeUtils.allTo(Boolean, [true, "false", "yes", [:]]) == [true, false, null, null]
        TypeUtils.allTo(Boolean, [true, "false", "yes", [:]], false) == [true, false, false, false]
        TypeUtils.allTo(Integer, [1, "2", false, "32.5"]) == [1, 2, null, 32]
        TypeUtils.allTo(Integer, null) == []
    }
}
