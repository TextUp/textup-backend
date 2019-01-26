package org.textup.validator.action

import org.textup.test.*
import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import org.textup.*

@Domain([AnnouncementReceipt, ContactNumber, CustomAccountDetails, FeaturedAnnouncement,
    FutureMessage, GroupPhoneRecord, IncomingSession, IndividualPhoneRecord, Location, MediaElement,
    MediaElementVersion, MediaInfo, Organization, OwnerPolicy, Phone, PhoneNumberHistory,
    PhoneOwnership, PhoneRecord, PhoneRecordMembers, Record, RecordCall, RecordItem,
    RecordItemReceipt, RecordNote, RecordNoteRevision, RecordText, Role, Schedule,
    SimpleFutureMessage, Staff, StaffRole, Team, Token])
@TestMixin(HibernateTestMixin)
class ShareActionSpec extends CustomSpec {

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
		ShareAction act1 = new ShareAction()

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
		ShareAction act1 = new ShareAction(action:Constants.SHARE_ACTION_STOP)

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
		ShareAction act1 = new ShareAction(action:Constants.SHARE_ACTION_MERGE,
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
