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
		act1.errors.getFieldError("action").code == "missingForRemove"

		when: "empty for adding"
		act1.action = Constants.MEDIA_ACTION_ADD

		then:
		act1.validate() == false
		act1.errors.errorCount == 1
		act1.errors.getFieldError("action").code == "errorForAdd"
	}

	void "test constraints for removing"() {
		given: "an empty action to remove"
		MediaAction act1 = new MediaAction(action:Constants.MEDIA_ACTION_REMOVE)

		when: "key specified for removing"
		act1.key = "I am a random string key"

		then: "ok"
		act1.validate() == true
	}

	void "test constraints for adding"() {
		given: "a valid action to add"
		String mimeType = "image/png"
		String rawData = "I am some data"
		String encodedData = Base64.encodeBase64String(rawData.getBytes(StandardCharsets.UTF_8))
		String checksum = DigestUtils.md5Hex(encodedData)
		MediaAction act1 = new MediaAction(action:Constants.MEDIA_ACTION_ADD,
			mimeType:mimeType, data:encodedData, checksum:checksum)
		assert act1.validate() == true

		when: "invalid mime type"
		act1.mimeType = "invalid"

		then:
		act1.validate() == false
		act1.errors.errorCount == 1
		act1.errors.getFieldError("action").code == "errorForAdd"

		when: "incorrectly encoded data"
		act1.mimeType = mimeType
		assert act1.validate() == true

		act1.data = rawData

		then:
		act1.validate() == false
		act1.errors.errorCount == 1
		act1.errors.getFieldError("action").code == "errorForAdd"

		when: "checksum that does not match data"
		act1.data = encodedData
		assert act1.validate() == true

		act1.checksum = "something that isn't a valid checksum"

		then:
		act1.validate() == false
		act1.errors.errorCount == 1
		act1.errors.getFieldError("action").code == "errorForAdd"
	}
}
