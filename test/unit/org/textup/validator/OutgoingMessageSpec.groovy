package org.textup.validator

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import org.textup.*
import org.textup.type.*
import org.textup.util.*
import spock.lang.Shared
import spock.lang.Specification

@Domain([Contact, Phone, ContactTag, ContactNumber, Record, RecordItem, RecordText,
    RecordCall, RecordItemReceipt, SharedContact, Staff, Team, Organization,
    Schedule, Location, WeeklySchedule, PhoneOwnership, Role, StaffRole, NotificationPolicy,
    MediaInfo, MediaElement, MediaElementVersion])
@TestMixin(HibernateTestMixin)
class OutgoingMessageSpec extends CustomSpec {

	static doWithSpring = {
		resultFactory(ResultFactory)
	}
	def setup() {
		setupData()
		addToMessageSource("outgoingMessage.getName.contactId")
		Helpers.metaClass.'static'.getMessageSource = { -> messageSource }
	}
	def cleanup() {
		cleanupData()
	}

	void "test constraints with media"() {
		when: "empty obj"
		OutgoingMessage msg1 = new OutgoingMessage()

		then:
		msg1.validate() == false
		msg1.errors.getFieldErrorCount("media") == 1
		msg1.errors.getFieldError("media").codes.contains("noInfo")
		msg1.errors.getFieldErrorCount("contacts") == 1
		msg1.errors.getFieldErrorCount("sharedContacts") == 1
		msg1.errors.getFieldErrorCount("tags") == 1

		when: "add recipients + empty media"
		msg1.media = new MediaInfo()
		msg1.contacts = new ContactRecipients()
		msg1.sharedContacts = new SharedContactRecipients()
		msg1.tags = new ContactTagRecipients()

		then:
		msg1.validate() == false
		msg1.errors.getFieldErrorCount("media") == 1
		msg1.errors.getFieldError("media").codes.contains("noInfo")
		msg1.errors.getFieldErrorCount("contacts") == 0
		msg1.errors.getFieldErrorCount("sharedContacts") == 0
		msg1.errors.getFieldErrorCount("tags") == 0

		when: "add media element to media so no longer empty"
		MediaElement e1 = new MediaElement()
        e1.type = MediaType.IMAGE
        e1.sendVersion = new MediaElementVersion(mediaVersion: MediaVersion.SEND,
            key: UUID.randomUUID().toString(),
            sizeInBytes: Constants.MAX_MEDIA_SIZE_PER_MESSAGE_IN_BYTES / 2,
            widthInPixels: 888)
        msg1.media.addToMediaElements(e1)

		then:
		msg1.validate() == true
	}

	void "test constraints with media"() {
		when: "empty obj"
		OutgoingMessage msg1 = new OutgoingMessage()

		then: "call validatate without phone to be invalid"
		msg1.validate() == false
		msg1.errors.getFieldErrorCount("media") == 1
		msg1.errors.getFieldError("media").codes.contains("noInfo")
		msg1.errors.getFieldErrorCount("contacts") == 1
		msg1.errors.getFieldErrorCount("sharedContacts") == 1
		msg1.errors.getFieldErrorCount("tags") == 1

		when: "add recipients + too-long message"
		msg1.message = buildVeryLongString()
		msg1.contacts = new ContactRecipients()
		msg1.sharedContacts = new SharedContactRecipients()
		msg1.tags = new ContactTagRecipients()

		then:
		msg1.validate() == false
		msg1.errors.getFieldErrorCount("message") == 1
		msg1.errors.getFieldErrorCount("contacts") == 0
		msg1.errors.getFieldErrorCount("sharedContacts") == 0
		msg1.errors.getFieldErrorCount("tags") == 0

		when: "add a valid message"
		msg1.message = "hi there!"

		then:
		msg1.validate() == true
	}

	void "test getting name"() {
		when: "empty outgoing message"
		OutgoingMessage msg1 = TestHelpers.buildOutgoingMessage("hello")

		then:
		msg1.name == ""

		when: "some contacts"
		msg1.contacts.recipients << c1

		then:
		msg1.name == "outgoingMessage.getName.contactId"

		when: "some tags and some contacts"
		msg1.tags.recipients << tag1

		then:
		msg1.name == "outgoingMessage.getName.contactId"

		when: "some tags but no contacts"
		msg1.contacts.recipients.clear()

		then:
		msg1.name == tag1.name
	}

	void "test getting phones"() {
		when: "empty outgoing message"
		OutgoingMessage msg1 = TestHelpers.buildOutgoingMessage("hello")

		then:
		msg1.phones.isEmpty() == true

		when: "some contacts"
		msg1.contacts.recipients << c1
		Collection<Phone> phones = msg1.phones

		then:
		[c1.phone].every { it in phones }

		when: "some tags and some contacts"
		msg1.tags.recipients << tag1
		phones = msg1.phones

		then:
		[c1.phone, tag1.phone].every { it in phones }

		when: "some tags, contacts and shared contacts"
		msg1.sharedContacts.recipients << sc1
		phones = msg1.phones

		then:
		[c1.phone, tag1.phone, sc1.sharedWith, sc1.sharedBy].every { it in phones }
	}

	void "test generating recipients"() {
		when: "an empty outgoing msg"
		OutgoingMessage msg1 = TestHelpers.buildOutgoingMessage("hello")

		then: "no recipients"
		msg1.toRecipients().isEmpty() == true

		when: "populated outgoing msg"
		msg1.sharedContacts.recipients = [sc1, sc2]
		msg1.contacts.recipients = [c1, c1_1]
		msg1.tags.recipients = [tag1, tag1_1]

		int numUniqueContactables = 0
		msg1.tags.recipients.each { ContactTag ct1 ->
			numUniqueContactables += (ct1.members - msg1.contacts.recipients).size()
		}

		then:
		msg1.toRecipients().size() == msg1.sharedContacts.recipients.size() +
			msg1.contacts.recipients.size() + numUniqueContactables
	}

	// Helpers
    // -------

    protected String buildVeryLongString() {
        StringBuilder sBuilder = new StringBuilder()
        15000.times { it -> sBuilder << it }
        sBuilder.toString()
    }
}
