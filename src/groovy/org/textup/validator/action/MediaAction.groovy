package org.textup.validator.action

import grails.compiler.GrailsCompileStatic
import grails.validation.Validateable
import groovy.transform.EqualsAndHashCode
import org.apache.commons.codec.binary.Base64
import org.apache.commons.codec.digest.DigestUtils
import org.textup.*
import org.textup.type.MediaType
import org.textup.validator.UploadItem

// documented as [mediaAction] in CustomApiDocs.groovy

@GrailsCompileStatic
@EqualsAndHashCode(callSuper=true)
@Validateable
class MediaAction extends BaseAction {

	// required when adding
	String mimeType
	String data
	String checksum

	// required when removing
	String uid

	static constraints = {
		mimeType nullable:true, blank:true, validator: { String mimeType, MediaAction obj ->
			if (obj.matches(Constants.MEDIA_ACTION_ADD)) {
				if (!mimeType) {
					return ["requiredForAdd"]
				}
				if (!MediaType.isValidMimeType(mimeType)) {
	                return ["invalidType"]
	            }
			}
		}
		data nullable:true, blank:true, validator: { String data, MediaAction obj ->
			if (obj.matches(Constants.MEDIA_ACTION_ADD)) {
				if (!data) {
					return ["requiredForAdd"]
				}
				if (!Base64.isBase64(data)) {
		            return ["invalidFormat"]
		        }
			}
		}
		checksum nullable:true, blank:true, validator: { String checksum, MediaAction obj ->
			if (obj.matches(Constants.MEDIA_ACTION_ADD)) {
				if (!checksum) {
					return ["requiredForAdd"]
				}
				if (checksum != DigestUtils.md5Hex(obj.data)) {
		            return ["compromisedIntegrity"]
		        }
			}
		}
		uid nullable:true, blank:true, validator: { String uid, MediaAction obj ->
			if (obj.matches(Constants.MEDIA_ACTION_REMOVE) && !obj.uid) {
				return ["missingForRemove"]
			}
		}
	}

	// Validation helpers
	// ------------------

	@Override
	Collection<String> getAllowedActions() {
		[Constants.MEDIA_ACTION_ADD, Constants.MEDIA_ACTION_REMOVE]
	}

	// Property access
	// ---------------

	byte[] getByteData() {
		if (data && Base64.isBase64(data)) {
            Base64.decodeBase64(data)
        }
	}
}
