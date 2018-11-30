package org.textup

import org.textup.test.*
import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import grails.validation.ValidationErrors
import org.springframework.context.MessageSource
import org.textup.type.*
import org.textup.util.*
import spock.lang.*

@Domain([Organization, Location])
@TestMixin(HibernateTestMixin)
class ResultSpec extends Specification {

    void "test static creators"() {
    	given: "a valid location"
    	Location loc1 = TestUtils.buildLocation()

    	when: "creating success"
    	Result<Location> res = Result.<Location>createSuccess(loc1, ResultStatus.CREATED)

    	then:
    	res.success == true
    	res.status == ResultStatus.CREATED
    	res.errorMessages.isEmpty() == true
    	res.payload.id == loc1.id

    	when: "creating error"
    	String msg = "I am an error"
    	res = Result.<Location>createError([msg], ResultStatus.UNPROCESSABLE_ENTITY)

    	then:
    	res.success == false
    	res.status == ResultStatus.UNPROCESSABLE_ENTITY
    	res.errorMessages.size() == 1
    	res.errorMessages[0] == msg
    	res.payload == null
    }

    void "test chaining results"() {
    	given: "one success and one failure result"
    	String msg = "I am an error"
    	Result<Location> failRes = Result.<Location>createError([msg], ResultStatus.UNPROCESSABLE_ENTITY)
    	Result<Location> successRes = Result.<Location>createSuccess(TestUtils.buildLocation(), ResultStatus.CREATED)

    	when: "for failed result"
    	int numTimesFailed = 0

    	failRes.thenEnd({ numTimesFailed-- }, { numTimesFailed++ })
    	Result res = failRes.then({ numTimesFailed--; successRes }, { numTimesFailed++; failRes; })

    	then: "appropriate action is called"
    	numTimesFailed == 2
    	res.success == failRes.success
    	res.status == failRes.status
    	res.errorMessages[0] == failRes.errorMessages[0]

    	when: "for successful result"
    	numTimesFailed = 0

    	successRes.thenEnd({ numTimesFailed-- }, { numTimesFailed++ })
    	res = successRes.then({ numTimesFailed--; successRes }, { numTimesFailed++; failRes; })

    	then: "appropriate action is called"
    	numTimesFailed == -2
    	res.success == successRes.success
    	res.status == successRes.status
    	res.errorMessages.isEmpty() == true
    	res.payload.id == successRes.payload.id
    }

    void "test fallback handlers when chaining"() {
        given:
        int successTimesCalled = 0
        int failTimesCalled = 0
        Closure successCounter = { successTimesCalled++ }
        Closure failCounter = { failTimesCalled++ }

        String msg = "hi"
        Result<Location> successRes = Result.<Location>createSuccess(TestUtils.buildLocation(), ResultStatus.CREATED)
        Result<Location> failRes = Result.<Location>createError([msg], ResultStatus.UNPROCESSABLE_ENTITY)

        when:
        Result res = successRes.then(null, failCounter)

        then: "if success handler is null, then just return original result obj"
        res == successRes
        0 == successTimesCalled
        0 == failTimesCalled

        when:
        res = failRes.then(successCounter, null)

        then: "if failure handler is null, then just return original result obj"
        res == failRes
        0 == successTimesCalled
        0 == failTimesCalled
    }

    void "test currying arguments for success when chaining"() {
        given:
        Result<Location> thisLocRes = new Result<>(payload: TestUtils.buildLocation())
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
        Result<byte[]> returnValue = new Result(payload: TestUtils.randString().bytes)
        Result<byte[]> successRes = Result.createSuccess("hi".bytes, ResultStatus.OK)

        expect:
        successRes.then { byte[] payload -> returnValue } == returnValue
    }

    void "test currying arguments for failure when chaining"() {
        given:
        Result<Location> thisLocRes = new Result<>(payload: TestUtils.buildLocation())
        Integer curry1 = 88
        String curry2 = "hi"
        String msg = TestUtils.randString()
        Result<Location> failRes = Result
            .<Location>createError([msg], ResultStatus.UNPROCESSABLE_ENTITY)
            .curry(curry1, curry2)

        when: "subsequent handler with zero arguments"
        Result<Location> res = failRes.then(null) { -> thisLocRes }

        then:
        res == thisLocRes

        when: "subsequent handler with exact number of arguments"
        res = failRes.then(null) { Integer a1, String a2, Result a3 -> thisLocRes }

        then:
        res == thisLocRes

        when: "subsequent handler with too few arguments"
        res = failRes.then(null) { Integer a1 -> thisLocRes }

        then:
        res == thisLocRes

        when: "subsequent handler with too many arguments"
        def extraArg
        res = failRes.then(null) { Integer a1, String a2, Result a3, Boolean a4 ->
            extraArg = a4
            thisLocRes
        }

        then:
        res == thisLocRes
        null == extraArg

        when: "incorrect arg types"
        res = failRes.then(null) { String a1, Integer a2, Boolean a3 -> thisLocRes }

        then:
        thrown MissingMethodException
    }

    void "test currying and clearing currying"() {
        given:
        Result<Location> thisLocRes = new Result<>(payload: TestUtils.buildLocation())
        Integer curry1 = 88
        String successCurry1 = "hi"
        Boolean failCurry1 = true
        Result<Location> res = new Result<>(status: ResultStatus.OK, payload: null)

        when: "currying"
        res
            .curry(curry1)
            .currySuccess(successCurry1)
            .curryFailure(failCurry1)

        then:
        res.then({ Integer a1, String a2, Location a3, ResultStatus a4 ->
            assert a1 == curry1;
            assert a2 == successCurry1;
            new Result()
        }, { Integer a1, Boolean a2, Result a3 ->
            assert a1 == curry1;
            assert a2 == failCurry1;
            new Result()
        })

        when: "clearing curry"
        res.clearCurry()

        then:
        res.then({ Location a1, ResultStatus a2 -> new Result() }, { Result a1 -> new Result() })
    }
}
