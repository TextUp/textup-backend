package org.textup

import grails.test.mixin.*
import grails.test.mixin.gorm.*
import grails.test.mixin.hibernate.*
import grails.test.mixin.support.*
import grails.test.mixin.web.ControllerUnitTestMixin
import grails.test.runtime.*
import grails.validation.*
import org.joda.time.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*
import spock.lang.*

@Domain([CustomAccountDetails, Token, Organization, Location])
@TestMixin([HibernateTestMixin, ControllerUnitTestMixin])
class TokenSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test creation + constraints using TokenType.PASSWORD_RESET"() {
        given:
        TokenType type = TokenType.PASSWORD_RESET
        Map invalidData = [:]
        Map data = TokenType.passwordResetData(TestUtils.randIntegerUpTo(88))

        when:
        Result res = Token.tryCreate(null, null)

        then:
        res.status == ResultStatus.UNPROCESSABLE_ENTITY

        when:
        res = Token.tryCreate(type, invalidData)

        then:
        res.status == ResultStatus.UNPROCESSABLE_ENTITY

        when:
        res = Token.tryCreate(type, data)

        then:
        res.status == ResultStatus.CREATED
        res.payload.type == type
        res.payload.token instanceof String
        res.payload.data == data
    }

    void "test data formatting for TokenType.VERIFY_NUMBER"() {
        given:
        TokenType type = TokenType.VERIFY_NUMBER
        Map data = TokenType.verifyNumberData(TestUtils.randPhoneNumber())

        when:
        Result res = Token.tryCreate(type, data)

        then:
        res.status == ResultStatus.CREATED
        res.payload.type == type
        res.payload.token instanceof String
        res.payload.data == data
    }

    void "test data formatting for TokenType.NOTIFY_STAFF"() {
        given:
        TokenType type = TokenType.NOTIFY_STAFF
        Map data = TokenType.notifyStaffData(TestUtils.randIntegerUpTo(88),
            [TestUtils.randIntegerUpTo(88), TestUtils.randIntegerUpTo(88)],
            TestUtils.randIntegerUpTo(88))

        when:
        Result res = Token.tryCreate(type, data)

        then:
        res.status == ResultStatus.CREATED
        res.payload.type == type
        res.payload.token instanceof String
        res.payload.data == data
    }

    void "test data formatting for TokenType.CALL_DIRECT_MESSAGE"() {
        given:
        TokenType type = TokenType.CALL_DIRECT_MESSAGE
        Map data = TokenType.callDirectMessageData(TestUtils.randString(),
            VoiceLanguage.values()[0],
            TestUtils.randString(),
            TestUtils.randIntegerUpTo(88))

        when:
        Result res = Token.tryCreate(type, data)

        then:
        res.status == ResultStatus.CREATED
        res.payload.type == type
        res.payload.token instanceof String
        res.payload.data == data
    }

    void "test expiration"() {
        when: "a valid token without maxNumAccess"
        Token tok1 = Token
            .tryCreate(TokenType.PASSWORD_RESET,
                TokenType.passwordResetData(TestUtils.randIntegerUpTo(88)))
            .payload

        then: "not expired"
        tok1.isExpired == false

        when: "a valid token with negative maxNumAccess"
        tok1.maxNumAccess = -1
        tok1.timesAccessed = 1

        then: "not expired"
        tok1.isExpired == false

        when: "valid token with zero maxNumAccess"
        tok1.maxNumAccess = 0
        tok1.timesAccessed = 1

        then: "not expired"
        tok1.isExpired == false

        when: "valid token with positive maxNumAccess"
        tok1.maxNumAccess = 2
        tok1.timesAccessed = 1

        then: "no expired"
        tok1.isExpired == false

        when: "accessed too many times"
        tok1.maxNumAccess = 2
        tok1.timesAccessed = 4

        then: "expired"
        tok1.isExpired == true
    }
}
