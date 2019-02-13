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
        TestUtils.standardMockSetup()
    }

	void "test constraints"() {
		given:
		PhoneNumber invalidNum = PhoneNumber.create("invalid")
		PhoneNumber pNum1 = TestUtils.randPhoneNumber()
		IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord()

		when: "empty"
		MergeGroupItem mItem1 = MergeGroupItem.create(null, null)

		then:
		mItem1.validate() == false
		mItem1.errors.getFieldError("number").code == "nullable"
		mItem1.errors.getFieldError("mergeIds").code == "minSize.notmet"
		mItem1.number == null
		mItem1.buildMergeWith().isEmpty()

		when: "empty contact ids"
		mItem1 = MergeGroupItem.create(null, [])

		then:
		mItem1.validate() == false
		mItem1.errors.getFieldError("number").code == "nullable"
		mItem1.errors.getFieldError("mergeIds").code == "minSize.notmet"
		mItem1.number == null
		mItem1.buildMergeWith().isEmpty()

		when: "valid with a nonexistent"
		mItem1 = MergeGroupItem.create(pNum1, [-88L])

		then:
		mItem1.validate() == false
		mItem1.errors.getFieldError("mergeIds").code == "someDoNotExist"
		mItem1.number.number == pNum1.number
		mItem1.buildMergeWith().isEmpty() // existence check happens in MergeGroup

		when: "invalid phone number"
		mItem1 = MergeGroupItem.create(invalidNum, [ipr1.id])

		then:
		mItem1.validate() == false
		mItem1.errors.getFieldErrorCount("number.number") > 0
		mItem1.number.number == ""
		mItem1.buildMergeWith().every { it.id == ipr1.id }

		when: "valid with an existent contact"
		mItem1 = MergeGroupItem.create(pNum1, [ipr1.id])

		then:
		mItem1.validate()
		mItem1.number.number == pNum1.number
		mItem1.buildMergeWith().every { it.id == ipr1.id }
	}
}
