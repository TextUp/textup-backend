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
    Schedule, Location, WeeklySchedule, PhoneOwnership, Role, StaffRole])
@TestMixin(HibernateTestMixin)
class OutgoingMessageSpec extends CustomSpec {

	static doWithSpring = {
		resultFactory(ResultFactory)
	}
	def setup() {
		setupData()
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
		expect:
		1 == 2

		// when: "empty outgoing text"

		// then:

		// when: "some contacts"

		// then:

		// when: "some tags"

		// then:

	}

	void "test generating recipients"() {
		when: "an empty outgoing text"
		OutgoingMessage text = new OutgoingMessage(message:"hello")

		then: "no recipients"
		text.toRecipients().isEmpty() == true

		when: "populated outgoing text"
		text = new OutgoingMessage(message:"hello")
		text.sharedContacts = [sc1, sc2]
		text.contacts = [c1, c1_1]
		text.tags = [tag1, tag1_1]

		int numUniqueContactables = 0
		text.tags.each { ContactTag ct1 ->
			numUniqueContactables += (ct1.members - text.contacts).size()
		}

		then:
		text.toRecipients().size() == text.sharedContacts.size() +
			text.contacts.size() + numUniqueContactables
	}
}
