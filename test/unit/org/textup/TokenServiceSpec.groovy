package org.textup

import org.textup.test.*
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
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*
import spock.lang.Shared

@TestFor(TokenService)
@Domain([Contact, Phone, ContactTag, ContactNumber, Record, RecordItem, RecordText,
    RecordCall, RecordItemReceipt, SharedContact, Staff, Team, Organization,
    Schedule, Location, WeeklySchedule, PhoneOwnership, Token, Role, StaffRole, NotificationPolicy,
    MediaInfo, MediaElement, MediaElementVersion])
@TestMixin(HibernateTestMixin)
class TokenServiceSpec extends CustomSpec {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        setupData()
        service.resultFactory = TestUtils.getResultFactory(grailsApplication)
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
        res.status == ResultStatus.OK
        res.payload instanceof Token

        when: "try to generate a verify number token"
        PhoneNumber pNum = new PhoneNumber(number:'1112223333')
        assert pNum.validate()
        res = service.generate(TokenType.VERIFY_NUMBER, [toVerifyNumber:pNum.number])

        then:
        res.success == true
        res.status == ResultStatus.OK
        res.payload instanceof Token

        when: "try to generate but invalid"
        res = service.generate(TokenType.VERIFY_NUMBER, [randomStuff: 123])

        then:
        res.success == false
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages.size() == 1
    }

    void "test finding token"() {
        given: "saved reset and verify tokens"
        Token reset = new Token(type:TokenType.PASSWORD_RESET),
            verify = new Token(type:TokenType.VERIFY_NUMBER)
        reset.data = [toBeResetId:88L]
        verify.data = [toVerifyNumber:"1112223333"]
        assert reset.save(failOnError:true)
        assert verify.save(failOnError:true, flush:true)

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
        PhoneNumber validNum = new PhoneNumber(number:"1112223333")
        PhoneNumber invalidNum = new PhoneNumber(number:"123901")
        assert validNum.validate()
        assert invalidNum.validate() == false
        int tBaseline = Token.count()

        when: "invalid number to validate"
        Result<Staff> res = service.generateVerifyNumber(invalidNum)

        then: "verification happens in calling service"
        res.status == ResultStatus.OK
        res.payload instanceof Token
        res.payload.type == TokenType.VERIFY_NUMBER
        res.payload.maxNumAccess == null
        Token.count() == tBaseline + 1

        when: "validating a valid number"
        res = service.generateVerifyNumber(validNum)

        then:
        res.success == true
        res.status == ResultStatus.OK
        res.payload instanceof Token
        res.payload.type == TokenType.VERIFY_NUMBER
        res.payload.maxNumAccess == null
        Token.count() == tBaseline + 2
    }

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
        res.status == ResultStatus.NOT_FOUND
        res.errorMessages[0] == "tokenService.verifyNumber.numbersNoMatch"

        when: "token found and numbers match"
        res = service.verifyNumber(token.token, pNum)

        then:
        res.success == true
        res.status == ResultStatus.NO_CONTENT
        res.payload == null

        when: "stored number to validate is invalid"
        token.data = [toVerifyNumber: "invalid number"]
        token.save(flush:true, failOnError:true)
        res = service.verifyNumber(token.token, pNum)

        then:
        res.status == ResultStatus.INTERNAL_SERVER_ERROR
        res.errorMessages[0] == "tokenService.verifyNumber.couldNotComplete"
    }

    // Password reset
    // --------------

    void "test requesting password reset"() {
        given:
        int tBaseline = Token.count()

        when: "nonexisting username"
        long invalidId = -88
        Result<Token> res = service.generatePasswordReset(invalidId)

        then: "username validation in calling service"
        res.status == ResultStatus.OK
        res.payload instanceof Token
        res.payload.maxNumAccess == 1
        Token.count() == tBaseline + 1
        Token.list().last().data.toBeResetId == invalidId

        when:
        res = service.generatePasswordReset(s1.id)

        then:
        res.status == ResultStatus.OK
        res.payload instanceof Token
        res.payload.maxNumAccess == 1
        Token.count() == tBaseline + 2
        Token.list().last().data.toBeResetId == s1.id
    }

    void "test completing password reset"() {
        given: "tokens"
        Integer maxNumAccess = 1
        Token tok1 = new Token(type:TokenType.PASSWORD_RESET, maxNumAccess:maxNumAccess),
            tok2 = new Token(type:TokenType.PASSWORD_RESET, maxNumAccess:maxNumAccess),
            expiredTok = new Token(type: TokenType.PASSWORD_RESET,
                expires:DateTime.now().minusDays(1))
        tok1.data = [toBeResetId: s1.id]
        tok2.data = [toBeResetId: -88]
        expiredTok.data = [toBeResetId: s1.id]
        [tok1, tok2, expiredTok]*.save(flush:true, failOnError:true)

        when: "request with invalid token"
        Result<Staff> res = service.findPasswordResetStaff("whatisthis")

        then:
        res.success == false
        res.status == ResultStatus.NOT_FOUND
        res.errorMessages[0] == "tokenService.tokenNotFound"

        when: "request with valid token but expired"
        res = service.findPasswordResetStaff(expiredTok.token)

        then:
        res.success == false
        res.status == ResultStatus.BAD_REQUEST
        res.errorMessages[0] == "tokenService.tokenExpired"

        when: "valid token, invalid password"
        res = service.findPasswordResetStaff(tok1.token)

        then: "token not yet expired"
        res.success == true
        res.status == ResultStatus.OK
        res.payload instanceof Staff

        when: "try to reuse the same reset token"
        res = service.findPasswordResetStaff(tok1.token)

        then: "one-time use only"
        res.status == ResultStatus.BAD_REQUEST
        res.errorMessages[0] == "tokenService.tokenExpired"

        when: "token has a nonexistent staff id"
        res = service.findPasswordResetStaff(tok2.token)

        then:
        res.status == ResultStatus.INTERNAL_SERVER_ERROR
        res.errorMessages[0] == "tokenService.resetPassword.couldNotComplete"
    }

    // Notify staff
    // ------------

    void "test create notification"() {
        given: "no tokens"
        int tBaseline = Token.count()

        when: "staff without personal phone number"
        Result<Token> res = service.generateNotification(null)

        then:
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        Token.count() == tBaseline

        when: "staff with a personal phone number"
        res = service.generateNotification(recordId: 1, phoneId: 2, contents: 3, outgoing: 4)

        then: "notification created"
        Token.count() == tBaseline + 1
        res.status == ResultStatus.OK
        res.payload instanceof Token
        Constants.MAX_NUM_ACCESS_NOTIFICATION_TOKEN == res.payload.maxNumAccess
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
        Result<Notification> res = service.findNotification("nonexistent")

        then:
        res.success == false
        res.status == ResultStatus.NOT_FOUND
        res.errorMessages[0] == "tokenService.tokenNotFound"

        when: "expired token"
        res = service.findNotification(expiredTok.token)

        then:
        res.success == false
        res.status == ResultStatus.BAD_REQUEST
        res.errorMessages[0] == "tokenService.tokenExpired"

        when: "about-to-expire valid token"
        int originalTimesAccessed = tok.timesAccessed
        res = service.findNotification(tok.token)
        Token.withSession { Session session -> session.flush() }

        then: "valid"
        res.status == ResultStatus.OK
        res.payload instanceof Token
        res.payload.id == tok.id
        res.payload.timesAccessed == originalTimesAccessed + 1

        when: "about-to-expire valid token again"
        res = service.findNotification(tok.token)

        then: "expired"
        res.success == false
        res.status == ResultStatus.BAD_REQUEST
        res.errorMessages[0] == "tokenService.tokenExpired"
    }

    // Call direct message
    // -------------------

    void "test building call direct message token"() {
        given:
        MediaInfo mInfo = new MediaInfo()
        mInfo.save(flush: true, failOnError: true)

        when: "no input data"
        Token tok1 = service.tryBuildAndPersistCallToken(null, null)

        then: "null result"
        tok1 == null

        when: "message is of text type"
        OutgoingMessage msg1 = new OutgoingMessage(message: "hi",
            type: RecordItemType.TEXT,
            language: VoiceLanguage.ENGLISH)
        tok1 = service.tryBuildAndPersistCallToken("hi", msg1)

        then: "no result because no need to build call token for text message"
        tok1 == null

        when: "valid call message without media"
        msg1.type = RecordItemType.CALL
        tok1 = service.tryBuildAndPersistCallToken("hi", msg1)

        then: "token generated"
        tok1 instanceof Token
        tok1.data.identifier == "hi"
        tok1.data.message == msg1.message
        tok1.data.mediaId == null
        tok1.data.language == msg1.language?.toString()

        when: "valid call message with media"
        msg1.type = RecordItemType.CALL
        msg1.media = mInfo
        tok1 = service.tryBuildAndPersistCallToken("hi", msg1)

        then: "token generated"
        tok1 instanceof Token
        tok1.data.identifier == "hi"
        tok1.data.message == msg1.message
        tok1.data.mediaId == mInfo.id
        tok1.data.language == msg1.language?.toString()
    }

    void "test do not build call token when is call but insufficent info"() {
        given:
        MediaInfo mInfo = new MediaInfo()
        mInfo.save(flush: true, failOnError: true)
        OutgoingMessage msg1 = new OutgoingMessage(type: RecordItemType.CALL, media: mInfo)

        when:
        Token tok1 = service.tryBuildAndPersistCallToken("hi", msg1)

        then: "not returned because need a message or audio recording"
        tok1 == null
    }

    void "test building call direct message body from token"() {
        given:
        Token tok1 = new Token(type:TokenType.CALL_DIRECT_MESSAGE, maxNumAccess: 1)
        tok1.data = [
            message: "hi",
            identifier: "Kiki",
            mediaId: null,
            language: VoiceLanguage.ENGLISH.toString()
        ]
        tok1.save(flush: true, failOnError: true)

        when: "null token"
        Result<Token> res = service.findDirectMessage(null)

        then: "return null --> will trigger error in twimlBuilder"
        res.status == ResultStatus.NOT_FOUND
        res.errorMessages[0] == "tokenService.tokenNotFound"

        when: "nonexistent token"
        res = service.findDirectMessage("i don't exist")

        then: "return null --> will trigger error in twimlBuilder"
        res.status == ResultStatus.NOT_FOUND
        res.errorMessages[0] == "tokenService.tokenNotFound"

        when: "existing token, but too many repeats"
        int originalTimesAccessed = tok1.timesAccessed
        res = service.findDirectMessage(tok1.token)
        Token.withSession { Session session -> session.flush() }

        then:
        res.status == ResultStatus.OK
        res.payload instanceof Token
        res.payload.id == tok1.id
        res.payload.timesAccessed == originalTimesAccessed + 1

        when:
        res = service.findDirectMessage(tok1.token)

        then:
        res.status == ResultStatus.BAD_REQUEST
        res.errorMessages[0] == "tokenService.tokenExpired"
    }
}
