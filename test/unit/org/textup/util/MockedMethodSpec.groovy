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
    void "test mock instance method without forced override"() {
        given:
        Cat cat = new Cat()
        String originalVal = cat.meow()
        String overrideVal = TestUtils.randString()
        Closure action = { overrideVal }

        when: "mock initially"
        MockedMethod meow = new MockedMethod(cat, "meow", action)

        then:
        notThrown IllegalArgumentException
        meow.callCount == 0

        when: "call the mocked method"
        def meowValue = cat.meow()

        then:
        meowValue == overrideVal
        meowValue != originalVal
        meow.callCount == 1
        meow.callArguments == [[null]]

        when: "try to mock again without restoring"
        meow = new MockedMethod(cat, "meow", action)

        then: "already mocked"
        thrown IllegalArgumentException

        when: "restore"
        meow.restore()
        meowValue = cat.meow(8)

        then: "mocked method not tracking calls anymore"
        meowValue != overrideVal
        meowValue == originalVal
        meow.callCount == 0
        meow.callArguments == []

        when: "mock again"
        meow = new MockedMethod(cat, "meow", action)

        then:
        notThrown IllegalArgumentException
        meow.callCount == 0
    }

    @DirtiesRuntime
    void "test mock static method with forced override"() {
        given:
        String originalVal = Cat.create()
        String overrideVal = TestUtils.randString()
        Closure action = { overrideVal }

        when: "mock initially"
        MockedMethod create = new MockedMethod(Cat, "create", action)

        then:
        notThrown IllegalArgumentException
        create.callCount == 0

        when: "call mocked method"
        def createValue = Cat.create()

        then:
        createValue == overrideVal
        createValue != originalVal
        create.callCount == 1
        create.callArguments == [[]]

        when: "try to mock again without restoring"
        create = new MockedMethod(Cat, "create")

        then:
        thrown IllegalArgumentException

        when: "mock with a forced override"
        create = new MockedMethod(Cat, "create", null, true)

        then:
        notThrown IllegalArgumentException
        create.callCount == 0

        when:
        createValue = Cat.create()

        then:
        createValue == null // because no closure specified, default value for instance types is null
        create.callCount == 1
        create.callArguments == [[]]

        when: "finally restore"
        create.restore()
        createValue = Cat.create()

        then: "mocked method not tracking calls anymore"
        createValue != overrideVal
        createValue == originalVal
        create.callCount == 0
        create.callArguments == []
    }

    // Test support classes
    // --------------------

    protected class Cat {

        static String create() {
            "Cat.create"
        }

        String meow(Integer numTimes = 2) {
            "Cat.meow"
        }
    }
}
