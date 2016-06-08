package org.textup

import grails.plugin.springsecurity.SpringSecurityService
import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestFor
import grails.test.mixin.TestMixin
import grails.test.runtime.FreshRuntime
import grails.validation.ValidationErrors
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.joda.time.DateTime
import org.springframework.context.MessageSource
import org.textup.types.OrgStatus
import org.textup.types.ResultType
import org.textup.types.StaffStatus
import org.textup.types.TokenType
import org.textup.util.CustomSpec
import org.textup.validator.BasePhoneNumber
import org.textup.validator.PhoneNumber
import spock.lang.Shared
import static org.springframework.http.HttpStatus.*

@TestFor(TokenService)
@Domain([Contact, Phone, ContactTag, ContactNumber, Record, RecordItem, RecordText,
    RecordCall, RecordItemReceipt, SharedContact, Staff, Team, Organization,
    Schedule, Location, WeeklySchedule, PhoneOwnership, Token, Role, StaffRole])
@TestMixin(HibernateTestMixin)
class TokenServiceSpec extends CustomSpec {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        setupData()
        service.grailsApplication = [getFlatConfig:{
            [
                "textup.resetTokenSize":10,
                "textup.verifyTokenSize":5
            ]
        }] as GrailsApplication
        service.resultFactory = getResultFactory()
        service.mailService = [notifyPasswordReset: { Staff s1, String token ->
            new Result(type:ResultType.SUCCESS, success:true, payload:token)
        }] as MailService
        service.messageSource = [getMessage: { String code, Object[] params, Locale loc ->
            code
        }] as MessageSource
        service.textService = [send: { BasePhoneNumber fromNum,
            List<? extends BasePhoneNumber> toNums, String message ->
            new Result(success:true)
        }] as TextService
    }

    def cleanup() {
        cleanupData()
    }

    // Helpers
    // -------

    void "test generating new token"() {
        when: "try to generate a password reset token"
        Result<Token> res = service.generate(TokenType.PASSWORD_RESET, [toBeResetId:88L])

        then:
        res.success == true
        res.payload instanceof Token

        when: "try to generate a verify number token"
        PhoneNumber pNum = new PhoneNumber(number:'1112223333')
        assert pNum.validate()
        res = service.generate(TokenType.VERIFY_NUMBER, [toVerifyNumber:pNum.number])

        then:
        res.success == true
        res.payload instanceof Token

        when: 'try to generate but invalid'
        res = service.generate(TokenType.VERIFY_NUMBER, [randomStuff: 123])

        then:
        res.success == false
        res.payload instanceof ValidationErrors
        res.payload.errorCount == 1
    }

    @FreshRuntime
    void "test finding token"() {
        given: "saved reset and verify tokens"
        Token reset = new Token(type:TokenType.PASSWORD_RESET, token:'blah'),
            verify = new Token(type:TokenType.VERIFY_NUMBER, token:'blah2')
        reset.data = [toBeResetId:88L]
        verify.data = [toVerifyNumber:'1112223333']
        assert reset.save(failOnError:true)
        assert verify.save(failOnError:true, flush:true)

        when: "looking for an existing password reset token"
        Result<Token> res = service.findToken(TokenType.PASSWORD_RESET, reset.token)

        then:
        res.success == true
        res.payload == reset

        when: "looking for an existing verify number token"
        res = service.findToken(TokenType.VERIFY_NUMBER, verify.token)

        then:
        res.success == true
        res.payload == verify

        when: "looking for a nonexistent token"
        res = service.findToken(TokenType.PASSWORD_RESET, 'nonexistent')

        then:
        res.success == false
        res.payload.status == NOT_FOUND
        res.payload.code == "tokenService.tokenNotFound"

        when: 'looking for an expired token'
        reset.expireNow()
        reset.save(failOnError:true, flush:true)
        res = service.findToken(TokenType.PASSWORD_RESET, reset.token)

        then:
        res.success == false
        res.payload.status == BAD_REQUEST
        res.payload.code == "tokenService.tokenExpired"
    }

    // Verify number
    // -------------

    void "test requesting number verification"() {
        given:
        String nKey = "textup.apiKeys.twilio.notificationNumber"
        PhoneNumber validNum = new PhoneNumber(number:"1112223333"),
            invalidNum = new PhoneNumber(number:"123901")
        assert validNum.validate()
        assert invalidNum.validate() == false

        when: "invalid number to validate"
        Result<Staff> res = service.requestVerify(invalidNum)

        then:
        res.success == false
        res.payload instanceof ValidationErrors
        res.payload.errorCount == 1

        when: "notification number missing"
        service.grailsApplication = [getFlatConfig:{
            [(nKey):null]
        }] as GrailsApplication
        res = service.requestVerify(validNum)

        then:
        res.success == false
        res.payload.status == INTERNAL_SERVER_ERROR
        res.payload.code == "tokenService.requestVerify.notificationNumberMissing"

        when: "invalid notification number"
        service.grailsApplication = [getFlatConfig:{
            [(nKey):"1230"]
        }] as GrailsApplication
        res = service.requestVerify(validNum)

        then:
        res.success == false
        res.payload instanceof ValidationErrors
        res.payload.errorCount == 1

        when: "validating a valid number with notification number present"
        service.grailsApplication = [getFlatConfig:{
            [(nKey):"1112223333"]
        }] as GrailsApplication
        res = service.requestVerify(validNum)

        then:
        res.success == true
    }

    @FreshRuntime
    void "test completing number verification"() {
        given: "valid verify token"
        PhoneNumber pNum = new PhoneNumber(number:"1112223333"),
            pNum2 = new PhoneNumber(number:'1029990000')
        assert pNum.validate()
        assert pNum2.validate()
        Token token = new Token(type:TokenType.VERIFY_NUMBER, token:"tokenname")
        token.data = [toVerifyNumber:pNum.number]
        token.save(flush:true, failOnError:true)

        when: "number does not match number associated with token"
        Result res = service.verifyNumber(token.token, pNum2)

        then:
        res.success == false
        res.payload.status == NOT_FOUND
        res.payload.code == "tokenService.verifyNumber.numbersNoMatch"

        when: "token found and numbers match"
        res = service.verifyNumber(token.token, pNum)

        then:
        res.success == true
    }

    // Password reset
    // --------------

    @FreshRuntime
    void "test requesting password reset"() {
        when: "nonexisting username"
        Result res = service.requestReset("invalid")

        then:
        res.success == false
        res.type == ResultType.MESSAGE_STATUS
        res.payload.status == NOT_FOUND
        res.payload.code == "tokenService.staffNotFound"

        when:
        res = service.requestReset(s1.username)

        then:
        res.success == true
        Token.count() == 1
        Token.list()[0].token == res.payload
        Token.list()[0].data.toBeResetId == s1.id
    }

    void "test completing password reset"() {
        given: "tokens"
        Token tok1 = new Token(token:"superSecretToken", type:TokenType.PASSWORD_RESET),
            expiredTok = new Token(token:"blah", type: TokenType.PASSWORD_RESET,
                expires:DateTime.now().minusDays(1))
        tok1.data = [toBeResetId:s1.id]
        expiredTok.data = [toBeResetId:s1.id]
        [tok1, expiredTok]*.save(flush:true, failOnError:true)

        when: "request with invalid token"
        Result<Staff> res = service.resetPassword("whatisthis", "password")

        then:
        res.success == false
        res.type == ResultType.MESSAGE_STATUS
        res.payload.status == NOT_FOUND
        res.payload.code == "tokenService.tokenNotFound"

        when: "request with valid token but expired"
        res = service.resetPassword(expiredTok.token, "password")

        then:
        res.success == false
        res.type == ResultType.MESSAGE_STATUS
        res.payload.status == BAD_REQUEST
        res.payload.code == "tokenService.tokenExpired"

        when: "valid token, invalid password"
        res = service.resetPassword(tok1.token, "")

        then: "token not yet expired"
        res.success == false
        res.type == ResultType.VALIDATION
        Token.findByToken(tok1.token).isExpired == false

        when:
        String pwd = "iamsospecial!!!!"
        res = service.resetPassword(tok1.token, pwd)

        then:
        res.success == true
        res.payload instanceof Staff
        res.payload.password == pwd
        Token.findByToken(tok1.token).isExpired == true
    }
}
