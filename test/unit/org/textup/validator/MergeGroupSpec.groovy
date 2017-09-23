package org.textup.validator

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
class MergeGroupSpec extends CustomSpec {

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
		MergeGroup mGroup = new MergeGroup()

		then:
		mGroup.validate() == false
		mGroup.errors.errorCount == 2
		mGroup.errors.getFieldError("targetContactId").code == "nullable"
		mGroup.errors.getFieldError("possibleMerges").code == "minSize.notmet"
		mGroup.targetContact == null

		when: "nonexistent target contact id"
		mGroup.targetContactId = -88L

		then:
		mGroup.validate() == false
		mGroup.errors.errorCount == 2
		mGroup.errors.getFieldError("targetContactId").code == "doesNotExist"
		mGroup.errors.getFieldError("possibleMerges").code == "minSize.notmet"
		mGroup.targetContact == null

		when: "items with overlapping ids"
		MergeGroupItem mItem1 = new MergeGroupItem(contactIds:[c1.id]),
			mItem2 = new MergeGroupItem(contactIds:[c1.id])
		// note that the standard validation method does not cascade
		// validation to the contained items
		assert mItem1.validate() == false
		assert mItem2.validate() == false
		mGroup.possibleMerges = [mItem1, mItem2]

		then:
		mGroup.validate() == false
		mGroup.errors.errorCount == 2
		mGroup.errors.getFieldError("targetContactId").code == "doesNotExist"
		mGroup.errors.getFieldError("possibleMerges").code == "overlappingId"
		mGroup.targetContact == null

		when: "items with some nonexistent ids"
		mItem1.contactIds[0] = -109L // must not be -88L or will overlap with target contact id
		mGroup.possibleMerges = [mItem1]

		then:
		mGroup.validate() == false
		mGroup.errors.errorCount == 2
		mGroup.errors.getFieldError("targetContactId").code == "doesNotExist"
		mGroup.errors.getFieldError("possibleMerges").code == "someDoNotExist"
		mGroup.targetContact == null

		when: "all valid"
		mGroup.targetContactId = c1.id
		mItem1.contactIds[0] = c1_1.id
		mItem2.contactIds[0] = c1_2.id
		mGroup.possibleMerges = [mItem1, mItem2]

		then:
		mGroup.validate() == true
		mGroup.targetContact.id == c1.id
	}

	void "test cascading validation to contained items"() {
		given: "valid group with invalid items"
		MergeGroupItem mItem1 = new MergeGroupItem(contactIds:[c1_1.id]),
			mItem2 = new MergeGroupItem(contactIds:[c1_2.id])
		MergeGroup mGroup = new MergeGroup(targetContactId:c1.id, possibleMerges:[mItem1, mItem2])
		assert mItem1.validate() == false
		assert mItem2.validate() == false

		when: "shallow validate"
		assert mGroup.validate() == true

		then:
		mGroup.errors.errorCount == 0

		when: "deep validate"
		assert mGroup.deepValidate() == false

		then:
		mGroup.errors.errorCount == 1
		mGroup.errors.getFieldErrorCount("possibleMerges") == 1
		mGroup.errors.getFieldError("possibleMerges").code == "mergeGroup.possibleMerges.invalidItems"
	}

	void "test adding merge group items"() {
		given: "merge group with valid target and no items"
		MergeGroup mGroup = new MergeGroup(targetContactId:c1.id)
		assert mGroup.targetContact.id == c1.id

		when: "adding a merge group item"
		PhoneNumber pNum = new PhoneNumber(number:"111222 afasf 3333")
		assert pNum.validate() == true
		mGroup.add(pNum, [c1_1.id])

		then:
		mGroup.validate() == true
		mGroup.deepValidate() == true
		mGroup.possibleMerges.size() == 1
		mGroup.possibleMerges[0].number.number == pNum.number
		mGroup.possibleMerges[0].contactIds.size() == 1
		mGroup.possibleMerges[0].contactIds[0] == c1_1.id
	}
}