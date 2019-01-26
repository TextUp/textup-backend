package org.textup.validator

import grails.test.mixin.*
import grails.test.mixin.gorm.*
import grails.test.mixin.hibernate.*
import grails.test.mixin.support.*
import grails.test.runtime.*
import grails.validation.*
import org.joda.time.*
import org.textup.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import spock.lang.*

@Domain([AnnouncementReceipt, ContactNumber, CustomAccountDetails, FeaturedAnnouncement,
    FutureMessage, GroupPhoneRecord, IncomingSession, IndividualPhoneRecord, Location, MediaElement,
    MediaElementVersion, MediaInfo, Organization, OwnerPolicy, Phone, PhoneNumberHistory,
    PhoneOwnership, PhoneRecord, PhoneRecordMembers, Record, RecordCall, RecordItem,
    RecordItemReceipt, RecordNote, RecordNoteRevision, RecordText, Role, Schedule,
    SimpleFutureMessage, Staff, StaffRole, Team, Token])
@TestMixin(HibernateTestMixin)
class MergeGroupItemSpec extends CustomSpec {

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
		MergeGroupItem mItem = new MergeGroupItem()

		then:
		mItem.validate() == false
		mItem.errors.errorCount == 2
		mItem.errors.getFieldError("numberAsString").code == "nullable"
		mItem.errors.getFieldError("contactIds").code == "minSize.notmet"
		mItem.number.number == null
		mItem.mergeWith.isEmpty() == true

		when: "empty contact ids"
		mItem.contactIds = []

		then:
		mItem.validate() == false
		mItem.errors.errorCount == 2
		mItem.errors.getFieldError("numberAsString").code == "nullable"
		mItem.errors.getFieldError("contactIds").code == "minSize.notmet"
		mItem.number.number == null
		mItem.mergeWith.isEmpty() == true

		when: "invalid phone number"
		mItem.number = new PhoneNumber(number:"I am not a valid phone number")

		then:
		mItem.validate() == false
		mItem.errors.errorCount == 2
		mItem.errors.getFieldError("numberAsString").code == "format"
		mItem.errors.getFieldError("contactIds").code == "minSize.notmet"
		mItem.number.number == ""
		mItem.mergeWith.isEmpty() == true

		when: "valid with a nonexistent"
		PhoneNumber pNum = new PhoneNumber(number:"111 adfasfasdfads222 3333")
		mItem.number = pNum
		mItem.contactIds = [-88L] // existence check happens in MergeGroup

		then:
		mItem.validate() == true
		mItem.number.number == pNum.number
		mItem.mergeWith.isEmpty() == true // existence check happens in MergeGroup

		when: "valid with an existent contact"
		mItem.contactIds = [c1.id]

		then:
		mItem.validate() == true
		mItem.number.number == pNum.number
		mItem.mergeWith.every { it.id == c1.id }
	}
}
