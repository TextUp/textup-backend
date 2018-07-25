package org.textup

import grails.compiler.GrailsCompileStatic
import grails.transaction.Transactional
import groovy.transform.TypeCheckingMode
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.springframework.context.i18n.LocaleContextHolder as LCH
import org.springframework.context.MessageSource
import org.springframework.transaction.TransactionDefinition
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
        generate(TokenType.PASSWORD_RESET, [toBeResetId:s1.id], 1)
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
        generate(TokenType.VERIFY_NUMBER, [toVerifyNumber:toNum.number])
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
        generate(TokenType.NOTIFY_STAFF, tokenData, maxNumAccess)
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

    // Note that this must return an ALREADY-SAVED token so that we don't have a
    // race condition in which the callback from the started call reaches the app before
    // the transaction finishes and the token is saved
    @Transactional(TransactionDefinition.PROPAGATION_REQUIRES_NEW)
    Token tryBuildAndPersistCallToken(String identifier, OutgoingMessage msg1) {
        // [FUTURE] right now, the only available media type is `IMAGE` so if we have no images
        // to send and only text, then even if the OutgoingMessage type is a call, we will still
        // send as a text message. HOWEVER, in the future, when we add in audio recording
        // capability, then we need to revisit this method because we will want to send the images
        // as a text message and the audio recordings over phone call
        if (!msg1.isText && msg1.message) {
            Result<Token> res = generate(TokenType.CALL_DIRECT_MESSAGE, [
                identifier: identifier,
                message: msg1.message,
                // cannot have language be of type VoiceLanguage because this hook is called
                // after the the TextUp user picks up the call and we must serialize the
                // parameters that are then passed back to TextUp by Twilio after pickup
                language: msg1.lang?.toString()
            ])
            if (!res.success) {
                log.error("Token.tryBuildAndPersistCallToken: ${res.errorMessages}")
            }
            res.success ? res.payload : null
        }
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

    Closure buildCallDirectMessageBody(Closure<String> getMessage, Closure<String> getLink,
        String token = null, Integer repeatsSoFar = null) {

        if (!token) { return }
        Result<Token> res = findToken(TokenType.CALL_DIRECT_MESSAGE, token)
        if (!res.success) { return }

        int repeatCount = repeatsSoFar ?: 0
        if (repeatCount < Constants.MAX_REPEATS) {
            Token tok1 = res.payload
            Map<String, ?> tData = tok1.data
            VoiceLanguage lang = Helpers.convertEnum(VoiceLanguage, tData.language)
            String messageIntro = getMessage("twimlBuilder.call.messageIntro", [tData.identifier]),
                repeatWebhook = getLink([
                    handle: CallResponse.DIRECT_MESSAGE,
                    token: tok1.token,
                    repeatCount: repeatCount + 1
                ])
            buildCallResponse(messageIntro, tData.message, lang, repeatWebhook)
        }
        else { buildCallEnd() }
    }
    @GrailsCompileStatic(TypeCheckingMode.SKIP)
    protected Closure buildCallResponse(String intro, String message, VoiceLanguage lang,
        String repeatWebhook) {

        return {
            Say(intro)
            Pause(length:"1")
            if (lang) {
                Say(language:lang.toTwimlValue(), message)
            }
            else { Say(message) }
            Redirect(repeatWebhook)
        }
    }
    @GrailsCompileStatic(TypeCheckingMode.SKIP)
    protected Closure buildCallEnd() { return { Hangup() } }

    // Helpers
    // -------

    protected Result<Token> generate(TokenType type, Map data, Integer maxNumAccess = null) {
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
