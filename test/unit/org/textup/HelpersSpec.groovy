package org.textup

import spock.lang.Specification
import grails.test.mixin.support.GrailsUnitTestMixin
import org.textup.types.CallResponse

@TestMixin(GrailsUnitTestMixin)
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
}
