package org.textup.validator.action

import grails.compiler.GrailsTypeChecked
import grails.validation.Validateable
import groovy.transform.EqualsAndHashCode
import org.apache.commons.codec.binary.Base64
import org.apache.commons.codec.digest.DigestUtils
import org.textup.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*

// [IMPORTANT] On classes with the Validateable annotation, public getters with no properties and
// no defined field are treated like fields during validation. Making these getters protected
// or overloading the method stops these from being treated as constrainted properties. Therefore,
// in this special case, if we don't want these methods to be called during validation, we need to
// (1) rename the method, (2) make the method protected, or (3) overload the method. If we are
// all right with the getter being called but we want to apply custom constraints on it, then we
// need to declare it as a static final field to make the constraints pass type checking.

@EqualsAndHashCode(callSuper = true)
@GrailsTypeChecked
@Validateable
class MediaAction extends BaseAction {

	static final String REMOVE = "remove"
	static final String ADD = "add"

	// required when adding
	String mimeType
	String data
	String checksum

	// required when removing
	String uid

	static constraints = {
		mimeType nullable:true, blank:true, validator: { String mimeType, MediaAction obj ->
			if (obj.matches(ADD)) {
				if (!mimeType) {
					return ["requiredForAdd"]
				}
				if (!MediaType.isValidMimeType(mimeType)) {
	                return ["invalidType"]
	            }
			}
		}
		data nullable:true, blank:true, validator: { String data, MediaAction obj ->
			if (obj.matches(ADD)) {
				if (!data) {
					return ["requiredForAdd"]
				}
				boolean isCorrectFormat = Base64.isBase64(data)
				if (!isCorrectFormat) {
		        	return ["invalidFormat"]
		        }
				if (isCorrectFormat &&
					Base64.decodeBase64(data).length > ValidationUtils.MAX_MEDIA_SIZE_PER_MESSAGE_IN_BYTES) {
					return ["tooLarge", ValidationUtils.MAX_MEDIA_SIZE_PER_MESSAGE_IN_BYTES]
		        }
			}
		}
		checksum nullable:true, blank:true, validator: { String checksum, MediaAction obj ->
			if (obj.matches(ADD)) {
				if (!checksum) {
					return ["requiredForAdd"]
				}
				if (checksum != DigestUtils.md5Hex(obj.data)) {
		            return ["compromisedIntegrity"]
		        }
			}
		}
		uid nullable:true, blank:true, validator: { String uid, MediaAction obj ->
			if (obj.matches(REMOVE) && !obj.uid) {
				return ["missingForRemove"]
			}
		}
	}

	// Methods
	// -------

	byte[] buildByteData() {
		if (data && Base64.isBase64(data)) {
            Base64.decodeBase64(data)
        }
	}

	MediaType buildType() { MediaType.convertMimeType(mimeType) }

	// Properties
	// ----------

	@Override
	Collection<String> getAllowedActions() { [ADD, REMOVE] }
}
