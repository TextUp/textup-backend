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
class GroupMemberActionSpec extends CustomSpec {

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
		GroupMemberAction act1 = new GroupMemberAction()

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
