package org.textup

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import groovy.transform.TypeCheckingMode
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.springframework.transaction.annotation.Propagation
import org.textup.annotation.*
import org.textup.rest.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*

@GrailsTypeChecked
@Transactional
class TokenService {

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
            Map data = TokenType
                .callDirectMessageData(r1.buildFromName(), r1.language, temp1.text, temp1.media?.id)
            Token.tryCreate(TokenType.CALL_DIRECT_MESSAGE, data)
                .logFail("tryBuildAndPersistCallToken")
                .then { Token tok1 ->
                    tok1.maxNumAccess = 1
                    DomainUtils.trySave(tok1, ResultStatus.CREATED)
                }
        }
        else { IOCUtils.resultFactory.success(null) }
    }

    Result<Closure> buildDirectMessageCall(String token) {
        Tokens.mustFindActiveForType(TokenType.CALL_DIRECT_MESSAGE, token)
            .then { Token tok1 -> tryIncrementTimesAccessed(tok1) }
            .then { Token tok1 ->
                TypeMap data = tok1.data
                List<URL> recordings = []
                Long mId = data.long(TokenType.PARAM_CDM_MEDIA)
                if (mId) {
                    MediaInfos.mustFindForId(mId)
                        .logFail("buildDirectMessageCall")
                        .then { MediaInfo mInfo ->
                            recordings = mInfo.getMediaElementsByType(MediaType.AUDIO_TYPES)
                                *.sendVersion
                                *.link
                        }
                }
                CallTwiml.directMessage(data.string(TokenType.PARAM_CDM_IDENT),
                    data.string(TokenType.PARAM_CDM_MESSAGE),
                    data.enum(VoiceLanguage, TokenType.PARAM_CDM_LANG),
                    CollectionUtils.ensureNoNull(recordings))
            }
    }

    Result<Token> generatePasswordReset(Long staffId) {
        Token.tryCreate(TokenType.PASSWORD_RESET, TokenType.passwordResetData(staffId))
            .then { Token tok1 ->
                tok1.maxNumAccess = 1
                DomainUtils.trySave(tok1, ResultStatus.CREATED)
            }
    }

    Result<Staff> findPasswordResetStaff(String token) {
    	Tokens.mustFindActiveForType(TokenType.PASSWORD_RESET, token)
            .then { Token tok -> tryIncrementTimesAccessed(tok) }
            .then { Token tok1 -> Staffs.mustFindForId(tok1.data.long(TokenType.PARAM_PR_ID)) }
    }

    Result<Token> generateVerifyNumber(PhoneNumber toNum) {
        Token.tryCreate(TokenType.VERIFY_NUMBER, TokenType.verifyNumberData(toNum))
    }

    Result<PhoneNumber> findVerifyNumber(String token) {
    	Tokens.mustFindActiveForType(TokenType.VERIFY_NUMBER, token)
            .then { Token tok1 -> PhoneNumber.tryCreate(tok1.data.string(TokenType.PARAM_VN_NUM)) }
    }

    Result<Token> tryGeneratePreviewInfo(OwnerPolicy op1, Notification notif1) {
        if (op1.shouldSendPreviewLink) {
            Notification.Dehydrated dn1 = notif1.dehydrate()
            Map data = TokenType.notifyStaffData(op1.id, dn1.itemIds, dn1.phoneId)
            Token.tryCreate(TokenType.NOTIFY_STAFF, data)
                .then { Token tok1 ->
                    tok1.maxNumAccess = ValidationUtils.MAX_NUM_ACCESS_NOTIFICATION_TOKEN
                    tok1.expires = DateTimeUtils.now().plusDays(1)
                    IOCUtils.resultFactory.success(tok1)
                }
        }
        else { IOCUtils.resultFactory.success(null) }
    }

    Result<Tuple<Long, Notification>> tryFindPreviewInfo(String token) {
        Tokens.mustFindActiveForType(TokenType.NOTIFY_STAFF, token)
            .then { Token tok1 -> tryIncrementTimesAccessed(tok1) }
            .then { Token tok1 ->
                TypeMap data = tok1.data
                Notification.Dehydrated
                    .tryCreate(data.long(TokenType.PARAM_NS_PHONE),
                        data.typedList(Long, TokenType.PARAM_NS_ITEMS))
                    .curry(data.long(TokenType.PARAM_NS_OWNER_POLICY))
            }
            .then { Long ownerPolicyId, Notification.Dehydrated dn1 ->
                dn1.tryRehydrate().curry(ownerPolicyId)
            }
            .then { Long ownerPolicyId, Notification notif1 ->
                IOCUtils.resultFactory.success(ownerPolicyId, notif1)
            }
    }

    // Helpers
    // -------

    protected Result<Token> tryIncrementTimesAccessed(Token tok1) {
        tok1.timesAccessed++
        DomainUtils.trySave(tok1)
    }
}
