package org.textup.validator.action

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import org.textup.*
import org.textup.util.CustomSpec

@Domain([Contact, Phone, ContactTag, ContactNumber, Record, RecordItem, RecordText,
	RecordCall, RecordItemReceipt, SharedContact, Staff, Team, Organization, Schedule,
	Location, WeeklySchedule, PhoneOwnership, FeaturedAnnouncement, IncomingSession,
	AnnouncementReceipt, Role, StaffRole, NotificationPolicy])
@TestMixin(HibernateTestMixin)
class ShareContactActionSpec extends CustomSpec {

	static doWithSpring = {
		resultFactory(ResultFactory)
	}

    def setup() {
    	setupData()
    }

    def cleanup() {
    	cleanupData()
    }

	void "test constraints for empty"() {
		when: "empty"
		ShareContactAction act1 = new ShareContactAction()

		then:
		act1.validate() == false
		act1.errors.errorCount == 2

		when: "empty for merging"
		act1.action = Constants.SHARE_ACTION_MERGE

		then:
		act1.validate() == false
		act1.errors.errorCount == 2
		act1.errors.getFieldError("id").code == "nullable"
		act1.errors.getFieldError("permission").code == "invalid"

		when: "empty for stopping"
		act1.action = Constants.SHARE_ACTION_STOP

		then:
		act1.validate() == false
		act1.errors.errorCount == 1
		act1.errors.getFieldError("id").code == "nullable"
	}

	void "test constraints for stopping"() {
		given: "an empty stop action"
		ShareContactAction act1 = new ShareContactAction(action:Constants.SHARE_ACTION_STOP)

		when: "a nonexistent phone id"
		act1.id = -88L

		then:
		act1.validate() == false
		act1.errors.errorCount == 1
		act1.errors.getFieldError("id").code == "doesNotExist"

		when: "an existing phone id"
		act1.id = p1.id

		then:
		act1.validate() == true
	}

	void "test constraints for merging"() {
		given: "a valid merge action"
		ShareContactAction act1 = new ShareContactAction(action:Constants.SHARE_ACTION_MERGE,
			id:p1.id, permission:"dElEgATE")
		assert act1.validate() == true

		when: "invalid permission"
		act1.permission = "i am an invalid permission level"

		then:
		act1.validate() == false
		act1.errors.errorCount == 1
		act1.errors.getFieldError("permission").code == "invalid"

		when: "valid permission of varying case"
		act1.permission = "vieW"

		then:
		act1.validate() == true
	}
}