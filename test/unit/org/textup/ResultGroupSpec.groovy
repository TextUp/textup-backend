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

@Domain([CustomAccountDetails, Organization, Location])
@TestMixin(HibernateTestMixin)
class ResultGroupSpec extends Specification {

    void "test adding items and composition"() {
        given:
        String msg1 = TestUtils.randString()
        String msg2 = TestUtils.randString()
        String msg3 = TestUtils.randString()

        when: "group is empty"
        ResultGroup<Location> resGroup = new ResultGroup<>()
        Result<Location> succ1 = Result.createSuccess(TestUtils.buildLocation(), ResultStatus.CREATED)
        Result<Location> succ2 = Result.createSuccess(TestUtils.buildLocation(), ResultStatus.CREATED)
        Result<Location> succ3 = Result.createSuccess(TestUtils.buildLocation(), ResultStatus.OK)
        Result<Location> fail1 = Result.createError([msg1], ResultStatus.UNPROCESSABLE_ENTITY)
        Result<Location> fail2 = Result.createError([msg2], ResultStatus.BAD_REQUEST)
        Result<Location> fail3 = Result.createError([msg3], ResultStatus.UNPROCESSABLE_ENTITY)

        then:
        resGroup.isEmpty == true
        resGroup.anySuccesses == false
        resGroup.anyFailures == false
        resGroup.successStatus == null
        resGroup.failureStatus == null
        resGroup.successes.isEmpty() == true
        resGroup.failures.isEmpty() == true
        resGroup.payload.isEmpty() == true
        resGroup.errorMessages.isEmpty() == true

        when:
        resGroup << [succ1, fail1]
        resGroup.add([succ2, fail2])
        resGroup << new ResultGroup([succ3, fail3])

        then:
        resGroup.isEmpty == false
        resGroup.anySuccesses == true
        resGroup.anyFailures == true
        resGroup.successStatus == ResultStatus.CREATED
        resGroup.failureStatus == ResultStatus.UNPROCESSABLE_ENTITY
        resGroup.successes.size() == 3
        resGroup.failures.size() == 3
        resGroup.payload.size() == 3
        resGroup.errorMessages.size() == 3
        msg1 in resGroup.errorMessages
        msg2 in resGroup.errorMessages
        msg3 in resGroup.errorMessages
    }

    void "test static collect for iterables"() {
        given:
        List args = []
        Closure action = { arg -> args << arg; Result.void(); }
        Iterable input1 = [1, 2, 3]

        when:
        ResultGroup resGroup = ResultGroup.collect(input1, action)

        then:
        resGroup.successes.size() == input1.size()
        args == input1
    }

    void "test static collect for maps"() {
        given:
        List keys = []
        List values = []
        Closure action = { arg1, arg2 ->
            keys << arg1
            values << arg2
            Result.void()
        }

        String key1 = TestUtils.randString()
        String key2 = TestUtils.randString()
        String value1 = TestUtils.randString()
        String value2 = TestUtils.randString()
        Map input1 = [(key1): value1, (key2): value2]

        when:
        ResultGroup resGroup = ResultGroup.collectEntries(input1, action)

        then:
        resGroup.successes.size() == input1.size()
        [key1, key2].every { it in keys }
        [value1, value2].every { it in values }
    }

    void "test converting to result"() {
        given:
        String successMsg = TestUtils.randString()
        Result res = Result.createSuccess(successMsg)
        Result failRes = Result.createError([], ResultStatus.UNPROCESSABLE_ENTITY)

        when:
        ResultGroup allSuccess = new ResultGroup([res])

        then: "that success payload is converted to a list"
        allSuccess.toResult(true).success == true
        allSuccess.toResult(true).payload == [successMsg]

        and: "or to null if empty"
        allSuccess.toEmptyResult(true).success == true
        allSuccess.toEmptyResult(true).payload == null
    }
}
