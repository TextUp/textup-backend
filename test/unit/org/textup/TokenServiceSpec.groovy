package org.textup

import grails.plugin.springsecurity.SpringSecurityService
import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestFor
import grails.test.mixin.TestMixin
import grails.test.runtime.FreshRuntime
import grails.validation.ValidationErrors
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.hibernate.Session
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.springframework.context.MessageSource
import org.textup.type.OrgStatus
import org.textup.type.StaffStatus
import org.textup.type.TokenType
import org.textup.util.CustomSpec
import org.textup.validator.BasePhoneNumber
import org.textup.validator.BasicNotification
import org.textup.validator.Notification
import org.textup.validator.PhoneNumber
import spock.lang.Shared

@TestFor(TokenService)
@Domain([Contact, Phone, ContactTag, ContactNumber, Record, RecordItem, RecordText,
    RecordCall, RecordItemReceipt, SharedContact, Staff, Team, Organization,
    Schedule, Location, WeeklySchedule, PhoneOwnership, Token, Role, StaffRole, NotificationPolicy])
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
        service.messageSource = messageSource
        service.mailService = [notifyPasswordReset: { Staff s1, String token ->
            new Result(status:ResultStatus.OK, payload:token)
        }] as MailService
        service.textService = [send: { BasePhoneNumber fromNum,
            List<? extends BasePhoneNumber> toNums, String message ->
            new Result(status:ResultStatus.OK)
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
        res.status == ResultStatus.OK
        res.payload instanceof Token

        when: "try to generate a verify number token"
        PhoneNumber pNum = new PhoneNumber(number:'1112223333')
        assert pNum.validate()
        res = service.generate(TokenType.VERIFY_NUMBER, null, [toVerifyNumber:pNum.number])

        then:
        res.success == true
        res.status == ResultStatus.OK
        res.payload instanceof Token

        when: 'try to generate but invalid'
        res = service.generate(TokenType.VERIFY_NUMBER, null, [randomStuff: 123])

        then:
        res.success == false
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages.size() == 1
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
        addToMessageSource(["tokenService.tokenNotFound", "tokenService.tokenExpired"])

        when: "looking for an existing password reset token"
        Result<Token> res = service.findToken(TokenType.PASSWORD_RESET, reset.token)

        then:
        res.success == true
        res.status == ResultStatus.OK
        res.payload == reset

        when: "looking for an existing verify number token"
        res = service.findToken(TokenType.VERIFY_NUMBER, verify.token)

        then:
        res.success == true
        res.status == ResultStatus.OK
        res.payload == verify

        when: "looking for a nonexistent token"
        res = service.findToken(TokenType.PASSWORD_RESET, 'nonexistent')

        then:
        res.success == false
        res.status == ResultStatus.NOT_FOUND
        res.errorMessages[0] == "tokenService.tokenNotFound"

        when: 'looking for an expired token'
        reset.maxNumAccess = 1
        reset.timesAccessed = 2
        reset.save(failOnError:true, flush:true)
        res = service.findToken(TokenType.PASSWORD_RESET, reset.token)

        then:
        res.success == false
        res.status == ResultStatus.BAD_REQUEST
        res.errorMessages[0] == "tokenService.tokenExpired"
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
        addToMessageSource(["tokenService.requestVerify.notificationNumberMissing", "tokenService.requestVerify.message"])

        when: "invalid number to validate"
        Result<Staff> res = service.requestVerify(invalidNum)

        then:
        res.success == false
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages.size() == 1

        when: "notification number missing"
        service.grailsApplication = [getFlatConfig:{
            [(nKey):null]
        }] as GrailsApplication
        res = service.requestVerify(validNum)

        then:
        res.success == false
        res.status == ResultStatus.INTERNAL_SERVER_ERROR
        res.errorMessages[0] == "tokenService.requestVerify.notificationNumberMissing"

        when: "invalid notification number"
        service.grailsApplication = [getFlatConfig:{
            [(nKey):"1230"]
        }] as GrailsApplication
        res = service.requestVerify(validNum)

        then:
        res.success == false
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages.size() == 1

        when: "validating a valid number with notification number present"
        service.grailsApplication = [getFlatConfig:{
            [(nKey):"1112223333"]
        }] as GrailsApplication
        res = service.requestVerify(validNum)

        then:
        res.success == true
        res.status == ResultStatus.NO_CONTENT
        res.payload == null
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
        addToMessageSource("tokenService.verifyNumber.numbersNoMatch")

        when: "number does not match number associated with token"
        Result res = service.verifyNumber(token.token, pNum2)

        then:
        res.success == false
        res.status == ResultStatus.NOT_FOUND
        res.errorMessages[0] == "tokenService.verifyNumber.numbersNoMatch"

        when: "token found and numbers match"
        res = service.verifyNumber(token.token, pNum)

        then:
        res.success == true
        res.status == ResultStatus.NO_CONTENT
        res.payload == null
    }

    // Password reset
    // --------------

    @FreshRuntime
    void "test requesting password reset"() {
        given:
        int tBaseline = Token.count()
        addToMessageSource("tokenService.staffNotFound")

        when: "nonexisting username"
        Result res = service.requestReset("invalid")

        then:
        res.success == false
        res.status == ResultStatus.NOT_FOUND
        res.errorMessages[0] == "tokenService.staffNotFound"

        when:
        res = service.requestReset(s1.username)

        then:
        res.success == true
        res.status == ResultStatus.NO_CONTENT
        res.payload == null
        Token.count() == tBaseline + 1
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
        addToMessageSource(["tokenService.tokenNotFound", "tokenService.tokenExpired"])

        when: "request with invalid token"
        Result<Staff> res = service.resetPassword("whatisthis", "password")

        then:
        res.success == false
        res.status == ResultStatus.NOT_FOUND
        res.errorMessages[0] == "tokenService.tokenNotFound"

        when: "request with valid token but expired"
        res = service.resetPassword(expiredTok.token, "password")

        then:
        res.success == false
        res.status == ResultStatus.BAD_REQUEST
        res.errorMessages[0] == "tokenService.tokenExpired"

        when: "valid token, invalid password"
        res = service.resetPassword(tok1.token, "")

        then: "token not yet expired"
        res.success == false
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        Token.findByToken(tok1.token).isExpired == false

        when: "try to reuse the same reset token"
        String pwd = "iamsospecial!!!!"
        res = service.resetPassword(tok1.token, pwd)

        then: "still valid since invalid password before"
        res.success == true
        res.status == ResultStatus.OK
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
        int tBaseline = Token.count()
        DateTime plusOneDay = DateTime.now().plusDays(1).minusMinutes(1),
            plusOneDayAndOneHour = DateTime.now().plusHours(25)
        String contents = "contents"
        String instructions = "instructions"
        Boolean isOutgoing = true
        Result res

        when: "staff without personal phone number"
        assert s1.personalPhoneNumber.number != null
        String personalPhoneNumber = s1.personalPhoneAsString
        s1.personalPhoneAsString = ""
        s1.save(flush:true, failOnError:true)

        BasicNotification bn1 = new BasicNotification(owner:p1.owner, record:c1.record, staff:s1)
        assert bn1.validate() == true
        res = service.notifyStaff(bn1, isOutgoing, contents, instructions)

        then: "short circuited"
        res.success == true
        res.status == ResultStatus.NO_CONTENT
        Token.count() == tBaseline

        when: "staff with a personal phone number"
        s1.personalPhoneAsString = personalPhoneNumber
        s1.save(flush:true, failOnError:true)

        res = service.notifyStaff(bn1, isOutgoing, contents, instructions)
        assert Token.count() == tBaseline + 1
        Token tok = Token.list()[0]

        then: "notification created"
        res.success == true
        res.status == ResultStatus.NO_CONTENT
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
        addToMessageSource(["tokenService.tokenNotFound", "tokenService.tokenExpired"])

        when: "nonexistent token"
        Result<Notification> res = service.showNotification("nonexistent")

        then:
        res.success == false
        res.status == ResultStatus.NOT_FOUND
        res.errorMessages[0] == "tokenService.tokenNotFound"

        when: "expired token"
        res = service.showNotification(expiredTok.token)

        then:
        res.success == false
        res.status == ResultStatus.BAD_REQUEST
        res.errorMessages[0] == "tokenService.tokenExpired"

        when: "about-to-expire valid token"
        res = service.showNotification(tok.token)
        Token.withSession { Session session -> session.flush() }

        then: "valid"
        res.success == true
        res.status == ResultStatus.OK
        res.payload instanceof Notification
        res.payload.validate()
        res.payload.contents == data.contents
        res.payload.tag == null
        res.payload.contact.id == c1.id
        res.payload.owner.phone.id == p1.id

        when: "about-to-expire valid token again"
        res = service.showNotification(tok.token)

        then: "expired"
        res.success == false
        res.status == ResultStatus.BAD_REQUEST
        res.errorMessages[0] == "tokenService.tokenExpired"
    }
}
