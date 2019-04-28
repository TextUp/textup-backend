package org.textup.util

import grails.test.mixin.support.GrailsUnitTestMixin
import grails.test.mixin.TestMixin
import org.textup.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.domain.*
import org.textup.validator.*
import spock.lang.*

@TestMixin(GrailsUnitTestMixin)
class ClosureUtilsSpec extends Specification {

    void "test building arguments based on max num allowed"() {
        given:
        List args = [1, 2, 3]

        expect:
        ClosureUtils.buildArgs(0, null) == []
        ClosureUtils.buildArgs(3, null) == [null, null, null]
        ClosureUtils.buildArgs(3, args) == [1, 2, 3]
        ClosureUtils.buildArgs(2, args) == [1, 2]
        ClosureUtils.buildArgs(0, args) == []
        ClosureUtils.buildArgs(5, args) == [1, 2, 3, null, null]
    }

    void "test executing closure, passing arguments properly"() {
        given:
        String val1 = TestUtils.randString()
        String val2 = TestUtils.randString()
        String val3 = TestUtils.randString()
        List inputArgs = [1, 2, 3, 4]
        List calledArgs = []
        Closure noArgs = { ->
            calledArgs << []
            val1
        }
        Closure oneArg = { arg1 ->
            calledArgs << [arg1]
            val2
        }
        Closure manyArgs = { arg1, arg2, arg3 ->
            calledArgs << [arg1, arg2, arg3]
            val3
        }

        when:
        def retVal = ClosureUtils.execute(noArgs, inputArgs)

        then:
        retVal == val1
        calledArgs.size() == 1
        calledArgs[0] == []

        when:
        retVal = ClosureUtils.execute(oneArg, inputArgs)

        then:
        retVal == val2
        calledArgs.size() == 2
        calledArgs[1] == [1]

        when:
        retVal = ClosureUtils.execute(manyArgs, inputArgs)

        then:
        retVal == val3
        calledArgs.size() == 3
        calledArgs[2] == [1, 2, 3]
    }

    void "test composing closures"() {
        given:
        String str1 = TestUtils.randString()
        Closure doAction = { str1 }

        when:
        def retVal = { ClosureUtils.compose(delegate, doAction) }.call()

        then:
        retVal == str1
    }
}
