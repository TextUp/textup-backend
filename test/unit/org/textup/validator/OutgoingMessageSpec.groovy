package org.textup.validator

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import org.textup.*
import org.textup.util.CustomSpec
import spock.lang.Shared
import spock.lang.Specification

@Domain([Contact, Phone, ContactTag, ContactNumber, Record, RecordItem, RecordText,
    RecordCall, RecordItemReceipt, SharedContact, Staff, Team, Organization,
    Schedule, Location, WeeklySchedule, PhoneOwnership, Role, StaffRole, NotificationPolicy])
@TestMixin(HibernateTestMixin)
class OutgoingMessageSpec extends CustomSpec {

	static doWithSpring = {
		resultFactory(ResultFactory)
	}
	def setup() {
		setupData()
		OutgoingMessage.metaClass.getMessageSource = { -> messageSource }
		addToMessageSource("outgoingMessage.getName.contactId")
	}
	def cleanup() {
		cleanupData()
	}

	void "test validation with phone"() {
		when: "a valid outgoing text without any recipients"
		OutgoingMessage text = new OutgoingMessage(message:"hello")

		then: "call validatate without phone to be invalid"
		text.validate() == false
		// recipients check only happens when calling validateSetPhone!
		text.errors.errorCount == 1
		text.errors.globalErrorCount == 0

		and: "call validate with phone to be also be invalid because no recipients"
		text.validateSetPhone(p1) == false
		text.errors.errorCount == 1
		text.errors.globalErrorCount == 1

		when: "a valid outgoing text with at least one recipient"
		text = new OutgoingMessage(message:"hello", contacts:[c1])

		then: "call validatate without phone to be invalid"
		text.validate() == false
		// recipients check only happens when calling validateSetPhone!
		text.errors.errorCount == 2
		text.errors.globalErrorCount == 0

		and: "call validate with phone to be valid"
		text.validateSetPhone(p1) == true
		text.errors.errorCount == 0
		text.errors.globalErrorCount == 0
	}

	void "test message constraints"() {
		when: "no fields filled out"
		OutgoingMessage text = new OutgoingMessage()

		then: "invalid"
		text.validateSetPhone(p1) == false
		text.errors.errorCount == 2
		text.errors.globalErrorCount == 1

		when: "message too long"
		text = new OutgoingMessage(message:"I am way too long. I am way too long. \
			I am way too long. I am way too long. I am way too long. I am way \
			too long. I am way too long. I am way too long. I am way too long. \
			I am way too long. I am way too long. I am way too long. I am way \
			too long. I am way too long. I am way too long. I am way too long. \
			I am way too long. I am way too long. I am way too long. I am way \
			too long. I am way too long. ")

		then: "invalid"
		text.validateSetPhone(p1) == false
		text.errors.errorCount == 2
		text.errors.globalErrorCount == 1

		when: "message of valid length "
		text = new OutgoingMessage(message:"I am just right.", contacts:[c1])

		then: "valid"
		text.validateSetPhone(p1) == true
	}

	void "test contacts constraints"() {
		when: "contacts that do not all belong to this phone"
		OutgoingMessage text = new OutgoingMessage(message:"hello")
		text.contacts = [c1, c1_1, c1_2, c2]

		then: "invalid"
		text.validateSetPhone(p1) == false
		text.errors.errorCount == 1

		when: "all contacts belong to this phone"
		text.contacts = [c1, c1_1]

		then: "valid"
		text.validateSetPhone(p1) == true
	}

	void "test shared contacts constraints"() {
		when: "some shared contacts not shared with us or inactive"
		OutgoingMessage text = new OutgoingMessage(message:"hello")
		text.sharedContacts = [sc1, sc2]

		then: "invalid"
		text.validateSetPhone(p1) == false
		text.errors.errorCount == 1

		when: "all shared contacts shared with us and active"
		text.sharedContacts = [sc2]

		then: "valid"
		text.validateSetPhone(p1) == true
	}

	void "test tags constraints"() {
		when: "some tags do not belong to this phone"
		OutgoingMessage text = new OutgoingMessage(message:"hello")
		text.tags = [tag1, tag1_1, tag2]

		then: "invalid"
		text.validateSetPhone(p1) == false
		text.errors.errorCount == 1

		when: "all contacts belong to us"
		text.tags = [tag1, tag1_1]

		then: "valid"
		text.validateSetPhone(p1) == true
	}

	void "test getting name"() {
		when: "empty outgoing message"
		OutgoingMessage msg = new OutgoingMessage(message:"hello")

		then:
		msg.name == ""

		when: "some contacts"
		msg.contacts << c1

		then:
		msg.name == "outgoingMessage.getName.contactId"

		when: "some tags and some contacts"
		msg.tags << tag1

		then:
		msg.name == "outgoingMessage.getName.contactId"

		when: "some tags but no contacts"
		msg.contacts.clear()

		then:
		msg.name == tag1.name
	}

	void "test getting phones"() {
		when: "empty outgoing message"
		OutgoingMessage msg = new OutgoingMessage(message:"hello")

		then:
		msg.phones.isEmpty() == true

		when: "some contacts"
		msg.contacts << c1
		Collection<Phone> phones = msg.phones

		then:
		[c1.phone].every { it in phones }

		when: "some tags and some contacts"
		msg.tags << tag1
		phones = msg.phones

		then:
		[c1.phone, tag1.phone].every { it in phones }

		when: "some tags, contacts and shared contacts"
		msg.sharedContacts << sc1
		phones = msg.phones

		then:
		[c1.phone, tag1.phone, sc1.sharedWith, sc1.sharedBy].every { it in phones }
	}

	void "test generating recipients"() {
		when: "an empty outgoing msg"
		OutgoingMessage msg = new OutgoingMessage(message:"hello")

		then: "no recipients"
		msg.toRecipients().isEmpty() == true

		when: "populated outgoing msg"
		msg = new OutgoingMessage(message:"hello")
		msg.sharedContacts = [sc1, sc2]
		msg.contacts = [c1, c1_1]
		msg.tags = [tag1, tag1_1]

		int numUniqueContactables = 0
		msg.tags.each { ContactTag ct1 ->
			numUniqueContactables += (ct1.members - msg.contacts).size()
		}

		then:
		msg.toRecipients().size() == msg.sharedContacts.size() +
			msg.contacts.size() + numUniqueContactables
	}
}
