package org.textup.validator.action

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import org.textup.*
import org.textup.test.*
import spock.lang.*

@Domain([AnnouncementReceipt, ContactNumber, CustomAccountDetails, FeaturedAnnouncement,
    FutureMessage, GroupPhoneRecord, IncomingSession, IndividualPhoneRecord, Location, MediaElement,
    MediaElementVersion, MediaInfo, Organization, OwnerPolicy, Phone, PhoneNumberHistory,
    PhoneOwnership, PhoneRecord, PhoneRecordMembers, Record, RecordCall, RecordItem,
    RecordItemReceipt, RecordNote, RecordNoteRevision, RecordText, Role, Schedule,
    SimpleFutureMessage, Staff, StaffRole, Team, Token])
@TestMixin(HibernateTestMixin)
@Unroll
class TeamActionSpec extends Specification {

	static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

	void "test constraints for #action"() {
        given:
        Staff s1 = TestUtils.buildStaff()

		when: "empty"
		TeamAction act1 = new TeamAction()

		then:
		act1.validate() == false
		act1.errors.getFieldError("id").code == "nullable"

		when: "all valid"
		act1.action = action
		act1.id = s1.id

		then:
		act1.validate() == true
        act1.buildStaff() == s1

		when: "nonexistent staff id"
		act1.id = -88L

		then:
		act1.validate() == false
		act1.errors.getFieldError("id").code == "doesNotExist"
        act1.buildStaff() == null

        where:
        action            | _
        TeamAction.ADD    | _
        TeamAction.REMOVE | _
	}
}
