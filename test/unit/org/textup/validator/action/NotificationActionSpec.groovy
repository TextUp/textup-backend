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
@Unroll
class NotificationActionSpec extends Specification {

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
    	NotificationAction act1 = new NotificationAction()

    	then:
    	act1.validate() == false

    	when:
    	act1.action = action

    	then:
    	act1.validate() == false
    	act1.errors.getFieldError("id").code == "nullable"

    	when:
        act1.id = -88L

    	then:
    	act1.validate() == false
    	act1.errors.getFieldError("id").code == "doesNotExist"

        when:
        act1.id = s1.id

        then:
        act1.validate()

        where:
        action                     | _
        NotificationAction.ENABLE  | _
        NotificationAction.DISABLE | _
    }
}
