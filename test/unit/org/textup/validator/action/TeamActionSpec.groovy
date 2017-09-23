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
class TeamActionSpec extends CustomSpec {

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
		TeamAction act1 = new TeamAction()

		then:
		act1.validate() == false
		act1.errors.errorCount == 2
		act1.errors.getFieldError("action").code == "nullable"
		act1.errors.getFieldError("id").code == "nullable"

		when: "all valid"
		act1.action = Constants.TEAM_ACTION_ADD
		act1.id = s1.id

		then:
		act1.validate() == true

		when: "invalid action"
		act1.action = "invalid action"

		then:
		act1.validate() == false
		act1.errors.errorCount == 1
		act1.errors.getFieldError("action").code == "invalid"

		when: "nonexistent staff id"
		act1.id = -88L

		then:
		act1.validate() == false
		act1.errors.errorCount == 2
		act1.errors.getFieldError("action").code == "invalid"
		act1.errors.getFieldError("id").code == "doesNotExist"
	}
}