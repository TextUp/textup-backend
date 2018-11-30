package org.textup

import org.textup.test.*
import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import grails.validation.ValidationErrors
import java.util.UUID
import org.springframework.context.MessageSource
import org.textup.type.*
import org.textup.validator.PhoneNumber
import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Specification

@Domain([Token, Organization, Location])
@TestMixin(HibernateTestMixin)
class TokenSpec extends Specification {

    void "test constraints"() {
    	when: "we have an empty token"
    	Token token = new Token()

    	then: "invalid but token is auto-populated"
    	token.validate() == false
    	token.errors.errorCount == 2
        token.errors.getFieldErrorCount('token') == 0

        when: "we try to set token property directly"
        String customVal = UUID.randomUUID().toString()
        token.token = customVal

        then: "can do so"
        token.token == customVal

        when: "we try to set stringData property directly"
        token.stringData = customVal

        then: "can do so"
        token.stringData == customVal
    }

    void "test data formatting for TokenType.PASSWORD_RESET"() {
        when: "invalid data for password reset"
        Token token = new Token(type:TokenType.PASSWORD_RESET)
        token.data = [randomThing: 123]

        then:
        token.validate() == false
        token.errors.errorCount == 1

        when: "valid data for password reset"
        token.data = [toBeResetId: 88L]

        then:
        token.validate() == true
    }

    void "test data formatting for TokenType.VERIFY_NUMBER"() {
        when: "invalid data for verify number"
        Token token = new Token(type:TokenType.VERIFY_NUMBER)
        token.data = [randomThing: 123]

        then:
        token.validate() == false
        token.errors.errorCount == 1

        when: "valid data for verify number"
        PhoneNumber num = new PhoneNumber(number:"1112223333")
        assert num.validate()
        token.data = [toVerifyNumber: num.number]

        then:
        token.validate() == true
    }

    void "test data formatting for TokenType.NOTIFY_STAFF"() {
        when: "invalid data for notification"
        Token token = new Token(type:TokenType.NOTIFY_STAFF)
        token.data = [randomThing: 123]

        then:
        token.validate() == false
        token.errors.errorCount == 1

        when: "valid data for notification"
        token.data = [recordId:88L, phoneId:88L, contents:'hi', outgoing:true]

        then:
        token.validate() == true
    }

    void "test data formatting for TokenType.CALL_DIRECT_MESSAGE"() {
        when: "invalid data for call direct message"
        Token token = new Token(type:TokenType.CALL_DIRECT_MESSAGE)
        token.data = [randomThing: 123]

        then:
        token.validate() == false
        token.errors.errorCount == 1

        when: "valid data for call direct message"
        token.data = [
            message: "hi",
            identifier: "Kiki",
            mediaId: 88,
            language: VoiceLanguage.ENGLISH.toString()
        ]

        then:
        token.validate() == true
    }

    void "test expiration"() {
        when: "a valid token without maxNumAccess"
        Token token = new Token(type: TokenType.PASSWORD_RESET)
        token.data = [toBeResetId:88L]
        assert token.validate()

        then: "not expired"
        token.isExpired == false

        when: "a valid token with negative maxNumAccess"
        token.maxNumAccess = -1
        token.timesAccessed = 1
        assert token.validate()

        then: "not expired"
        token.isExpired == false

        when: "valid token with zero maxNumAccess"
        token.maxNumAccess = 0
        token.timesAccessed = 1
        assert token.validate()

        then: "not expired"
        token.isExpired == false

        when: "valid token with positive maxNumAccess"
        token.maxNumAccess = 2
        token.timesAccessed = 1
        assert token.validate()

        then: "no expired"
        token.isExpired == false

        when: "accessed too many times"
        token.maxNumAccess = 2
        token.timesAccessed = 4
        assert token.validate()

        then: "expired"
        token.isExpired == true
    }
}
