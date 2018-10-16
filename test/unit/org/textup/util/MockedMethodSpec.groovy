package org.textup.util

import grails.test.mixin.support.GrailsUnitTestMixin
import grails.test.mixin.TestMixin
import grails.test.runtime.*
import spock.lang.*

@TestMixin(GrailsUnitTestMixin)
class MockedMethodSpec extends Specification {

    @DirtiesRuntime
    void "test try mock nonexistent method"() {
        given:
        Cat cat = new Cat()

        when:
        new MockedMethod(cat, "poop")

        then: "nonexistent -- cats do not poop"
        thrown IllegalArgumentException
    }

    @DirtiesRuntime
    void "test mock instance method"() {
        given:
        Cat cat = new Cat()
        String retVal = TestHelpers.randString()
        Closure action = { retVal }

        when: "mock initially"
        MockedMethod meow = new MockedMethod(cat, "meow", action)

        then:
        notThrown IllegalArgumentException
        meow.callCount == 0

        when: "call the mocked method"
        def meowValue = cat.meow()

        then:
        meowValue == retVal
        meow.callCount == 1
        meow.callArguments == [[null]]

        when: "try to mock again without restoring"
        meow = new MockedMethod(cat, "meow", action)

        then: "already mocked"
        thrown IllegalArgumentException

        when: "restore then mock again"
        meow.restore()
        meow = new MockedMethod(cat, "meow", action)

        then:
        notThrown IllegalArgumentException
        meow.callCount == 0
    }

    @DirtiesRuntime
    void "test mock static method"() {
        given:
        String retVal = TestHelpers.randString()
        Closure action = { retVal }

        when: "mock initially"
        MockedMethod create = new MockedMethod(Cat, "create", action)

        then:
        notThrown IllegalArgumentException
        create.callCount == 0

        when: "call mocked method"
        def createValue = Cat.create()

        then:
        createValue == retVal
        create.callCount == 1
        create.callArguments == [[]]

        when: "try to mock again without restoring"
        create = new MockedMethod(Cat, "create")

        then:
        thrown IllegalArgumentException

        when: "restore then mock again"
        create.restore()
        create = new MockedMethod(Cat, "create")

        then:
        notThrown IllegalArgumentException
        create.callCount == 0
    }

    // Test support classes
    // --------------------

    protected class Cat {

        static String create() {
            "i am a cat"
        }

        String meow(Integer numTimes = 2) {
            "meow"
        }
    }
}
