package org.textup.util

import grails.test.mixin.support.GrailsUnitTestMixin
import grails.test.mixin.TestMixin
import org.codehaus.groovy.reflection.*
import org.textup.*
import spock.lang.*

@TestMixin(GrailsUnitTestMixin)
class MockedUtilsSpec extends Specification {

    Dog dog

    def setup() {
        dog = new Dog()
    }

    void "test getting default values for various types"() {
        expect:
        MockedUtils.getDefaultValue(byte) == (byte)0
        MockedUtils.getDefaultValue(short) == (short)0
        MockedUtils.getDefaultValue(int) == 0
        MockedUtils.getDefaultValue(long) == 0L
        MockedUtils.getDefaultValue(float) == 0.0f
        MockedUtils.getDefaultValue(double) == 0.0d
        MockedUtils.getDefaultValue(char) == '\u0000'
        MockedUtils.getDefaultValue(boolean) == false
        MockedUtils.getDefaultValue(null) == null
        MockedUtils.getDefaultValue(Integer) == null
        MockedUtils.getDefaultValue(MockedUtils) == null
    }

    void "test building closure for method with multiple incompatible signatures"() {
        given:
        ClassLoader loader = this.class.classLoader

        when:
        List<MetaMethod> metaMethods = dog.metaClass.respondsTo(dog, "bark")
        Closure override = MockedUtils.buildOverride(metaMethods, loader, null, null)

        then:
        thrown UnsupportedOperationException
    }

    void "test building closure for method with multiple incompatible return types"() {
        given:
        ClassLoader loader = this.class.classLoader

        when:
        List<MetaMethod> metaMethods = dog.metaClass.respondsTo(dog, "wag")
        Closure override = MockedUtils.buildOverride(metaMethods, loader, null, null)

        then:
        thrown UnsupportedOperationException
    }

    void "test building closure for method with no args"() {
        given:
        List callArgs = Mock()
        String retVal = TestUtils.randString()
        Closure action = { retVal }
        ClassLoader loader = this.class.classLoader

        when:
        List<MetaMethod> metaMethods = dog.metaClass.respondsTo(dog, "eat")
        Closure override = MockedUtils.buildOverride(metaMethods, loader, callArgs, action)

        then:
        notThrown UnsupportedOperationException
        override != null

        when:
        def callResult = override()

        then:
        callResult == retVal
        1 * callArgs.add({ it == [] })
    }

    void "test building closure for method one signature that contains a primitive"() {
        given:
        List callArgs = Mock()
        String retVal = TestUtils.randString()
        Closure action = { retVal }
        ClassLoader loader = this.class.classLoader

        when:
        List<MetaMethod> metaMethods = dog.metaClass.respondsTo(dog, "sniff")
        Closure override = MockedUtils.buildOverride(metaMethods, loader, callArgs, action)

        then:
        notThrown UnsupportedOperationException
        override != null

        when:
        def callResult = override(true)

        then:
        callResult == retVal
        1 * callArgs.add({ it == [true] })
    }

    void "test building closure for method with optional args that is a reference type"() {
        given:
        List callArgs = Mock()
        String retVal = TestUtils.randString()
        Closure action = { retVal }
        ClassLoader loader = this.class.classLoader
        String path = TestUtils.randString()

        when:
        List<MetaMethod> metaMethods = dog.metaClass.respondsTo(dog, "walk")
        Closure override = MockedUtils.buildOverride(metaMethods, loader, callArgs, action)

        then:
        notThrown UnsupportedOperationException
        override != null

        when: "call without optional param"
        def callResult = override(path)

        then:
        callResult == retVal
        1 * callArgs.add({ it == [path, 0] }) // default value of `int` is 0

        when: "call with optional params"
        callResult = override(path, 88)

        then:
        callResult == retVal
        1 * callArgs.add({ it == [path, 88] })
    }

    void "test passing in overriding action closure with incorrect parameter types throws exception"() {
        given:
        List callArgs = Mock()
        String retVal = TestUtils.randString()
        Closure invalidAction = { BigDecimal numTimes -> retVal }
        ClassLoader loader = this.class.classLoader
        String path = TestUtils.randString()

        when:
        List<MetaMethod> metaMethods = dog.metaClass.respondsTo(dog, "sniff")
        Closure override = MockedUtils.buildOverride(metaMethods, loader, callArgs, invalidAction)

        then:
        notThrown UnsupportedOperationException
        override != null

        when: "we try to pass a boolean to our closure that takes a BigDecimal"
        override(true)

        then:
        thrown MissingMethodException
    }

    void "test not passing in an overriding action just returns a default value based on the return type"() {
        given:
        List callArgs = Mock()
        String retVal = TestUtils.randString()
        ClassLoader loader = this.class.classLoader
        String path = TestUtils.randString()

        when:
        List<MetaMethod> metaMethods = dog.metaClass.respondsTo(dog, "yelp")
        Closure override = MockedUtils.buildOverride(metaMethods, loader, callArgs, null)

        then:
        notThrown UnsupportedOperationException
        override != null

        expect: "fall back to the default value for a double"
        0.0d == override()
    }

    void "test classloading for custom classes"() {
        given:
        List callArgs = Mock()
        String retVal = TestUtils.randString()
        Closure action = { retVal }
        ClassLoader loader = this.class.classLoader
        String path = TestUtils.randString()

        when:
        List<MetaMethod> metaMethods = dog.metaClass.respondsTo(dog, "nap")
        Closure override = MockedUtils.buildOverride(metaMethods, loader, callArgs, action)

        then:
        notThrown UnsupportedOperationException
        override != null

        when:
        def callResult = override(ResultStatus.OK)

        then:
        callResult == retVal
        1 * callArgs.add({ it == [ResultStatus.OK] })
    }

    void "test building closure with primitive array argument"() {
        given:
        List callArgs = Mock()
        String retVal = TestUtils.randString()
        Closure action = { retVal }
        ClassLoader loader = this.class.classLoader
        byte[] game = TestUtils.getJpegSampleData256()

        when:
        List<MetaMethod> metaMethods = dog.metaClass.respondsTo(dog, "play")
        Closure override = MockedUtils.buildOverride(metaMethods, loader, callArgs, action)

        then:
        notThrown UnsupportedOperationException
        override != null

        when: "call without optional param"
        def callResult = override(game)

        then:
        callResult == retVal
        1 * callArgs.add({ it == [game] })
    }

    // Test support classes
    // --------------------

    protected class Dog {

        String bark(Integer numTimes) {
            "woof"
        }

        String bark(String sound) {
            sound
        }

        void wag(Integer numTimes) {
        }

        String wag() {
            "hi"
        }

        String eat() {
            "yum"
        }

        String sniff(boolean shouldDoIt) {
            "smells good"
        }

        String walk(String path, int miles = 123) {
            path
        }

        String nap(ResultStatus sleepQuality) {
            sleepQuality.toString()
        }

        String play(byte[] game) {
            "this is fun"
        }

        double yelp() {
            88.8
        }
    }
}
