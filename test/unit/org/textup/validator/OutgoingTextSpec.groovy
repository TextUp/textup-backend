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
    Schedule, Location, WeeklySchedule, PhoneOwnership])
@TestMixin(HibernateTestMixin)
class OutgoingTextSpec extends CustomSpec {

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
		given: "a valid outgoing text"
		OutgoingText text = new OutgoingText(message:"hello")

		expect: "call validatate without phone to be invalid"
		text.validate() == false
		text.errors.errorCount == 1

		and: "call validate with phone to be valid"
		text.validateSetPhone(p1) == true
	}

	void "test message constraints"() {
		when: "no fields filled out"
		OutgoingText text = new OutgoingText()

		then: "invalid"
		text.validateSetPhone(p1) == false
		text.errors.errorCount == 1

		when: "message too long"
		text = new OutgoingText(message:"I am way too long. I am way too long. \
			I am way too long. I am way too long. I am way too long. I am way \
			too long. I am way too long. I am way too long. I am way too long. \
			I am way too long. I am way too long. I am way too long. I am way \
			too long. I am way too long. I am way too long. I am way too long. \
			I am way too long. I am way too long. I am way too long. I am way \
			too long. I am way too long. ")

		then: "invalid"
		text.validateSetPhone(p1) == false
		text.errors.errorCount == 1

		when: "message of valid length "
		text = new OutgoingText(message:"I am just right.")

		then: "valid"
		text.validateSetPhone(p1) == true
	}

	void "test contacts constraints"() {
		when: "contacts that do not all belong to this phone"
		OutgoingText text = new OutgoingText(message:"hello")
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
		OutgoingText text = new OutgoingText(message:"hello")
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
		OutgoingText text = new OutgoingText(message:"hello")
		text.tags = [tag1, tag1_1, tag2]

		then: "invalid"
		text.validateSetPhone(p1) == false
		text.errors.errorCount == 1

		when: "all contacts belong to us"
		text.tags = [tag1, tag1_1]

		then: "valid"
		text.validateSetPhone(p1) == true
	}
}
