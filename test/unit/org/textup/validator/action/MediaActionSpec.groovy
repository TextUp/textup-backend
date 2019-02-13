package org.textup.validator.action

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import org.textup.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import spock.lang.*

@Domain([CustomAccountDetails, Organization, Location])
@TestMixin(HibernateTestMixin)
class MediaActionSpec extends Specification {

	static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

	void "test constraints when empty"() {
		when: "completely empty"
		MediaAction act1 = new MediaAction()

		then:
		act1.validate() == false

		when: "empty for removing"
		act1.action = MediaAction.REMOVE

		then:
		act1.validate() == false
		act1.errors.getFieldErrorCount("uid") == 1
		act1.errors.getFieldError("uid").code == "missingForRemove"

		when: "empty for adding"
		act1.action = MediaAction.ADD

		then:
		act1.validate() == false
		act1.errors.getFieldErrorCount("mimeType") == 1
		act1.errors.getFieldError("mimeType").code == "requiredForAdd"
		act1.errors.getFieldErrorCount("data") == 1
		act1.errors.getFieldError("data").code == "requiredForAdd"
		act1.errors.getFieldErrorCount("checksum") == 1
		act1.errors.getFieldError("checksum").code == "requiredForAdd"
	}

	void "test constraints for removing"() {
		given: "an empty action to remove"
		MediaAction act1 = new MediaAction(action: MediaAction.REMOVE)

		when: "uid specified for removing"
		act1.uid = TestUtils.randString()

		then: "ok + can't get byte data"
		act1.validate()
		act1.buildByteData() == null
		act1.buildType() == null
	}

	void "test constraints for adding"() {
		given:
		String mimeType = MediaType.IMAGE_PNG.mimeType
		String rawData = "I am some data*~~~~|||"
		String encodedData = TestUtils.encodeBase64String(rawData.bytes)
		String checksum = TestUtils.getChecksum(encodedData)

		when: "a valid action to add"
		MediaAction act1 = new MediaAction(action: MediaAction.ADD,
			mimeType: mimeType,
			data: encodedData,
			checksum: checksum)

		then: "can get byte data"
		act1.validate() == true
		act1.buildByteData() instanceof byte[]
		act1.buildType() == MediaType.IMAGE_PNG

		when: "invalid mime type"
		act1.mimeType = "invalid"

		then:
		act1.validate() == false
		act1.errors.getFieldErrorCount("mimeType") == 1
		act1.errors.getFieldError("mimeType").code == "invalidType"
		act1.buildType() == null

		when: "incorrectly encoded data"
		act1.mimeType = mimeType
		assert act1.validate() == true

		act1.data = rawData

		then: "invalid + can't get byte data"
		act1.validate() == false
		act1.errors.getFieldErrorCount("data") == 1
		act1.errors.getFieldError("data").code == "invalidFormat"
		act1.errors.getFieldErrorCount("checksum") == 1
		act1.errors.getFieldError("checksum").code == "compromisedIntegrity"
		act1.buildByteData() == null
		act1.buildType() == MediaType.IMAGE_PNG

		when: "checksum that does not match data"
		act1.data = encodedData
		assert act1.validate() == true

		act1.checksum = "something that isn't a valid checksum"

		then:
		act1.validate() == false
		act1.errors.getFieldErrorCount("checksum") == 1
		act1.errors.getFieldError("checksum").code == "compromisedIntegrity"
		act1.buildByteData() instanceof byte[]
		act1.buildType() == MediaType.IMAGE_PNG
	}
}
