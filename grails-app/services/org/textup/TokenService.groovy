package org.textup

import grails.compiler.GrailsCompileStatic
import grails.transaction.Transactional
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.springframework.context.i18n.LocaleContextHolder as LCH
import org.springframework.context.MessageSource
import org.textup.type.TokenType
import org.textup.validator.BasicNotification
import org.textup.validator.Notification
import org.textup.validator.PhoneNumber

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

	Result<Void> requestReset(String username) {
        Staff s1 = Staff.findByUsername(username?.trim()?.toLowerCase())
        if (!s1) {
            return resultFactory.failWithCodeAndStatus("tokenService.staffNotFound",
                ResultStatus.NOT_FOUND, [username])
        }
        else if (!s1.email) {
            return resultFactory.failWithCodeAndStatus("tokenService.requestReset.staffNoEmail",
                ResultStatus.NOT_FOUND)
        }
        generate(TokenType.PASSWORD_RESET, 1, [toBeResetId:s1.id])
            .then({ Token t1 -> mailService.notifyPasswordReset(s1, t1.token) })
            .then({ resultFactory.success() })
    }
    Result<Void> requestVerify(PhoneNumber toNum) {
        // validate to number
        if (!toNum.validate()) {
        	return resultFactory.failWithValidationErrors(toNum.errors)
        }
        // build notice number
        String noticeNum = grailsApplication
        	.flatConfig["textup.apiKeys.twilio.notificationNumber"]
        if (!noticeNum) {
        	return resultFactory.failWithCodeAndStatus(
                "tokenService.requestVerify.notificationNumberMissing",
                ResultStatus.INTERNAL_SERVER_ERROR)
        }
        PhoneNumber fromNum = new PhoneNumber(number:noticeNum)
        if (!fromNum.validate()) {
        	return resultFactory.failWithValidationErrors(fromNum.errors)
        }
        // actually generate token
        generate(TokenType.VERIFY_NUMBER, null, [toVerifyNumber:toNum.number])
            .then({ Token t1 ->
            	String msg = messageSource.getMessage("tokenService.requestVerify.message",
            		[t1.token] as Object[], LCH.getLocale())
                textService
                    .send(fromNum, [toNum], msg)
                    .logFail("TokenService.requestVerify from $fromNum to $toNum")
            })
            .then({ resultFactory.success() })
    }
    Result<Void> notifyStaff(BasicNotification bn1, Boolean outgoing, String contents, String instr) {
        Phone p1 = bn1.owner.phone
        Staff s1 = bn1.staff
        // short circuit if no staff specified or staff has no personal phone
        if (!s1?.personalPhoneAsString) {
            return resultFactory.success()
        }
        Map tokenData = [
            phoneId: p1.id,
            recordId:bn1.record.id,
            contents:contents,
            outgoing:outgoing
        ]
        Integer maxNumAccess = Helpers.to(Integer, grailsApplication
            .flatConfig["textup.numTimesAccessNotification"])
        generate(TokenType.NOTIFY_STAFF, maxNumAccess, tokenData)
            .then({ Token t1 ->
                t1.expires = DateTime.now(DateTimeZone.UTC).plusDays(1)
                String notifyLink = grailsApplication
                    .flatConfig["textup.links.notifyMessage"]
                String notification = "${instr} \n\n ${notifyLink + t1.token}"
                textService
                    .send(p1.number, [s1.personalPhoneNumber], notification)
                    .logFail("TokenService.notifyStaff for data $tokenData")
            })
            .then({ resultFactory.success() })
    }

    // Complete
    // --------

    Result<Staff> resetPassword(String token, String password) {
    	findToken(TokenType.PASSWORD_RESET, token).then({ Token resetToken ->
    		Staff s1 = Staff.get(Helpers.to(Long, resetToken.data.toBeResetId))
	        if (!s1) {
	            log.error("tokenService.resetPassword: for token '$token' \
	                with toBeResetId '${resetToken.data.toBeResetId}', \
	                could not find a staff with that id")
	            return resultFactory.failWithCodeAndStatus("tokenService.resetPassword.couldNotComplete",
                    ResultStatus.INTERNAL_SERVER_ERROR)
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
		})
    }
    Result<Void> verifyNumber(String token, PhoneNumber toVerify) {
    	findToken(TokenType.VERIFY_NUMBER, token).then({ Token tok ->
            PhoneNumber stored =
                new PhoneNumber(number:tok.data.toVerifyNumber?.toString())
            if (!stored.validate()) {
                log.error("tokenService.verifyNumber: for token '$token' \
                    with toVerifyNumber '${tok.data.toVerifyNumber}', \
                    number is invalid: ${stored.errors}")
                return resultFactory.failWithCodeAndStatus("tokenService.verifyNumber.couldNotComplete",
                    ResultStatus.INTERNAL_SERVER_ERROR)
            }
            (stored.number == toVerify.number) ?
                resultFactory.success() :
                resultFactory.failWithCodeAndStatus("tokenService.verifyNumber.numbersNoMatch",
                    ResultStatus.NOT_FOUND)
		})
    }
    Result<Notification> showNotification(String token) {
        findToken(TokenType.NOTIFY_STAFF, token).then({ Token tok ->
            Map data = tok.data
            Notification notif = new Notification(contents:data.contents as String,
                owner:Phone.get(Helpers.to(Long, data.phoneId))?.owner,
                outgoing:Helpers.to(Boolean, data.outgoing),
                tokenId:Helpers.to(Long, tok.id))
            notif.record = Record.get(Helpers.to(Long, data.recordId))
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
            return resultFactory.failWithCodeAndStatus("tokenService.tokenNotFound",
                ResultStatus.NOT_FOUND, [token])
        }
        else if (t1.isExpired) {
            return resultFactory.failWithCodeAndStatus("tokenService.tokenExpired",
                ResultStatus.BAD_REQUEST)
        }
        resultFactory.success(t1)
    }
}
