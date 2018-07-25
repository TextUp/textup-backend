package org.textup.type

import grails.compiler.GrailsCompileStatic
import org.textup.Constants

@GrailsCompileStatic
enum TokenType {
	PASSWORD_RESET([
		"toBeResetId" // id of the staff to allow password reset for
	]),
	VERIFY_NUMBER(5, [
		"toVerifyNumber" // phone number to verify
	]),
	NOTIFY_STAFF([
		"recordId", // id of the record associated with message, we can
					// extrapolate from this id the contact/tag
		"phoneId", // specify phoneId separately from record id even though
				   // it is possible to extrapolate from record because we might
				   // have shared the contact that owns the record and therefore
				   // the phone may not necessarily be the one associated with record
		"contents", // contents of the message we are notifying for
		"outgoing" // whether or not the message we are notifying for is outgoing
	]),
	CALL_DIRECT_MESSAGE([
		"message", // contents of the message to deliver as a call
		"identifier", // who to identify the message as being from
		"language" // VoiceLanguage enum as string (NOT Twiml value) to read the message in
	])

	private final Collection<String> requiredKeys
	private final int tokenSize = Constants.DEFAULT_TOKEN_LENGTH

	TokenType(int tokSize, Collection<String> reqKeys) {
		this.tokenSize = tokSize
		this.requiredKeys = reqKeys
	}
	TokenType(Collection<String> reqKeys) {
		this.requiredKeys = reqKeys
	}

	Collection<String> getRequiredKeys() { this.requiredKeys }
	int getTokenSize() { this.tokenSize }
}
