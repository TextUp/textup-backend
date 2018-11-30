package org.textup.validator.action

import org.textup.test.*
import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import org.textup.*

@Domain([Contact, Phone, ContactTag, ContactNumber, Record, RecordItem, RecordText,
	RecordCall, RecordItemReceipt, SharedContact, Staff, Team, Organization, Schedule,
	Location, WeeklySchedule, PhoneOwnership, FeaturedAnnouncement, IncomingSession,
	AnnouncementReceipt, Role, StaffRole, NotificationPolicy,
	MediaInfo, MediaElement, MediaElementVersion])
@TestMixin(HibernateTestMixin)
class MergeActionSpec extends CustomSpec {

	static doWithSpring = {
		resultFactory(ResultFactory)
	}

    def setup() {
    	setupData()
    }

    def cleanup() {
    	cleanupData()
    }

	void "test constraints error"() {
		when: "all empty"
		MergeAction act1 = new MergeAction()

		then:
		act1.validate() == false
		// would be 4 but nameId and noteId optional if not reconciliation merge
		act1.errors.errorCount == 2

		when: "empty for default merge action"
		act1.action = Constants.MERGE_ACTION_DEFAULT

		then:
		act1.validate() == false
		act1.errors.errorCount == 1
		act1.errors.getFieldError("mergeIds").code == "nullable"

		when: "empty for reconciliation action"
		act1.action = Constants.MERGE_ACTION_RECONCILE

		then:
		act1.validate() == false
		act1.errors.errorCount == 3
		act1.errors.getFieldError("mergeIds").code == "nullable"
		act1.errors.getFieldError("nameId").code == "requiredForReconciliation"
		act1.errors.getFieldError("noteId").code == "requiredForReconciliation"
	}

	void "test constraints for default merge"() {
		given: "empty default merge action"
		MergeAction act1 = new MergeAction(action:Constants.MERGE_ACTION_DEFAULT)

		when: "merge ids are empty"
		act1.mergeIds = []

		then:
		act1.validate() == false
		act1.errors.errorCount == 1
		act1.errors.getFieldError("mergeIds").code == "emptyOrNotACollection"
		act1.contacts.isEmpty() == true
		act1.name == null
		act1.note == null

		when: "merge ids are not a collection"
		act1.mergeIds = "not a collection"

		then:
		act1.validate() == false
		act1.errors.errorCount == 1
		act1.errors.getFieldError("mergeIds").code == "emptyOrNotACollection"
		act1.contacts.isEmpty() == true
		act1.name == null
		act1.note == null

		when: "merge ids are not all numeric values"
		act1.mergeIds = [-88L, "not a number", [:]]

		then:
		act1.validate() == false
		act1.errors.errorCount == 1
		act1.errors.getFieldError("mergeIds").code == "notAllNumbers"
		act1.contacts.isEmpty() == true
		act1.name == null
		act1.note == null

		when: "merge ids point to contacts that do not all exist"
		act1.mergeIds = [c1.id, -88L]

		then:
		act1.validate() == false
		act1.errors.errorCount == 1
		act1.errors.getFieldError("mergeIds").code == "someDoNotExist"
		act1.contacts.every { it.id == c1.id }
		act1.name == null
		act1.note == null

		when: "merge ids are number in a collection corresponding to existent contacts"
		Collection<Contact> toBeMergedIds = [c1, c1_1]*.id
		act1.mergeIds = toBeMergedIds

		then:
		act1.validate() == true
		act1.contacts.every { it.id in toBeMergedIds }
		act1.name == null
		act1.note == null
	}

	void "test constraints for reconciliation action"() {
		given: "reconciliation action with valid merge ids"
		Collection<Contact> toBeMergedIds = [c1, c1_1]*.id
		MergeAction act1 = new MergeAction(action:Constants.MERGE_ACTION_RECONCILE,
				mergeIds:toBeMergedIds)

		when: "no name or note ids specified"
		act1.nameId = null
		act1.noteId = null

		then:
		act1.validate() == false
		act1.errors.errorCount == 2
		act1.errors.getFieldError("nameId").code == "requiredForReconciliation"
		act1.errors.getFieldError("noteId").code == "requiredForReconciliation"
		act1.contacts.every { it.id in toBeMergedIds }
		act1.name == null
		act1.note == null

		when: "name id is not in the list of merge ids"
		act1.nameId = -88L

		then:
		act1.validate() == false
		act1.errors.errorCount == 2
		act1.errors.getFieldError("nameId").code == "notInIdsList"
		act1.errors.getFieldError("noteId").code == "requiredForReconciliation"
		act1.contacts.every { it.id in toBeMergedIds }
		act1.name == null
		act1.note == null

		when: "note id is not in the list of merge ids"
		act1.nameId = c1.id
		act1.noteId = -88L

		then:
		act1.validate() == false
		act1.errors.errorCount == 1
		act1.errors.getFieldError("noteId").code == "notInIdsList"
		act1.contacts.every { it.id in toBeMergedIds }
		act1.name == c1.name
		act1.note == null

		when: "name and note ids are in the list of merge ids"
		act1.noteId = c1.id

		then:
		act1.validate() == true
		act1.contacts.every { it.id in toBeMergedIds }
		act1.name == c1.name
		act1.note == c1.note
	}
}
