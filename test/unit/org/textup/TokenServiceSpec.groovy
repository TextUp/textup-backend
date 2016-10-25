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
import org.joda.time.DateTimeZone
import org.springframework.context.MessageSource
import org.textup.types.OrgStatus
import org.textup.types.ResultType
import org.textup.types.StaffStatus
import org.textup.types.TokenType
import org.textup.util.CustomSpec
import org.textup.validator.BasePhoneNumber
import org.textup.validator.Notification
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

    int _numTimesAccessNotification = 3

    def setup() {
        setupData()
        service.grailsApplication = [getFlatConfig:{
            [
                "textup.resetTokenSize":10,
                "textup.verifyTokenSize":5,
                "textup.numTimesAccessNotification":_numTimesAccessNotification
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
        Result<Token> res = service.generate(TokenType.PASSWORD_RESET, null, [toBeResetId:88L])

        then:
        res.success == true
        res.payload instanceof Token

        when: "try to generate a verify number token"
        PhoneNumber pNum = new PhoneNumber(number:'1112223333')
        assert pNum.validate()
        res = service.generate(TokenType.VERIFY_NUMBER, null, [toVerifyNumber:pNum.number])

        then:
        res.success == true
        res.payload instanceof Token

        when: 'try to generate but invalid'
        res = service.generate(TokenType.VERIFY_NUMBER, null, [randomStuff: 123])

        then:
        res.success == false
        res.payload instanceof ValidationErrors
        res.payload.errorCount == 1
    }

    @FreshRuntime
    void "test finding token"() {
        given: "saved reset and verify tokens"
        Token reset = new Token(type:TokenType.PASSWORD_RESET),
            verify = new Token(type:TokenType.VERIFY_NUMBER)
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
        reset.maxNumAccess = 1
        reset.timesAccessed = 2
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
        Token token = new Token(type:TokenType.VERIFY_NUMBER)
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
        Integer maxNumAccess = 1
        Token tok1 = new Token(type:TokenType.PASSWORD_RESET,
                maxNumAccess:maxNumAccess),
            expiredTok = new Token(type: TokenType.PASSWORD_RESET,
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

        when: "try to reuse the same reset token"
        String pwd = "iamsospecial!!!!"
        res = service.resetPassword(tok1.token, pwd)

        then: "still valid since invalid password before"
        res.success == true
        res.payload instanceof Staff
        res.payload.password == pwd
        // but the token is now expired for future attempts
        Token.findByToken(tok1.token).isExpired == true
    }

    // Notify staff
    // ------------

    @FreshRuntime
    void "test create notification"() {
        given: "no tokens"
        assert Token.count() == 0
        DateTime plusOneDay = DateTime.now().plusDays(1).minusMinutes(1),
            plusOneDayAndOneHour = DateTime.now().plusHours(25)

        when:
        String contents = "contents"
        String instructions = "instructions"
        Boolean isOutgoing = true
        Result res = service.notifyStaff(p1, s1, c1.record.id, isOutgoing,
            contents, instructions)
        assert Token.count() == 1
        Token tok = Token.list()[0]

        then:
        res.success == true
        tok.data.phoneId == p1.id
        tok.data.recordId == c1.record.id
        tok.data.contents == contents
        tok.data.outgoing == isOutgoing
        tok.expires.isAfter(plusOneDay)
        tok.expires.isBefore(plusOneDayAndOneHour)
    }

    void "test claim notification"() {
        given: "about-to-expire valid token and expired token"
        Integer maxNumAccess = 1
        Token tok = new Token(type:TokenType.NOTIFY_STAFF, maxNumAccess:maxNumAccess),
            expiredTok = new Token(type:TokenType.NOTIFY_STAFF,
                maxNumAccess:maxNumAccess, timesAccessed:maxNumAccess * 4)
        Map data = [phoneId:p1.id, recordId:c1.record.id, contents:"hi", outgoing:true]
        tok.data = data
        expiredTok.data = data
        [tok, expiredTok]*.save(flush:true, failOnError:true)

        when: "nonexistent token"
        Result<Notification> res = service.showNotification("nonexistent")

        then:
        res.success == false
        res.type == ResultType.MESSAGE_STATUS
        res.payload.status == NOT_FOUND
        res.payload.code == "tokenService.tokenNotFound"

        when: "expired token"
        res = service.showNotification(expiredTok.token)

        then:
        res.success == false
        res.type == ResultType.MESSAGE_STATUS
        res.payload.status == BAD_REQUEST
        res.payload.code == "tokenService.tokenExpired"

        when: "about-to-expire valid token"
        res = service.showNotification(tok.token)
        tok.save(flush:true, failOnError:true)

        then: "valid"
        res.success == true
        res.payload instanceof Notification
        res.payload.validate()
        res.payload.contents == data.contents
        res.payload.tag == null
        res.payload.contact == c1
        res.payload.owner.phone == p1

        when: "about-to-expire valid token again"
        res = service.showNotification(tok.token)

        then: "expired"
        res.success == false
        res.type == ResultType.MESSAGE_STATUS
        res.payload.status == BAD_REQUEST
        res.payload.code == "tokenService.tokenExpired"
    }
}
