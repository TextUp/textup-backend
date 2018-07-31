package org.textup.validator.action

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import java.nio.charset.StandardCharsets
import org.apache.commons.codec.binary.Base64
import org.apache.commons.codec.digest.DigestUtils
import org.textup.*
import org.textup.util.CustomSpec

@Domain([Organization, Location])
@TestMixin(HibernateTestMixin)
class MediaActionSpec extends CustomSpec {

	static doWithSpring = {
		resultFactory(ResultFactory)
	}

	// extend CustomSpec solely for access to helper methods, not for setting up test data
	def setup() {
		ResultFactory resultFactory = grailsApplication.mainContext.getBean("resultFactory")
		resultFactory.messageSource = mockMessageSourceWithResolvable()
	}

	void "test constraints when empty"() {
		when: "completely empty"
		MediaAction act1 = new MediaAction()

		then:
		act1.validate() == false
		act1.errors.errorCount == 1
		act1.errors.getFieldError("action").code == "nullable"

		when: "empty for removing"
		act1.action = Constants.MEDIA_ACTION_REMOVE

		then:
		act1.validate() == false
		act1.errors.errorCount == 1
		act1.errors.getFieldErrorCount("uid") == 1
		act1.errors.getFieldError("uid").code == "missingForRemove"

		when: "empty for adding"
		act1.action = Constants.MEDIA_ACTION_ADD

		then:
		act1.validate() == false
		act1.errors.errorCount == 3
		act1.errors.getFieldErrorCount("mimeType") == 1
		act1.errors.getFieldError("mimeType").code == "requiredForAdd"
		act1.errors.getFieldErrorCount("data") == 1
		act1.errors.getFieldError("data").code == "requiredForAdd"
		act1.errors.getFieldErrorCount("checksum") == 1
		act1.errors.getFieldError("checksum").code == "requiredForAdd"
	}

	void "test constraints for removing"() {
		given: "an empty action to remove"
		MediaAction act1 = new MediaAction(action:Constants.MEDIA_ACTION_REMOVE)

		when: "uid specified for removing"
		act1.uid = "I am a random string uid"

		then: "ok + can't get byte data"
		act1.validate() == true
		act1.byteData == null
	}

	void "test constraints for adding"() {
		when: "a valid action to add"
		String mimeType = Constants.MIME_TYPE_PNG
		String rawData = "I am some data*~~~~|||"
		String encodedData = Base64.encodeBase64String(rawData.getBytes(StandardCharsets.UTF_8))
		String checksum = DigestUtils.md5Hex(encodedData)
		MediaAction act1 = new MediaAction(action:Constants.MEDIA_ACTION_ADD,
			mimeType:mimeType, data:encodedData, checksum:checksum)

		then: "can get byte data"
		act1.validate() == true
		act1.byteData instanceof byte[]

		when: "invalid mime type"
		act1.mimeType = "invalid"

		then:
		act1.validate() == false
		act1.errors.errorCount == 1
		act1.errors.getFieldErrorCount("mimeType") == 1
		act1.errors.getFieldError("mimeType").code == "invalidType"

		when: "incorrectly encoded data"
		act1.mimeType = mimeType
		assert act1.validate() == true

		act1.data = rawData

		then: "invalid + can't get byte data"
		act1.validate() == false
		act1.errors.errorCount == 2
		act1.errors.getFieldErrorCount("data") == 1
		act1.errors.getFieldError("data").code == "invalidFormat"
		act1.errors.getFieldErrorCount("checksum") == 1
		act1.errors.getFieldError("checksum").code == "compromisedIntegrity"
		act1.byteData == null

		when: "checksum that does not match data"
		act1.data = encodedData
		assert act1.validate() == true

		act1.checksum = "something that isn't a valid checksum"

		then:
		act1.validate() == false
		act1.errors.errorCount == 1
		act1.errors.getFieldErrorCount("checksum") == 1
		act1.errors.getFieldError("checksum").code == "compromisedIntegrity"
		act1.byteData instanceof byte[]
	}
}
