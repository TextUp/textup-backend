package org.textup.validator

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import org.textup.*
import org.textup.types.PhoneOwnershipType
import org.textup.util.CustomSpec
import spock.lang.Shared
import spock.lang.Specification

@Domain([Contact, Phone, ContactTag, ContactNumber, Record, RecordItem, RecordText,
    RecordCall, RecordItemReceipt, SharedContact, Staff, Team, Organization,
    Schedule, Location, WeeklySchedule, PhoneOwnership, Role, StaffRole])
@TestMixin(HibernateTestMixin)
class NotificationSpec extends CustomSpec {

	static doWithSpring = {
		resultFactory(ResultFactory)
	}
	def setup() {
		setupData()
	}
	def cleanup() {
		cleanupData()
	}

	void "test constraints"() {
		when: "an empty notification"
		Notification notif = new Notification()

		then: "invalid"
		notif.validate() == false
		notif.errors.errorCount == 6

		when: "fill out info"
		notif.tokenId = 1L
		notif.owner = p1.owner
		notif.record = c1.record
		notif.contents = "hi"
		notif.outgoing = true

		then: "valid"
		notif.validate() == true
		notif.ownerType ==
			(p1.owner.type == PhoneOwnershipType.INDIVIDUAL ? "staff" : "team")
		notif.otherType == "contact"
		notif.otherId == c1.id.toString()
		notif.otherName == c1.nameOrNumber
	}
}
