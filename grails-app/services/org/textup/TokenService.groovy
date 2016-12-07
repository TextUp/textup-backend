package org.textup

import grails.compiler.GrailsCompileStatic
import grails.transaction.Transactional
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.springframework.context.i18n.LocaleContextHolder as LCH
import org.springframework.context.MessageSource
import org.textup.types.TokenType
import org.textup.validator.Notification
import org.textup.validator.PhoneNumber
import static org.springframework.http.HttpStatus.*

@GrailsCompileStatic
@Transactional
class TokenService {

    GrailsApplication grailsApplication
    MailService mailService
    ResultFactory resultFactory
    TextService textService
    MessageSource messageSource

    // Initiate
    // -------

	Result requestReset(String username) {
        Staff s1 = Staff.findByUsername(username?.trim()?.toLowerCase())
        if (!s1) {
            return resultFactory.failWithMessageAndStatus(NOT_FOUND,
                "tokenService.staffNotFound", [username])
        }
        else if (!s1.email) {
            return resultFactory.failWithMessageAndStatus(NOT_FOUND,
                "tokenService.requestReset.staffNoEmail")
        }
        generate(TokenType.PASSWORD_RESET, 1, [toBeResetId:s1.id]).then({ Token t1 ->
            mailService.notifyPasswordReset(s1, t1.token)
        }) as Result
    }
    Result requestVerify(PhoneNumber toNum) {
        // validate to number
        if (!toNum.validate()) {
        	return resultFactory.failWithValidationErrors(toNum.errors)
        }
        // build notice number
        String noticeNum = grailsApplication
        	.flatConfig["textup.apiKeys.twilio.notificationNumber"]
        if (!noticeNum) {
        	return resultFactory.failWithMessageAndStatus(INTERNAL_SERVER_ERROR,
                "tokenService.requestVerify.notificationNumberMissing")
        }
        PhoneNumber fromNum = new PhoneNumber(number:noticeNum)
        if (!fromNum.validate()) {
        	return resultFactory.failWithValidationErrors(fromNum.errors)
        }
        // actually generate token
        generate(TokenType.VERIFY_NUMBER, null, [toVerifyNumber:toNum.number]).then({ Token t1 ->
        	String msg = messageSource.getMessage('tokenService.requestVerify.message',
        		[t1.token] as Object[], LCH.getLocale())
            textService.send(fromNum, [toNum], msg)
        }) as Result
    }
    Result notifyStaff(Phone p1, Staff s1, Long recordId,
        Boolean outgoing, String contents, String instructions) {
        // short circuit if staff has no personal phone
        if (!s1.personalPhoneAsString) {
            return resultFactory.success()
        }
        Map tokenData = [
            phoneId: p1.id,
            recordId:recordId,
            contents:contents,
            outgoing:outgoing
        ]
        Integer maxNumAccess = Helpers.toInteger(grailsApplication
            .flatConfig["textup.numTimesAccessNotification"])
        generate(TokenType.NOTIFY_STAFF, maxNumAccess, tokenData).then({ Token t1 ->
            t1.expires = DateTime.now(DateTimeZone.UTC).plusDays(1)
            String notifyLink = grailsApplication
                .flatConfig["textup.links.notifyStaff"]
            String notification = "${instructions} \n\n ${notifyLink + t1.token}"
            textService.send(p1.number, [s1.personalPhoneNumber], notification)
        }) as Result
    }

    // Complete
    // --------

    Result<Staff> resetPassword(String token, String password) {
    	findToken(TokenType.PASSWORD_RESET, token).then({ Token resetToken ->
    		Staff s1 = Staff.get(Helpers.toLong(resetToken.data.toBeResetId))
	        if (!s1) {
	            log.error("tokenService.resetPassword: for token '$token' \
	                with toBeResetId '${resetToken.data.toBeResetId}', \
	                could not find a staff with that id")
	            return resultFactory.failWithMessageAndStatus(INTERNAL_SERVER_ERROR,
	                "tokenService.resetPassword.couldNotComplete")
	        }
	        s1.password = password
	        if (s1.save()) {
                resetToken.timesAccessed++
	            if (resetToken.save()) {
	                resultFactory.success(s1)
	            }
	            else { resultFactory.failWithValidationErrors(resetToken.errors) }
	        }
	        else { resultFactory.failWithValidationErrors(s1.errors) }
		}) as Result<Staff>
    }
    Result verifyNumber(String token, PhoneNumber toVerify) {
    	findToken(TokenType.VERIFY_NUMBER, token).then({ Token tok ->
            PhoneNumber stored =
                new PhoneNumber(number:tok.data.toVerifyNumber?.toString())
            if (!stored.validate()) {
                log.error("tokenService.verifyNumber: for token '$token' \
                    with toVerifyNumber '${tok.data.toVerifyNumber}', \
                    number is invalid: ${stored.errors}")
                return resultFactory.failWithMessageAndStatus(INTERNAL_SERVER_ERROR,
                    "tokenService.verifyNumber.couldNotComplete")
            }
            (stored.number == toVerify.number) ?
                resultFactory.success() :
                resultFactory.failWithMessageAndStatus(NOT_FOUND,
                    "tokenService.verifyNumber.numbersNoMatch")
		}) as Result
    }
    Result<Notification> showNotification(String token) {
        findToken(TokenType.NOTIFY_STAFF, token).then({ Token tok ->
            Map data = tok.data
            Notification notif = new Notification(contents:data.contents as String,
                owner:Phone.get(Helpers.toLong(data.phoneId))?.owner,
                outgoing:Helpers.toBoolean(data.outgoing),
                tokenId:Helpers.toLong(tok.id))
            notif.record = Record.get(Helpers.toLong(data.recordId))
            if (notif.validate()) {
                tok.timesAccessed++
                if (tok.save()) {
                    resultFactory.success(notif)
                }
                else { resultFactory.failWithValidationErrors(tok.errors) }
            }
        }) as Result<Notification>
    }

    // Helpers
    // -------

    protected Result<Token> generate(TokenType type, Integer maxNumAccess, Map data) {
        Token token = new Token(type:type, maxNumAccess:maxNumAccess)
        token.data = data
        if (token.save()) {
            resultFactory.success(token)
        }
        else { resultFactory.failWithValidationErrors(token.errors) }
    }
    protected Result<Token> findToken(TokenType type, String token) {
    	Token t1 = Token.findByTypeAndToken(type, token)
        if (!t1) {
            return resultFactory.failWithMessageAndStatus(NOT_FOUND,
                "tokenService.tokenNotFound", [token])
        }
        else if (t1.isExpired) {
            return resultFactory.failWithMessageAndStatus(BAD_REQUEST,
                "tokenService.tokenExpired")
        }
        resultFactory.success(t1)
    }
}
