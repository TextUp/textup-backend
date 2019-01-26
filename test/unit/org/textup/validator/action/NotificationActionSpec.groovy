package org.textup.validator.action

import org.textup.test.*
import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import org.textup.*
import org.textup.type.NotificationLevel

@Domain([AnnouncementReceipt, ContactNumber, CustomAccountDetails, FeaturedAnnouncement,
    FutureMessage, GroupPhoneRecord, IncomingSession, IndividualPhoneRecord, Location, MediaElement,
    MediaElementVersion, MediaInfo, Organization, OwnerPolicy, Phone, PhoneNumberHistory,
    PhoneOwnership, PhoneRecord, PhoneRecordMembers, Record, RecordCall, RecordItem,
    RecordItemReceipt, RecordNote, RecordNoteRevision, RecordText, Role, Schedule,
    SimpleFutureMessage, Staff, StaffRole, Team, Token])
@TestMixin(HibernateTestMixin)
class NotificationActionSpec extends CustomSpec {

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
    	NotificationAction act1 = new NotificationAction()

    	then:
    	act1.validate() == false
    	act1.errors.errorCount == 2
    	act1.errors.getFieldError("action").code == "nullable"
    	act1.errors.getFieldError("id").code == "nullable"

    	when: "empty for enable"
    	act1.action = Constants.NOTIFICATION_ACTION_ENABLE

    	then:
    	act1.validate() == false
    	act1.errors.errorCount == 1
    	act1.errors.getFieldError("id").code == "nullable"

    	when: "empty for disable"
    	act1.action = Constants.NOTIFICATION_ACTION_DISABLE

    	then:
    	act1.validate() == false
    	act1.errors.errorCount == 1
    	act1.errors.getFieldError("id").code == "nullable"

    	when: "empty for changing default"
    	act1.action = Constants.NOTIFICATION_ACTION_DEFAULT

    	then:
    	act1.validate() == false
    	act1.errors.errorCount == 2
    	act1.errors.getFieldError("id").code == "nullable"
    	act1.errors.getFieldError("level").code == "requiredForChangingDefault"
    }

    void "test constraints for changing default notification level"() {
    	given: "empty changing default action"
    	NotificationAction act1 = new NotificationAction(action:
    		Constants.NOTIFICATION_ACTION_DEFAULT)

    	when: "nonexistent staff id"
    	act1.id = -88L

    	then:
    	act1.validate() == false
    	act1.errors.errorCount == 2
    	act1.errors.getFieldError("id").code == "doesNotExist"
    	act1.errors.getFieldError("level").code == "requiredForChangingDefault"

    	when: "existent staff id"
    	act1.id  = s1.id

    	then:
    	act1.validate() == false
    	act1.errors.errorCount == 1
    	act1.errors.getFieldError("level").code == "requiredForChangingDefault"

    	when: "invalid level"
    	act1.level = "i am an invalid level"

    	then: "level as enum is null"
    	act1.validate() == false
    	act1.errors.errorCount == 1
    	act1.errors.getFieldError("level").code == "invalid"

    	when: "a valid notification policy"
    	act1.level = "aLl"

    	then: "level as enum is not null"
    	act1.validate() == true
    }
}
