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
        IOCUtils.metaClass."static".getMessageSource = { -> TestUtils.mockMessageSource() }
        service.resultFactory = TestUtils.getResultFactory(grailsApplication)
    }

    void "test start verifying ownership"() {
        given:
        PhoneNumber invalidNum = new PhoneNumber(number: "abc")
        PhoneNumber validNum = new PhoneNumber(number: TestUtils.randPhoneNumberString())
        assert invalidNum.validate() == false
        assert validNum.validate()
        service.tokenService = Mock(TokenService)
        service.textService = Mock(TextService)

        when: "input is not valid"
        Result<Void> res = service.startVerifyOwnership(invalidNum)

        then:
        0 * service.tokenService._
        0 * service.textService._
        res.status == ResultStatus.UNPROCESSABLE_ENTITY

        when:
        Utils.metaClass."static".tryGetNotificationNumber = { ->
            new Result(payload: new PhoneNumber())
        }
        res = service.startVerifyOwnership(validNum)

        then: "custom account id is always null because notif number always with main account"
        1 * service.tokenService.generateVerifyNumber(*_) >> new Result(payload: [token: "hi"] as Token)
        1 * service.textService.send(_, _, "numberService.startVerifyOwnership.message", null) >>
            new Result()
        res.status == ResultStatus.OK
    }

    void "test finish verifying ownership"() {
        given:
        service.tokenService = Mock(TokenService)

        when:
        Result<Void> res = service.finishVerifyOwnership(null, null)

        then:
        1 * service.tokenService.verifyNumber(*_) >> new Result()
        res.status == ResultStatus.OK
    }

    void "test cleaning number search query"() {
        expect:
        service.cleanQuery(null) == ""
        service.cleanQuery("&&&!@#abcABC123") == "abcABC123"
    }
}
