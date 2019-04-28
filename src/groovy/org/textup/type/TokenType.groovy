package org.textup.type

import grails.compiler.GrailsTypeChecked
import org.textup.*
import org.textup.util.*
import org.textup.validator.*

@GrailsTypeChecked
enum TokenType {
	CALL_DIRECT_MESSAGE([PARAM_CDM_IDENT, PARAM_CDM_LANG, PARAM_CDM_MEDIA, PARAM_CDM_MESSAGE]),
	PASSWORD_RESET([PARAM_PR_ID]),
	VERIFY_NUMBER([PARAM_VN_NUM], 5),
	NOTIFY_STAFF([PARAM_NS_STAFF, PARAM_NS_ITEMS, PARAM_NS_PHONE]),

    static final String PARAM_CDM_MESSAGE = "message"
    static final String PARAM_CDM_MEDIA = "mediaId"
    static final String PARAM_CDM_IDENT = "identifier"
    static final String PARAM_CDM_LANG = "language"
    static final String PARAM_PR_ID = "toBeResetId"
    static final String PARAM_VN_NUM = "toVerifyNumber"
    static final String PARAM_NS_STAFF = "staffId"
    static final String PARAM_NS_ITEMS = "itemIds"
    static final String PARAM_NS_PHONE = "phoneId"

	final Collection<String> requiredKeys
	final int tokenSize

	TokenType(Collection<String> reqKeys, int tokSize = Constants.DEFAULT_TOKEN_LENGTH) {
		requiredKeys = Collections.unmodifiableCollection(reqKeys)
		tokenSize = tokSize
	}

	static Map callDirectMessageData(String ident, VoiceLanguage lang, String msg = null, Long mId = null) {
        if (ident && lang && (msg || mId)) {
            [
                (PARAM_CDM_IDENT): ident,
                // cannot have language be of type VoiceLanguage because this hook is called
                // after the the TextUp user picks up the call and we must serialize the
                // parameters that are then passed back to TextUp by Twilio after pickup
                (PARAM_CDM_LANG): lang.toString(),
                (PARAM_CDM_MESSAGE): msg, // may be null
                (PARAM_CDM_MEDIA): mId // may be null
            ]
        }
        else { [:] }
	}

	static Map passwordResetData(Long staffId) {
		staffId ? [(PARAM_PR_ID): staffId] : [:]
	}

	static Map verifyNumberData(BasePhoneNumber bNum) {
		bNum ? [(PARAM_VN_NUM): bNum.number] : [:]
	}

	static Map notifyStaffData(Long staffId, Collection<Long> itemIds, Long phoneId) {
        if (staffId && itemIds?.size() > 0 && phoneId) {
            [(PARAM_NS_STAFF): staffId, (PARAM_NS_ITEMS): itemIds, (PARAM_NS_PHONE): phoneId]
        }
        else { [:] }
	}
}
