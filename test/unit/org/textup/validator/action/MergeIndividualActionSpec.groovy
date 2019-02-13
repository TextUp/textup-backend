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
class MergeIndividualActionSpec extends CustomSpec {

	static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

	void "test constraints"() {
		when: "all empty"
		MergeIndividualAction act1 = new MergeIndividualAction()

		then:
		act1.validate() == false

		when: "empty for default merge action"
		act1.action = MergeIndividualAction.DEFAULT

		then:
		act1.validate() == false
		act1.errors.getFieldError("mergeIds").code == "nullable"

		when: "empty for reconciliation action"
		act1.action = MergeIndividualAction.RECONCILE

		then:
		act1.validate() == false
		act1.errors.getFieldError("mergeIds").code == "nullable"
		act1.errors.getFieldError("nameId").code == "requiredForReconciliation"
		act1.errors.getFieldError("noteId").code == "requiredForReconciliation"
	}

	void "test constraints for default merge"() {
		given: "empty default merge action"
		Long nonexistentId = -88L
		MergeIndividualAction act1 = new MergeIndividualAction(action: MergeIndividualAction.DEFAULT)

		when: "merge ids are empty"
		act1.mergeIds = []

		then:
		act1.validate() == false
		act1.errors.getFieldError("toBeMergedIds").code == "emptyOrNotACollection"
		act1.buildName() == null
		act1.buildNote() == null

		when: "merge ids are not a collection"
		act1.mergeIds = "not a collection"

		then:
		act1.validate() == false
		act1.errors.getFieldError("toBeMergedIds").code == "emptyOrNotACollection"

		when: "merge ids are not all numeric values"
		act1.mergeIds = [nonexistentId, "not a number", [:]]

		then: "do not do existence check here"
		act1.validate()
		act1.toBeMergedIds == [nonexistentId]
	}

	void "test constraints for reconciliation action"() {
		given: "reconciliation action with valid merge ids"
		IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord()
		ipr1.name = TestUtils.randString()
		IndividualPhoneRecord ipr2 = TestUtils.buildIndPhoneRecord()
		ipr2.note = TestUtils.randString()
		MergeIndividualAction act1 = new MergeIndividualAction(action: MergeIndividualAction.RECONCILE,
				mergeIds: [ipr1.id, ipr2.id])

		when: "no name or note ids specified"
		act1.nameId = null
		act1.noteId = null

		then:
		act1.validate() == false
		act1.errors.getFieldError("nameId").code == "requiredForReconciliation"
		act1.errors.getFieldError("noteId").code == "requiredForReconciliation"
		ipr1.id in act1.toBeMergedIds
		ipr2.id in act1.toBeMergedIds
		act1.buildName() == null
		act1.buildNote() == null

		when: "name id is not in the list of merge ids"
		act1.nameId = -88L

		then:
		act1.validate() == false
		act1.errors.getFieldError("nameId").code == "notInIdsList"
		act1.errors.getFieldError("noteId").code == "requiredForReconciliation"
		act1.buildName() == null
		act1.buildNote() == null

		when: "note id is not in the list of merge ids"
		act1.nameId = ipr1.id
		act1.noteId = -88L

		then:
		act1.validate() == false
		act1.errors.getFieldError("noteId").code == "notInIdsList"
		act1.buildName() == ipr1.name
		act1.buildNote() == null

		when: "name and note ids are in the list of merge ids"
		act1.noteId = ipr2.id

		then:
		act1.validate()
		act1.buildName() == ipr1.name
		act1.buildNote() == ipr2.note
	}
}
