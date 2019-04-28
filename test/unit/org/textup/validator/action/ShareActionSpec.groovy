package org.textup.validator.action

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import org.textup.*
import org.textup.test.*
import org.textup.type.*
import spock.lang.*

@Domain([AnnouncementReceipt, ContactNumber, CustomAccountDetails, FeaturedAnnouncement,
    FutureMessage, GroupPhoneRecord, IncomingSession, IndividualPhoneRecord, Location, MediaElement,
    MediaElementVersion, MediaInfo, Organization, OwnerPolicy, Phone, PhoneNumberHistory,
    PhoneOwnership, PhoneRecord, PhoneRecordMembers, Record, RecordCall, RecordItem,
    RecordItemReceipt, RecordNote, RecordNoteRevision, RecordText, Role, Schedule,
    SimpleFutureMessage, Staff, StaffRole, Team, Token])
@TestMixin(HibernateTestMixin)
class ShareActionSpec extends Specification {

	static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

	void "test constraints for empty"() {
		when: "empty"
		ShareAction act1 = new ShareAction()

		then:
		act1.validate() == false

		when: "empty for merging"
		act1.action = ShareAction.MERGE

		then:
		act1.validate() == false
		act1.errors.getFieldError("id").code == "nullable"
		act1.errors.getFieldError("permission").code == "invalid"

		when: "empty for stopping"
		act1.action = ShareAction.STOP

		then:
		act1.validate() == false
		act1.errors.getFieldError("id").code == "nullable"
		act1.errors.getFieldErrorCount("permission") == 0
	}

	void "test constraints for stopping"() {
		given: "an empty stop action"
		Phone p1 = TestUtils.buildStaffPhone()
		ShareAction act1 = new ShareAction(action: ShareAction.STOP)

		when: "a nonexistent phone id"
		act1.id = -88L

		then:
		act1.validate() == false
		act1.errors.getFieldError("id").code == "doesNotExist"

		when: "an existing phone id"
		act1.id = p1.id

		then:
		act1.validate() == true
		act1.buildPhone() == p1
	}

	void "test constraints for merging"() {
		given:
		Phone p1 = TestUtils.buildStaffPhone()

		when:
		ShareAction act1 = new ShareAction(action: ShareAction.MERGE,
			id: p1.id,
			permission: "dElEgATE")

		then:
		act1.validate() == true
		act1.buildPhone() == p1
		act1.buildSharePermission() == SharePermission.DELEGATE

		when:
		act1.permission = TestUtils.randString()

		then:
		act1.validate() == false
		act1.errors.getFieldError("permission").code == "invalid"
	}
}
