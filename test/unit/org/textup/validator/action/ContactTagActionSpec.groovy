package org.textup.validator.action

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import org.textup.*
import org.textup.util.CustomSpec

@Domain([Contact, Phone, ContactTag, ContactNumber, Record, RecordItem, RecordText,
	RecordCall, RecordItemReceipt, SharedContact, Staff, Team, Organization, Schedule,
	Location, WeeklySchedule, PhoneOwnership, FeaturedAnnouncement, IncomingSession,
	AnnouncementReceipt, Role, StaffRole, NotificationPolicy,
	MediaInfo, MediaElement, MediaElementVersion])
@TestMixin(HibernateTestMixin)
class ContactTagActionSpec extends CustomSpec {

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
		when: "empty"
		ContactTagAction act1 = new ContactTagAction()

		then:
		act1.validate() == false
		act1.errors.errorCount == 2

		when: "invalid action"
		act1.action = "invalid action"

		then:
		act1.validate() == false
		act1.errors.errorCount == 2
		act1.errors.getFieldError("action").code == "invalid"

		when: "nonexistent contact"
		act1.id = -88L

		then:
		act1.validate() == false
		act1.errors.errorCount == 2
		act1.errors.getFieldError("id").code == "doesNotExist"

		when: "all valid"
		act1.with {
			action = Constants.TAG_ACTION_ADD
			id = c1.id
		}

		then:
		act1.validate() == true
	}
}
