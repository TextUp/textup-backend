package org.textup

import grails.test.mixin.support.GrailsUnitTestMixin
import org.textup.types.CallResponse
import org.textup.types.ResultType
import org.textup.validator.ImageInfo
import spock.lang.Specification

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
                new Result(type:ResultType.SUCCESS, payload:new URL(url))
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
}
