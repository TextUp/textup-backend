package org.textup

import grails.test.mixin.*
import grails.test.mixin.gorm.*
import grails.test.mixin.hibernate.*
import grails.test.mixin.support.*
import grails.test.runtime.*
import grails.validation.*
import org.joda.time.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*
import spock.lang.*

@TestMixin(GrailsUnitTestMixin)
class TypeMapSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test creation"() {
        when:
        TypeMap map = TypeMap.tryCreate(null).payload

        then:
        map.size() == 0

        when: "from object"
        map = TypeMap.tryCreate("not a map").payload

        then:
        map.size() == 0

        when: "from map"
        map = TypeMap.tryCreate([hi: "there!"]).payload

        then:
        map.size() == 1
    }

    void "test converting to string"() {
        given:
        String key = TestUtils.randString()
        String val = TestUtils.randString()
        TypeMap map = TypeMap.create((key): val)

        expect:
        map.string(key) instanceof String
        map.string(key) == val
    }

    void "test converting to trimmed string"() {
        given:
        String key = TestUtils.randString()
        String val = TestUtils.randString()
        TypeMap map = TypeMap.create((key): "  "  + val + " ")

        expect:
        map.string(key) != val
        map.trimmedString(key) == val
    }

    void "test converting to type map, if possible"() {
        given:
        String key1 = TestUtils.randString()
        String key2 = TestUtils.randString()
        String val = TestUtils.randString()
        TypeMap map = new TypeMap((key1): [(key2): val])

        when:
        TypeMap nested = map.typeMapNoNull("not present key")

        then:
        nested.isEmpty() == true

        when:
        nested = map.typeMapNoNull(key1)

        then:
        nested.size() == 1
        nested.string(key2) == val
    }

    void "test converting to DateTime"() {
        given:
        String key1 = TestUtils.randString()
        DateTime dt = DateTime.now()
        TypeMap map = new TypeMap((key1): dt)

        expect:
        map.dateTime(key1) == dt
    }

    void "test converting to phone number list"() {
        given:
        String key1 = TestUtils.randString()
        PhoneNumber pNum1 = TestUtils.randPhoneNumber()
        PhoneNumber pNum2 = TestUtils.randPhoneNumber()
        String invalidNum1 = TestUtils.randString()
        TypeMap map = new TypeMap((key1): [pNum1.number, pNum2.number, invalidNum1])

        when:
        List pNums = map.phoneNumberList(key1)

        then:
        pNums.size() == 2
        pNums.any { it.number == pNum1.number }
        pNums.any { it.number == pNum2.number }
        pNums.every { it.number != invalidNum1 }
    }

    void "test converting to typed list"() {
        given:
        String key1 = TestUtils.randString()
        TypeMap map = new TypeMap((key1): [true, true, false])

        when:
        List typedList = map.typedList(Boolean, key1)

        then:
        typedList.every { it instanceof Boolean }
    }

    void "test converting to enum"() {
        given:
        String key1 = TestUtils.randString()
        String val = VoiceLanguage.ENGLISH.toString()
        TypeMap map = new TypeMap((key1): val)

        when:
        def outcome1 = map.enum(VoiceLanguage, key1)

        then:
        outcome1 == VoiceLanguage.ENGLISH

        when:
        def outcome2 = map.enumList(VoiceLanguage, key1)

        then:
        outcome2 instanceof Collection
        outcome2.size() == 1
        outcome2[0] == VoiceLanguage.ENGLISH
    }
}
