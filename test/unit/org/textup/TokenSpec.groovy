package org.textup

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import grails.validation.ValidationErrors
import org.springframework.context.MessageSource
import org.textup.types.TokenType
import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Specification
import org.textup.validator.PhoneNumber

@Domain(Token)
@TestMixin(HibernateTestMixin)
class TokenSpec extends Specification {

    void "test constraints"() {
    	when: "we have an empty token"
    	Token token = new Token()

    	then: "invalid"
    	token.validate() == false
    	token.errors.errorCount == 3

    	when: "invalid data for password reset"
    	token = new Token(token:"testing123", type:TokenType.PASSWORD_RESET)
    	token.data = [randomThing: 123]

    	then:
    	token.validate() == false
    	token.errors.errorCount == 1

    	when: "valid data for password reset"
    	token.data = [toBeResetId: 88L]

    	then:
    	token.validate() == true

    	when: "invalid data for verify number"
    	token = new Token(token:"testing123", type:TokenType.VERIFY_NUMBER)
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

    void "test expiration"() {
        when: "a valid token"
        Token token = new Token(token:"testing123",
        	type: TokenType.PASSWORD_RESET)
        token.data = [toBeResetId:88L]
        assert token.validate()

        then: "not expired"
        token.isExpired == false

        when: "we manually expire"
        token.expireNow()

        then: "expired"
        token.isExpired == true
    }
}
