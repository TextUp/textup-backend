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
class ResultUtilsSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test normalizing varargs"() {
        given:
        String str1 = TestUtils.randString()

        expect:
        ResultUtils.normalizeVarArgs(null) == [null]
        ResultUtils.normalizeVarArgs([] as Object[]) == []
        ResultUtils.normalizeVarArgs([str1] as Object[]) == [str1]
    }

    void "test converting all successes"() {
        given:
        String successMsg = TestUtils.randString()
        Result res = Result.createSuccess(successMsg)
        ResultGroup allSuccess = new ResultGroup([res])

        expect:
        allSuccess.toResult(true).success == true
        allSuccess.toResult(true).payload == [successMsg]

        allSuccess.toResult(false).success == true
        allSuccess.toResult(false).payload == [successMsg]

        and:
        allSuccess.toEmptyResult(true).success == true
        allSuccess.toEmptyResult(true).payload == null

        allSuccess.toEmptyResult(false).success == true
        allSuccess.toEmptyResult(false).payload == null
    }

    void "test converting all failures"() {
        given:
        String errMsg = TestUtils.randString()
        Result failRes = Result.createError([errMsg], ResultStatus.UNPROCESSABLE_ENTITY)
        ResultGroup allFail = new ResultGroup([failRes])

        expect:
        allFail.toResult(true).success == false
        allFail.toResult(true).errorMessages == [errMsg]

        allFail.toResult(false).success == false
        allFail.toResult(false).errorMessages == [errMsg]

        and:
        allFail.toEmptyResult(true).success == false
        allFail.toEmptyResult(true).errorMessages == [errMsg]

        allFail.toEmptyResult(false).success == false
        allFail.toEmptyResult(false).errorMessages == [errMsg]
    }

    void "test converting some successes and some failures"() {
        given:
        String successMsg = TestUtils.randString()
        String errMsg = TestUtils.randString()
        Result res = Result.createSuccess(successMsg)
        Result failRes = Result.createError([errMsg], ResultStatus.UNPROCESSABLE_ENTITY)
        ResultGroup mixed = new ResultGroup([res, failRes])

        expect:
        mixed.toResult(true).success == true
        mixed.toResult(true).payload == [successMsg]

        mixed.toResult(false).success == false
        mixed.toResult(false).errorMessages == [errMsg]

        and:
        mixed.toEmptyResult(true).success == true
        mixed.toEmptyResult(true).payload == null

        mixed.toEmptyResult(false).success == false
        mixed.toEmptyResult(false).errorMessages == [errMsg]
    }

    void "test converting empty group"() {
        given:
        ResultGroup empty = new ResultGroup()

        expect:
        empty.toResult(true).success == true
        empty.toResult(true).payload == []

        empty.toResult(false).success == true
        empty.toResult(false).payload == []

        and:
        empty.toEmptyResult(true).success == true
        empty.toEmptyResult(true).payload == null

        empty.toEmptyResult(false).success == true
        empty.toEmptyResult(false).payload == null
    }
}
