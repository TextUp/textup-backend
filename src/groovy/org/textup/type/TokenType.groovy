package org.textup.type

import grails.compiler.GrailsTypeChecked
import org.textup.Constants

@GrailsTypeChecked
enum TokenType {

	static final String PARAM_CDM_MESSAGE = "message"
	static final String PARAM_CDM_MEDIA = "mediaId"
	static final String PARAM_CDM_IDENT = "identifier"
	static final String PARAM_CDM_LANG = "language"
	static final String PARAM_PR_ID = "toBeResetId"
	static final String PARAM_VN_NUM = "toVerifyNumber"
	static final String PARAM_NS_OWNER_POLICY = "ownerPolicyId"
	static final String PARAM_NS_ITEMS = "itemIds"
	static final String PARAM_NS_PHONE = "phoneId"

	CALL_DIRECT_MESSAGE([PARAM_CDM_IDENT, PARAM_CDM_LANG, PARAM_CDM_MEDIA, PARAM_CDM_MESSAGE]),
	PASSWORD_RESET([PARAM_PR_ID]),
	VERIFY_NUMBER([PARAM_VN_NUM], 5),
	NOTIFY_STAFF([PARAM_NS_OWNER_POLICY, PARAM_NS_ITEMS, PARAM_NS_PHONE]),

	final Collection<String> requiredKeys
	final int tokenSize

	TokenType(Collection<String> reqKeys, int tokSize = Constants.DEFAULT_TOKEN_LENGTH) {
		requiredKeys = Collections.unmodifiableCollection(reqKeys)
		tokenSize = tokSize
	}

	static Map callDirectMessageData(String ident, VoiceLanguage lang, String msg, Long mId = null) {
		[
            (PARAM_CDM_IDENT): lang
            // cannot have language be of type VoiceLanguage because this hook is called
            // after the the TextUp user picks up the call and we must serialize the
            // parameters that are then passed back to TextUp by Twilio after pickup
            (PARAM_CDM_LANG): lang.toString(),
            (PARAM_CDM_MESSAGE): msg,
            (PARAM_CDM_MEDIA): mId // may be null
        ]
	}

	static Map passwordResetData(Long staffId) {
		[(PARAM_PR_ID): staffId]
	}

	static Map verifyNumberData(BasePhoneNumber bNum) {
		[(PARAM_VN_NUM): bNum.number]
	}

	static Map notifyStaffData(Long opId, Collection<Long> itemIds, Long phoneId) {
		[(PARAM_NS_OWNER_POLICY): opId, (PARAM_NS_ITEMS): itemIds, (PARAM_NS_PHONE): phoneId]
	}
}
