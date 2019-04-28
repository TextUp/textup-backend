package org.textup.util

import grails.test.mixin.TestFor
import org.textup.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.domain.*
import org.textup.validator.*
import spock.lang.*

@TestFor(NumberService)
class NumberServiceSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test start verifying ownership"() {
        given:
        PhoneNumber pNum1 = TestUtils.randPhoneNumber()
        PhoneNumber invalidNum = PhoneNumber.create("abc")
        PhoneNumber validNum = TestUtils.randPhoneNumber()

        service.tokenService = GroovyMock(TokenService)
        service.textService = GroovyMock(TextService)

        MockedMethod tryGetNotificationNumber = MockedMethod.create(Utils, "tryGetNotificationNumber") {
            Result.createSuccess(pNum1)
        }

        when:
        Result res = service.startVerifyOwnership(null)

        then:
        0 * service.tokenService._
        0 * service.textService._
        res.status == ResultStatus.INTERNAL_SERVER_ERROR

        when:
        res = service.startVerifyOwnership(invalidNum)

        then:
        0 * service.tokenService._
        0 * service.textService._
        res.status == ResultStatus.UNPROCESSABLE_ENTITY

        when:
        res = service.startVerifyOwnership(validNum)

        then: "custom account id is always null because notif number always with main account"
        1 * service.tokenService.generateVerifyNumber(*_) >> Result.createSuccess([token: "hi"] as Token)
        1 * service.textService.send(_, _, "numberService.startVerifyOwnership", null) >> Result.void()
        res.status == ResultStatus.NO_CONTENT

        cleanup:
        tryGetNotificationNumber?.restore()
    }

    void "test finish verifying ownership"() {
        given:
        String str1 = TestUtils.randString()
        PhoneNumber pNum1 = TestUtils.randPhoneNumber()
        PhoneNumber pNum2 = TestUtils.randPhoneNumber()

        service.tokenService = GroovyMock(TokenService)

        when:
        Result res = service.finishVerifyOwnership(null, null)

        then:
        1 * service.tokenService.findVerifyNumber(null) >> Result.createSuccess(pNum1)
        res.status == ResultStatus.NOT_FOUND

        when:
        res = service.finishVerifyOwnership(str1, pNum2)

        then:
        1 * service.tokenService.findVerifyNumber(str1) >> Result.createSuccess(pNum1)
        res.status == ResultStatus.NOT_FOUND

        when:
        res = service.finishVerifyOwnership(str1, pNum2)

        then:
        1 * service.tokenService.findVerifyNumber(str1) >> Result.createSuccess(pNum2)
        res.status == ResultStatus.NO_CONTENT
    }
}
