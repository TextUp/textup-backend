package org.textup

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import groovy.transform.TypeCheckingMode
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.springframework.transaction.annotation.Propagation
import org.textup.type.*
import org.textup.util.RollbackOnResultFailure
import org.textup.validator.*

@GrailsTypeChecked
@Transactional
class TokenService {

    GrailsApplication grailsApplication
    ResultFactory resultFactory

    // Initiate
    // --------

	Result<Token> generatePasswordReset(Long staffId) {
        generate(TokenType.PASSWORD_RESET, [toBeResetId: staffId], 1)
    }

    Result<Token> generateVerifyNumber(PhoneNumber toNum) {
        generate(TokenType.VERIFY_NUMBER, [toVerifyNumber: toNum.number])
    }

    Result<Token> generateNotification(Map tokenData) {
        generate(TokenType.NOTIFY_STAFF, tokenData, Constants.MAX_NUM_ACCESS_NOTIFICATION_TOKEN)
            .then { Token t1 ->
                t1.expires = DateTime.now(DateTimeZone.UTC).plusDays(1)
                resultFactory.success(t1)
            }
    }

    // Note that this must return an ALREADY-SAVED token so that we don't have a
    // race condition in which the callback from the started call reaches the app before
    // the transaction finishes and the token is saved
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    Token tryBuildAndPersistCallToken(String identifier, OutgoingMessage msg1) {
        // [FUTURE] right now, the only available media type is `IMAGE` so if we have no images
        // to send and only text, then even if the OutgoingMessage type is a call, we will still
        // send as a text message. HOWEVER, in the future, when we add in audio recording
        // capability, then we need to revisit this method because we will want to send the images
        // as a text message and the audio recordings over phone call
        if (identifier && !msg1.isText && msg1.message) {
            Result<Token> res = generate(TokenType.CALL_DIRECT_MESSAGE, [
                identifier: identifier,
                message: msg1.message,
                // cannot have language be of type VoiceLanguage because this hook is called
                // after the the TextUp user picks up the call and we must serialize the
                // parameters that are then passed back to TextUp by Twilio after pickup
                language: msg1.language?.toString()
            ], 1)
            if (!res.success) {
                log.error("Token.tryBuildAndPersistCallToken: ${res.errorMessages}")
            }
            res.success ? res.payload : null
        }
    }

    // Complete
    // --------

    Result<Staff> findPasswordResetStaff(String token) {
    	findToken(TokenType.PASSWORD_RESET, token).then({ Token resetToken ->
            resetToken.timesAccessed++
            if (!resetToken.save()) {
                return resultFactory.failWithValidationErrors(resetToken.errors)
            }
    		Staff s1 = Staff.get(Helpers.to(Long, resetToken.data.toBeResetId))
	        if (s1) {
                resultFactory.success(s1)
	        }
            else {
                log.error("tokenService.resetPassword: for token '$token' \
                    with toBeResetId '${resetToken.data.toBeResetId}', \
                    could not find a staff with that id")
                resultFactory.failWithCodeAndStatus("tokenService.resetPassword.couldNotComplete",
                    ResultStatus.INTERNAL_SERVER_ERROR)
            }
		})
    }

    Result<Void> verifyNumber(String token, PhoneNumber toVerify) {
    	findToken(TokenType.VERIFY_NUMBER, token).then({ Token tok ->
            PhoneNumber stored = new PhoneNumber(number:tok.data.toVerifyNumber?.toString())
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

    Result<Token> findNotification(String token) {
        findToken(TokenType.NOTIFY_STAFF, token).then { Token tok ->
            tok.timesAccessed++
            if (tok.save()) {
                resultFactory.success(tok)
            }
            else { resultFactory.failWithValidationErrors(tok.errors) }
        }
    }

    Result<Token> findDirectMessage(String token) {
        findToken(TokenType.CALL_DIRECT_MESSAGE, token)
    }

    // Helpers
    // -------

    protected Result<Token> generate(TokenType type, Map data, Integer maxNumAccess = null) {
        Token token = new Token(type: type, maxNumAccess: maxNumAccess)
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
