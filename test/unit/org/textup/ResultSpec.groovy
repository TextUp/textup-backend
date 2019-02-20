package org.textup

import grails.test.mixin.*
import grails.test.mixin.gorm.*
import grails.test.mixin.hibernate.*
import grails.test.mixin.support.*
import grails.test.runtime.*
import grails.validation.*
import org.apache.log4j.Logger
import org.joda.time.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*
import spock.lang.*

@Domain([CustomAccountDetails, Organization, Location])
@TestMixin(HibernateTestMixin)
class ResultSpec extends Specification {

    void "test static creators"() {
    	given: "a valid location"
    	Location loc1 = TestUtils.buildLocation()

    	when: "creating success"
    	Result res = Result.createSuccess(loc1, ResultStatus.CREATED)

    	then:
    	res.success == true
    	res.status == ResultStatus.CREATED
    	res.errorMessages.isEmpty() == true
    	res.payload.id == loc1.id

    	when: "creating error"
    	String msg = "I am an error"
    	res = Result.createError([msg], ResultStatus.UNPROCESSABLE_ENTITY)

    	then:
    	res.success == false
    	res.status == ResultStatus.UNPROCESSABLE_ENTITY
    	res.errorMessages.size() == 1
    	res.errorMessages[0] == msg
    	res.payload == null

        when: "creating void"
        res = Result.void()

        then:
        res.success == true
        res.status == ResultStatus.NO_CONTENT
        res.payload == null
    }

    void "test currying arguments for success when chaining"() {
        given:
        Result<Location> thisLocRes = Result.createSuccess(TestUtils.buildLocation())
        Integer curry1 = 88
        String curry2 = "hi"
        Result<Location> successRes = Result
            .<Location>createSuccess(TestUtils.buildLocation(), ResultStatus.CREATED)
            .curry(curry1, curry2)

        when: "subsequent handler with zero arguments"
        Result<Location> res = successRes.then { -> thisLocRes }

        then:
        res == thisLocRes

        when: "subsequent handler with exact number of arguments"
        res = successRes.then { Integer a1, String a2, Location a3, ResultStatus a4 -> thisLocRes }

        then:
        res == thisLocRes

        when: "subsequent handler with too few arguments"
        res = successRes.then { Integer a1 -> thisLocRes }

        then:
        res == thisLocRes

        when: "subsequent handler with too many arguments"
        def extraArg
        res = successRes.then { Integer a1, String a2, Location a3, ResultStatus a4, Boolean a5 ->
            extraArg = a5
            thisLocRes
        }

        then:
        res == thisLocRes
        null == extraArg

        when: "incorrect arg types"
        res = successRes.then { String a1, Integer a2, Boolean a3 -> thisLocRes }

        then:
        thrown MissingMethodException
    }

    void "test ensure that array type one-argument handlers are properly unwrapped"() {
        given:
        Result<byte[]> returnValue = Result.createSuccess(TestUtils.randString().bytes)
        Result<byte[]> successRes = Result.createSuccess("hi".bytes, ResultStatus.OK)

        expect:
        successRes.then { byte[] payload -> returnValue } == returnValue
    }

    void "test currying with a single null value"() {
        given:
        def curriedVal1 = TestUtils.randString()
        def curriedVal2 = TestUtils.randString()

        when:
        Result.createSuccess(10)
            .curry(null)
            .thenEnd { arg1 -> curriedVal1 = arg1 }

        then:
        notThrown NullPointerException
        curriedVal1 == null

        when:
        Result.createError([], ResultStatus.UNPROCESSABLE_ENTITY)
            .curryFailure(null)
            .ifFailEnd { arg1 -> curriedVal2 = arg1 }

        then:
        notThrown NullPointerException
        curriedVal1 == null
    }

    void "test currying arguments for failure when chaining"() {
        given:
        Result<Location> thisLocRes = Result.createSuccess(TestUtils.buildLocation())
        Integer curry1 = 88
        String curry2 = "hi"
        String msg = TestUtils.randString()

        when: "subsequent handler with zero arguments"
        Result res = Result.createError([msg], ResultStatus.UNPROCESSABLE_ENTITY)
            .curryFailure(curry1, curry2)
            .ifFail { -> thisLocRes }

        then:
        res == thisLocRes

        when: "subsequent handler with exact number of arguments"
        res = Result.createError([msg], ResultStatus.UNPROCESSABLE_ENTITY)
            .curryFailure(curry1, curry2)
            .ifFail { Integer a1, String a2, Result a3 -> thisLocRes }

        then:
        res == thisLocRes

        when: "subsequent handler with too few arguments"
        res = Result.createError([msg], ResultStatus.UNPROCESSABLE_ENTITY)
            .curryFailure(curry1, curry2)
            .ifFail { Integer a1 -> thisLocRes }

        then:
        res == thisLocRes

        when: "subsequent handler with too many arguments"
        def extraArg
        res = Result.createError([msg], ResultStatus.UNPROCESSABLE_ENTITY)
            .curryFailure(curry1, curry2)
            .ifFail { Integer a1, String a2, Result a3, Boolean a4 ->
                extraArg = a4
                thisLocRes
            }

        then:
        res == thisLocRes
        null == extraArg

        when: "incorrect arg types"
        res = Result.createError([msg], ResultStatus.UNPROCESSABLE_ENTITY)
            .curryFailure(curry1, curry2)
            .ifFail { String a1, Integer a2, Boolean a3 -> thisLocRes }

        then:
        thrown MissingMethodException
    }

    void "test currying and clearing currying"() {
        given:
        Integer curry1 = 88
        String successCurry1 = "hi"
        Boolean failCurry1 = true
        Result successRes = Result.createSuccess(TestUtils.buildLocation())
        Result failRes = Result.createError([TestUtils.randString()], ResultStatus.UNPROCESSABLE_ENTITY)

        when: "currying"
        successRes
            .curry(curry1, successCurry1)
        failRes
            .curry(curry1)
            .curryFailure(failCurry1)

        then:
        successRes.then { Integer a1, String a2, Location a3, ResultStatus a4 ->
            assert a1 == curry1;
            assert a2 == successCurry1;
            Result.void()
        }
        failRes.ifFail { Boolean a2, Result a3 ->
            assert a2 == failCurry1;
            Result.void()
        }

        when: "clearing curry"
        successRes.clearCurry()
        successRes.clearCurryFailure()
        successRes.resetErrorHandling()
        failRes.clearCurry()
        failRes.clearCurryFailure()
        failRes.resetErrorHandling()

        then: "reset back to default types without calling error"
        successRes.then { Location a1, ResultStatus a2 ->
            Result.void()
        }
        failRes.ifFail { Result a1 ->
            Result.void()
        }
    }

    void "test only one failure handler will be called for a failed result"() {
        given:
        int timesCalled = 0
        Closure failAction = { res -> ++timesCalled; res; }

        when:
        Result failRes = Result.createError([], ResultStatus.BAD_REQUEST)

        then:
        failRes.hasErrorBeenHandled() == false

        when:
        failRes.ifFail(failAction)

        then:
        failRes.hasErrorBeenHandled() == true
        timesCalled == 1

        when:
        failRes.ifFail(failAction)

        then: "not called again"
        failRes.hasErrorBeenHandled() == true
        timesCalled == 1

        when:
        failRes.resetErrorHandling()
        failRes.ifFail(failAction)

        then: "failure is called again after reset"
        failRes.hasErrorBeenHandled() == true
        timesCalled == 2
    }

    void "test failure handlers"() {
        given:
        ByteArrayOutputStream stdErr = TestUtils.captureAllStreamsReturnStdErr()
        int timesCalled = 0
        Closure failAction = { res -> ++timesCalled; res; }
        String prefix1 = TestUtils.randString()
        String prefix2 = TestUtils.randString()
        String msg = TestUtils.randString()

        when: "one success and one failure result"
        Result failRes = Result.createError([msg], ResultStatus.UNPROCESSABLE_ENTITY)
        def retVal = failRes.ifFail(failAction)

        then:
        retVal instanceof Result
        timesCalled == 1

        when:
        failRes.resetErrorHandling()
        retVal = failRes.ifFailEnd(failAction)

        then:
        retVal == null
        timesCalled == 2

        when:
        failRes.resetErrorHandling()
        retVal = failRes.ifFail(prefix1, failAction)

        then:
        retVal instanceof Result
        stdErr.toString().contains(prefix1)
        !stdErr.toString().contains(prefix2)
        stdErr.toString().contains(msg)
        timesCalled == 3

        when:
        stdErr.reset()
        failRes.resetErrorHandling()
        retVal = failRes.ifFailEnd(prefix2, failAction)

        then:
        retVal == null
        !stdErr.toString().contains(prefix1)
        stdErr.toString().contains(prefix2)
        stdErr.toString().contains(msg)
        timesCalled == 4

        cleanup:
        TestUtils.restoreAllStreams()
    }

    void "test success handlers"() {
        given:
        int timesCalled = 0
        Closure action = { ++timesCalled; Result.void(); }

        when:
        Result res = Result.createSuccess(TestUtils.buildLocation())
        def retVal = res.then(action)

        then:
        retVal instanceof Result
        timesCalled == 1

        when:
        retVal = res.thenEnd(action)

        then:
        retVal == null
        timesCalled == 2
    }

    void "test catch-all handlers"() {
        given:
        int timesCalled = 0
        Closure action = { ++timesCalled; Result.void(); }

        Result res = Result.createSuccess("hi")
        Result failRes = Result.createError([], ResultStatus.BAD_REQUEST)

        when:
        res
            .alwaysEnd(action)
        failRes
            .ifFail(action)
            .alwaysEnd(action)

        then:
        timesCalled == 3
        failRes.hasErrorBeenHandled() == true

        when:
        failRes
            .ifFail(action)
            .alwaysEnd(action)

        then: "`alwaysEnd` called even if error already handled"
        failRes.hasErrorBeenHandled() == true
        timesCalled == 4
    }

    void "test converting to group"() {
        given:
        Result res = Result.createSuccess("hi")

        when:
        ResultGroup resGroup = res.toGroup()

        then:
        resGroup.isEmpty == false
        resGroup.anySuccesses == true
        resGroup.anyFailures == false
    }

    void "test chaining"() {
        given:
        int timesSuccess = 0
        int timesFail = 0
        int timesAlways = 0
        Closure successReturnSuccess = { ++timesSuccess; Result.void(); }
        Closure successReturnFail = { ++timesSuccess; Result.createError([], ResultStatus.BAD_REQUEST); }
        Closure failAction = { res -> ++timesFail; res; }
        Closure alwaysAction = { ++timesAlways }

        when:
        Result.void()
            .ifFail(failAction) // skip
            .then(successReturnSuccess) // success + 1
            .ifFail(failAction) // skip
            .then(successReturnFail) // success + 1
            .ifFail(failAction) // failure + 1
            .then(successReturnSuccess) // skip
            .ifFail(failAction) // skip
            .alwaysEnd(alwaysAction) // always + 1

        then:
        timesSuccess == 2
        timesFail == 1
        timesAlways == 1
    }
}
