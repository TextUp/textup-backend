package org.textup.validator.action

import grails.compiler.GrailsTypeChecked
import grails.validation.Validateable
import groovy.transform.EqualsAndHashCode
import org.apache.commons.codec.binary.Base64
import org.apache.commons.codec.digest.DigestUtils
import org.textup.*
import org.textup.type.MediaType
import org.textup.validator.UploadItem

// documented as [mediaAction] in CustomApiDocs.groovy

// [IMPORTANT] On classes with the Validateable annotation, public getters with no properties and
// no defined field are treated like fields during validation. Making these getters protected
// or overloading the method stops these from being treated as constrainted properties. Therefore,
// in this special case, if we don't want these methods to be called during validation, we need to
// (1) rename the method, (2) make the method protected, or (3) overload the method. If we are
// all right with the getter being called but we want to apply custom constraints on it, then we
// need to declare it as a static final field to make the constraints pass type checking.

@GrailsTypeChecked
@EqualsAndHashCode(callSuper=true)
@Validateable
class MediaAction extends BaseAction {

	// required when adding
	String mimeType
	String data
	String checksum

	// required when removing
	String uid

	final byte[] byteData
	final MediaType type

	static constraints = {
		byteData nullable:true
		type nullable:true
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
				boolean isCorrectFormat = Base64.isBase64(data)
				if (!isCorrectFormat) {
		        	return ["invalidFormat"]
		        }
				if (isCorrectFormat &&
					Base64.decodeBase64(data).length > Constants.MAX_MEDIA_SIZE_PER_MESSAGE_IN_BYTES) {

					return ["tooLarge", Constants.MAX_MEDIA_SIZE_PER_MESSAGE_IN_BYTES]
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

	MediaType getType() {
		MediaType.convertMimeType(this.mimeType)
	}
}
