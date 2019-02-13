package org.textup.validator.action

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import org.textup.*
import org.textup.test.*

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
        TestUtils.standardMockSetup()
    }

	void "test constraints"() {
        given:
        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord()

		when: "empty"
		GroupMemberAction act1 = new GroupMemberAction()

		then:
		act1.validate() == false


		when: "nonexistent"
		act1.id = -88L

		then:
		act1.validate() == false
		act1.errors.errorCount == 2
		act1.errors.getFieldError("id").code == "doesNotExist"

		when: "all valid"
		act1.with {
			action = GroupMemberAction.ADD
			id = ipr1.id
		}

		then:
		act1.validate() == true
        act1.buildPhoneRecord() == ipr1
	}
}
