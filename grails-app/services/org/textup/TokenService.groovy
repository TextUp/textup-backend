package org.textup

import grails.compiler.GrailsCompileStatic
import grails.transaction.Transactional
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.springframework.context.i18n.LocaleContextHolder as LCH
import org.springframework.context.MessageSource
import org.textup.types.TokenType
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
        generate(TokenType.PASSWORD_RESET, [toBeResetId:s1.id]).then({ Token t1 ->
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
        generate(TokenType.VERIFY_NUMBER, [toVerifyNumber:toNum.number]).then({ Token t1 ->
        	String msg = messageSource.getMessage('tokenService.requestVerify.message',
        		[t1.token] as Object[], LCH.getLocale())
            textService.send(fromNum, [toNum], msg)
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
	            resetToken.expireNow()
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

    // Helpers
    // -------

    protected Result<Token> generate(TokenType type, Map data) {
        String sizeKey = (type == TokenType.PASSWORD_RESET) ?
            "textup.resetTokenSize" :
            "textup.verifyTokenSize"
        Integer tokenSize = Helpers.toInteger(grailsApplication.flatConfig[sizeKey])
        String tokenString = Helpers.randomAlphanumericString(tokenSize)
        //ensure that our generated token is unique
        while (Token.countByToken(tokenString) != 0) {
            tokenString = Helpers.randomAlphanumericString(tokenSize)
        }
        Token token = new Token(token:tokenString, type:type)
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
