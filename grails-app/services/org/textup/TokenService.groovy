package org.textup

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import groovy.transform.TypeCheckingMode
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.springframework.transaction.annotation.Propagation
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*

@GrailsTypeChecked
@Transactional
class TokenService {

    // Initiate
    // --------

	Result<Token> generatePasswordReset(Long staffId) {
        Token.tryCreate(TokenType.PASSWORD_RESET, [toBeResetId: staffId])
            .then { Token tok1 ->
                tok1.maxNumAccess = 1
                DomainUtils.trySave(tok1, ResultStatus.CREATED)
            }
    }

    Result<Token> generateVerifyNumber(PhoneNumber toNum) {
        Token.tryCreate(TokenType.VERIFY_NUMBER, [toVerifyNumber: toNum.number])
    }

    // TODO
    Result<Token> generateNotification(OutgoingNotification notif1) {
        Map tokenData
        Token.tryCreate(TokenType.NOTIFY_STAFF, tokenData)
            .then { Token tok1 ->
                tok1.maxNumAccess = ValidationUtils.MAX_NUM_ACCESS_NOTIFICATION_TOKEN
                tok1.expires = DateTimeUtils.now().plusDays(1)
                IOCUtils.resultFactory.success(tok1)
            }
    }

    // Note that this must return an ALREADY-SAVED token so that we don't have a
    // race condition in which the callback from the started call reaches the app before
    // the transaction finishes and the token is saved
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    Result<Token> tryBuildAndPersistCallToken(RecordItemType type, Recipients r1,
        TempRecordItem temp1) {

        // Store media id because we send the images as text and the audio over phone call
        // Only need to start phone all if the message is a call that has either a
        // written message read in a robo-voice or at least one audio recording
        if (type == RecordItemType.CALL && temp1.supportsCall()) {
            Token.tryCreate(TokenType.CALL_DIRECT_MESSAGE, [
                    identifier: r1.buildFromName(),
                    message: temp1.text,
                    mediaId: temp1.media?.id,
                    // cannot have language be of type VoiceLanguage because this hook is called
                    // after the the TextUp user picks up the call and we must serialize the
                    // parameters that are then passed back to TextUp by Twilio after pickup
                    language: r1.language.toString()
                ])
                .logFail("tryBuildAndPersistCallToken")
                .then { Token tok1 ->
                    tok1.maxNumAccess = 1
                    DomainUtils.trySave(tok1, ResultStatus.CREATED)
                }
        }
        else { IOCUtils.resultFactory.success(null) }
    }

    // Complete
    // --------

    Result<Staff> findPasswordResetStaff(String token) {
    	Tokens.mustFindActiveForType(TokenType.PASSWORD_RESET, token)
            .then { Token tok -> incrementTimesAccessed(tok) }
            .then { Token resetToken ->
        		Staff s1 = Staff.get(TypeConversionUtils.to(Long, resetToken.data.toBeResetId))
    	        if (s1) {
                    IOCUtils.resultFactory.success(s1)
    	        }
                else {
                    log.error("tokenService.resetPassword: for token '$token' \
                        with toBeResetId '${resetToken.data.toBeResetId}', \
                        could not find a staff with that id")
                    IOCUtils.resultFactory.failWithCodeAndStatus("tokenService.resetPassword.couldNotComplete",
                        ResultStatus.INTERNAL_SERVER_ERROR)
                }
    		}
    }

    Result<Void> verifyNumber(String token, PhoneNumber toVerify) {
    	Tokens.mustFindActiveForType(TokenType.VERIFY_NUMBER, token).then { Token tok ->
            PhoneNumber stored = new PhoneNumber(number: tok.data.toVerifyNumber?.toString())
            if (!stored.validate()) {
                log.error("tokenService.verifyNumber: for token '$token' \
                    with toVerifyNumber '${tok.data.toVerifyNumber}', \
                    number is invalid: ${stored.errors}")
                return IOCUtils.resultFactory.failWithCodeAndStatus("tokenService.verifyNumber.couldNotComplete",
                    ResultStatus.INTERNAL_SERVER_ERROR)
            }
            (stored.number == toVerify.number) ?
                IOCUtils.resultFactory.success() :
                IOCUtils.resultFactory.failWithCodeAndStatus("tokenService.verifyNumber.numbersNoMatch",
                    ResultStatus.NOT_FOUND)
		}
    }

    // TODO implement
    Result<RedeemedNotification> findNotification(String token) {
        Tokens.mustFindActiveForType(TokenType.NOTIFY_STAFF, token)
            .then { Token tok -> incrementTimesAccessed(tok) }
            .then { Token tok ->


            }
    }

    Result<Token> findDirectMessage(String token) {
        Tokens.mustFindActiveForType(TokenType.CALL_DIRECT_MESSAGE, token)
            .then { Token tok -> incrementTimesAccessed(tok) }
    }

    // Helpers
    // -------

    protected Result<Token> incrementTimesAccessed(Token tok1) {
        tok1.timesAccessed++
        DomainUtils.trySave(tok1)
    }

    // TODO remove
    protected Result<Token> generate(TokenType type, Map data, Integer maxNumAccess = null) {
        Token tok1 = new Token(type: type, maxNumAccess: maxNumAccess, data: data)
        DomainUtils.trySave(tok1)
    }
}
