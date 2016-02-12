package org.textup

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import grails.validation.ValidationErrors
import org.springframework.context.MessageSource
import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Specification

@Domain(PasswordResetToken)
@TestMixin(HibernateTestMixin)
class PasswordResetTokenSpec extends Specification {

    void "test constraints"() {
    	when: "we have an empty token"
    	PasswordResetToken token = new PasswordResetToken()

    	then: "invalid"
    	token.validate() == false
    	token.errors.errorCount == 2

    	when: "we fill out all fields"
    	token = new PasswordResetToken(token:"testing123", toBeResetId:88L)

    	then: "valid"
    	token.validate() == true
    }

    void "test expiration"() {
        when: "a valid token"
        PasswordResetToken token = new PasswordResetToken(token:"testing123",
            toBeResetId:88L)
        assert token.validate()

        then: "not expired"
        token.isExpired == false

        when: "we manually expire"
        token.expireNow()

        then: "expired"
        token.isExpired == true
    }
}
