package org.textup

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestFor
import grails.test.mixin.TestMixin
import grails.validation.ValidationErrors
import org.apache.commons.lang3.tuple.Pair
import org.springframework.context.MessageSource
import org.textup.type.ContactStatus
import org.textup.type.FutureMessageType
import org.textup.type.SharePermission
import org.textup.type.StaffStatus
import org.textup.util.*
import org.textup.validator.MergeGroup
import org.textup.validator.MergeGroupItem
import spock.lang.Shared
import spock.lang.Specification

@TestFor(DuplicateService)
@Domain([Contact, Phone, ContactTag, ContactNumber, Record, RecordItem, RecordText,
  RecordCall, RecordItemReceipt, SharedContact, Staff, Team, Organization,
  Schedule, Location, WeeklySchedule, PhoneOwnership, Role, StaffRole, NotificationPolicy,
  RecordNote, RecordNoteRevision, FutureMessage, SimpleFutureMessage])
@TestMixin(HibernateTestMixin)
class DuplicateServiceSpec extends CustomSpec {

    static doWithSpring = {
		resultFactory(ResultFactory)
	}

    def setup() {
    	setupData()
    	service.resultFactory = getResultFactory()

    	FutureMessage.metaClass.refreshTrigger = { -> null }
    }

    def cleanup() {
    	cleanupData()
    }

    // Build merge groups
    // ------------------

    void "test getting and building contacts data"() {
        given: "contacts with some deleted"
        Collection<Contact> existingContacts = p1.contacts
        assert existingContacts.size() > 0
        Collection<Contact> deletedContacts = []
        deletedContacts << p1.createContact([isDeleted:true], [TestHelpers.randPhoneNumber()]).payload
        deletedContacts << p1.createContact([isDeleted:true], [TestHelpers.randPhoneNumber()]).payload
        deletedContacts << p1.createContact([isDeleted:true], [TestHelpers.randPhoneNumber()]).payload

        when: "getting contacts data"
        List<Object[]> data = service.getContactsData({ eq("phone", p1) })

        then: "contacts excluding deleted ones"
        data.each {
            assert (it[0] as Long) in existingContacts*.id
            assert !((it[0] as Long) in deletedContacts*.id)
        }

        when: "build extracted data"
        Map<Long, HashSet<String>> numToContactIds = service.buildContactsData(data)

        then:
        numToContactIds.values().flatten().each {
            assert it in existingContacts*.id
            assert !(it in deletedContacts*.id)
        }
        numToContactIds.keySet().each { String num ->
            assert existingContacts.find { num in it.numbers*.number } != null
            assert deletedContacts.find { num in it.numbers*.number } == null
        }
    }

    void "test go through merging process"() {
        given:
        String strNum1 = TestHelpers.randPhoneNumber()
        String strNum2 = TestHelpers.randPhoneNumber()
        String strNum3 = TestHelpers.randPhoneNumber()
        String strNum4 = TestHelpers.randPhoneNumber()
        String strNum5 = TestHelpers.randPhoneNumber()
        Long c1Id = p1.createContact([:], [TestHelpers.randPhoneNumber()]).payload.id
        Long c2Id = p1.createContact([:], [TestHelpers.randPhoneNumber()]).payload.id
        Long c3Id = p1.createContact([:], [TestHelpers.randPhoneNumber()]).payload.id
        Long c4Id = p1.createContact([:], [TestHelpers.randPhoneNumber()]).payload.id
        Long c5Id = p1.createContact([:], [TestHelpers.randPhoneNumber()]).payload.id
        Long c6Id = p1.createContact([:], [TestHelpers.randPhoneNumber()]).payload.id
        Contact.withSession { it.flush() }

        Map<String, HashSet<Long>> numToContactIds = [
            (strNum1): new HashSet<Long>([c1Id, c2Id]),
            (strNum2): new HashSet<Long>([c1Id, c3Id]),
            (strNum3): new HashSet<Long>([c1Id, c4Id]),
            (strNum4): new HashSet<Long>([c4Id, c5Id]),
            (strNum5): new HashSet<Long>([c6Id])
        ]

        when: "building possible merge groups"
        Result<Pair<Map<Long, Collection<String>>, Collection<String>>> res1 =
            service.buildPossibleMerges(numToContactIds)
        assert res1.status == ResultStatus.OK
        Map<Long, Collection<String>> contactIdToMergeNums = res1.payload.left
        Collection<String> possibleMergeNums = res1.payload.right

        then: "one numbers with multiple ids quality as potential merge groups"
        contactIdToMergeNums.size() == 5
        contactIdToMergeNums.containsKey(c6Id) == false
        contactIdToMergeNums[c1Id].every { it in [strNum1, strNum2, strNum3] }
        contactIdToMergeNums[c2Id].every { it in [strNum1] }
        contactIdToMergeNums[c3Id].every { it in [strNum2] }
        contactIdToMergeNums[c4Id].every { it in [strNum3, strNum4] }
        contactIdToMergeNums[c5Id].every { it in [strNum4] }

        possibleMergeNums.size() == 4
        possibleMergeNums.every { it in [strNum1, strNum2, strNum3, strNum4] }
        (strNum5 in possibleMergeNums) == false

        when: "confirm possible merges"
        Result<Map<Long,Collection<String>>> res2 = service.confirmPossibleMerges(numToContactIds,
            contactIdToMergeNums, possibleMergeNums)
        assert res2.status == ResultStatus.OK
        Map<Long,Collection<String>> targetIdToConfirmedNums = res2.payload

        then:
        // strNum3 didn't make the cut because both c1Id and c4Id are ambiguous and each merge group
        // can only have one ambiguous contact id
        targetIdToConfirmedNums.size() == 2
        targetIdToConfirmedNums[c1Id].each { it in [strNum1, strNum2] }
        targetIdToConfirmedNums[c4Id].each { it in [strNum4] }

        when: "building merge groups from confirmed merges"
        Result<List<MergeGroup>> res3 = service.buildMergeGroups(numToContactIds,
            targetIdToConfirmedNums)
        assert res3.status == ResultStatus.OK
        List<MergeGroup> mGroups = res3.payload

        then:
        mGroups.size() == 2
        mGroups.find { it.targetContactId == c1Id }.possibleMerges.each { MergeGroupItem mItem ->
            assert mItem.numberAsString in [strNum1, strNum2]
            assert mItem.contactIds.every { it in [c2Id, c3Id] }
        }
        mGroups.find { it.targetContactId == c4Id }.possibleMerges.each { MergeGroupItem mItem ->
            assert mItem.numberAsString in [strNum4]
            assert mItem.contactIds.every { it in [c5Id] }
        }
    }

    void "test building merge groups overall"() {
        given: "the same setup as the manual walk through in the previous method"
        Phone phone1 = new Phone(numberAsString:TestHelpers.randPhoneNumber())
        phone1.resultFactory = getResultFactory()
        phone1.resultFactory.messageSource = messageSource
        phone1.updateOwner(s1)
        phone1.save(flush:true, failOnError:true)

        String strNum1 = TestHelpers.randPhoneNumber()
        String strNum2 = TestHelpers.randPhoneNumber()
        String strNum3 = TestHelpers.randPhoneNumber()
        String strNum4 = TestHelpers.randPhoneNumber()
        String strNum5 = TestHelpers.randPhoneNumber()
        Long c1Id = phone1.createContact([:], [strNum1, strNum2, strNum3]).payload.id
        Long c2Id = phone1.createContact([:], [strNum1]).payload.id
        Long c3Id = phone1.createContact([:], [strNum2]).payload.id
        Long c4Id = phone1.createContact([:], [strNum3, strNum4]).payload.id
        Long c5Id = phone1.createContact([:], [strNum4]).payload.id
        Long c6Id = phone1.createContact([:], [strNum5]).payload.id
        Contact.withSession { it.flush() }

        // the above setup is build to generate such a setup:
        // Map<String, HashSet<Long>> numToContactIds = [
        //     (strNum1): new HashSet<Long>([c1Id, c2Id]),
        //     (strNum2): new HashSet<Long>([c1Id, c3Id]),
        //     (strNum3): new HashSet<Long>([c1Id, c4Id]),
        //     (strNum4): new HashSet<Long>([c4Id, c5Id]),
        //     (strNum5): new HashSet<Long>([c6Id])
        // ]

        when: "run through providing the contact ids"
        Result<List<MergeGroup>> res1 = service.findDuplicates([c1Id, c2Id, c3Id, c4Id, c5Id, c6Id])
        assert res1.status == ResultStatus.OK
        List<MergeGroup> mGroups = res1.payload

        then:
        mGroups.size() == 2
        mGroups.find { it.targetContactId == c1Id }.possibleMerges.each { MergeGroupItem mItem ->
            assert mItem.numberAsString in [strNum1, strNum2]
            assert mItem.contactIds.every { it in [c2Id, c3Id] }
        }
        mGroups.find { it.targetContactId == c4Id }.possibleMerges.each { MergeGroupItem mItem ->
            assert mItem.numberAsString in [strNum4]
            assert mItem.contactIds.every { it in [c5Id] }
        }

        when: "run through providing the overall phone's id"
        res1 = service.findDuplicates(phone1)
        assert res1.status == ResultStatus.OK
        mGroups = res1.payload

        then: "same result"
        mGroups.size() == 2
        mGroups.find { it.targetContactId == c1Id }.possibleMerges.each { MergeGroupItem mItem ->
            assert mItem.numberAsString in [strNum1, strNum2]
            assert mItem.contactIds.every { it in [c2Id, c3Id] }
        }
        mGroups.find { it.targetContactId == c4Id }.possibleMerges.each { MergeGroupItem mItem ->
            assert mItem.numberAsString in [strNum4]
            assert mItem.contactIds.every { it in [c5Id] }
        }
    }

    void "test building merge groups overall error conditions"() {
        given:
        addToMessageSource("duplicateService.findDuplicates.missingContactIds")

        when: "no contact ids provided"
        Result<List<MergeGroup>> res1 = service.findDuplicates([])

        then:
        res1.success == false
        res1.status == ResultStatus.UNPROCESSABLE_ENTITY
        res1.errorMessages[0] == "duplicateService.findDuplicates.missingContactIds"

        when: "null provided"
        res1 = service.findDuplicates(null)

        then: "defaults to the contact ids implementation and returns UNPROCESSABLE_ENTITY"
        res1.success == false
        res1.status == ResultStatus.UNPROCESSABLE_ENTITY
        res1.errorMessages[0] == "duplicateService.findDuplicates.missingContactIds"
    }

    // Merging
    // -------

    protected void delete(Contact c1) {
  		c1.delete()
  		c1.record.delete()
  	}
  	protected Collection<ContactTag> addTags(Contact contact1, Contact contact2) {
  		assert contact1.phone.id == contact2.phone.id

  		ContactTag cTag1 = contact1.phone.createTag([name:TestHelpers.randPhoneNumber()]).payload
    	ContactTag cTag2 = contact1.phone.createTag([name:TestHelpers.randPhoneNumber()]).payload
    	ContactTag cTag3 = contact1.phone.createTag([name:TestHelpers.randPhoneNumber()]).payload

    	Collection<ContactTag> cTags = [cTag1, cTag2, cTag3]
    	cTags*.save(flush:true, failOnError:true)

    	cTag1.addToMembers(contact1)
    	cTag1.addToMembers(contact2)
    	cTag2.addToMembers(contact2)
    	cTag3.addToMembers(contact2)

    	Contact.withSession { it.flush() }
    	cTags
  	}
  	protected Collection<SharedContact> addSharedContacts(Contact contact1, Contact contact2) {
  		assert contact1.phone.id == contact2.phone.id

  		Collection<SharedContact> sharedContacts = []
  		sharedContacts << new SharedContact(contact:contact1, sharedBy:p1, sharedWith:p2,
			permission:SharePermission.DELEGATE)
    	sharedContacts << new SharedContact(contact:contact1, sharedBy:p1, sharedWith:p3,
			permission:SharePermission.VIEW)
    	sharedContacts << new SharedContact(contact:contact1, sharedBy:p1, sharedWith:otherP1,
			permission:SharePermission.VIEW)
    	sharedContacts << new SharedContact(contact:contact2, sharedBy:p1, sharedWith:p2,
			permission:SharePermission.DELEGATE)
    	sharedContacts*.save(flush:true, failOnError:true)
  	}
  	protected Collection<RecordItem> addRecordItems(Contact contact1, Contact contact2) {
  		assert contact1.phone.id == contact2.phone.id

  		Collection<RecordItem> rItems = []
		rItems << contact1.record.addText([contents:"hello"], null).payload
		rItems << contact1.record.addCall([:], null).payload
		rItems << contact1.record.addText([contents:"hello"], null).payload
		rItems << contact2.record.addText([contents:"hello"], null).payload
		rItems << contact2.record.addCall([:], null).payload
		rItems << new RecordNote(record:contact1.record)
		rItems*.save(flush:true, failOnError:true)
  	}
  	protected Collection<FutureMessage> addFutureMessage(Contact contact1, Contact contact2) {
  		assert contact1.phone.id == contact2.phone.id

  		Collection<FutureMessage> fMsgs = []
		fMsgs << new FutureMessage(type:FutureMessageType.TEXT, message:"hi", record:contact1.record)
    	fMsgs << new FutureMessage(type:FutureMessageType.TEXT, message:"hi", record:contact1.record)
    	fMsgs << new FutureMessage(type:FutureMessageType.CALL, message:"hi", record:contact2.record)
    	fMsgs*.save(flush:true, failOnError:true)
  	}

    void "test findng most permissible status"() {
    	ContactStatus unr = ContactStatus.UNREAD,
    		act = ContactStatus.ACTIVE,
    		arc = ContactStatus.ARCHIVED,
    		blo = ContactStatus.BLOCKED

    	expect:
    	service.findMostPermissibleStatus([unr]) == act
    	service.findMostPermissibleStatus([act]) == act
    	service.findMostPermissibleStatus([arc]) == arc
    	service.findMostPermissibleStatus([blo]) == blo
    	service.findMostPermissibleStatus([unr, act]) == act
    	service.findMostPermissibleStatus([unr, arc]) == act
    	service.findMostPermissibleStatus([unr, blo]) == act
    	service.findMostPermissibleStatus([act, arc]) == act
    	service.findMostPermissibleStatus([act, blo]) == act
    	service.findMostPermissibleStatus([arc, blo]) == arc
    	service.findMostPermissibleStatus([unr, act, arc]) == act
    	service.findMostPermissibleStatus([unr, act, blo]) == act
    	service.findMostPermissibleStatus([unr, arc, blo]) == act
    	service.findMostPermissibleStatus([act, arc, blo]) == act
    	service.findMostPermissibleStatus([unr, act, arc, blo]) == act
    }

    void "test merging contact numbers"() {
    	given: "contact numbers belonging to another contact"
    	Contact contact1 = p1.createContact([:], [TestHelpers.randPhoneNumber(), TestHelpers.randPhoneNumber()]).payload
    	Contact contact2 = p1.createContact([:], [TestHelpers.randPhoneNumber(), TestHelpers.randPhoneNumber()]).payload
    	[contact1, contact2]*.save(flush:true, failOnError:true)
    	int cnBaseline = ContactNumber.count()
    	int recBaseline = Record.count()
    	int contBaseline = Contact.count()

    	when: "merging numbers"
    	Collection<ContactNumber> numsToMerge = contact1.numbers + contact2.numbers
    	int previousNumNums = c1.numbers.size()

    	assert service.mergeNumbers(c1, numsToMerge).status == ResultStatus.NO_CONTENT
    	Contact.withSession { it.flush() }

    	then: "new contact numbers are created and old ones remain"
    	ContactNumber.count() == cnBaseline + numsToMerge.size()
    	Record.count() == recBaseline
    	Contact.count() == contBaseline
    	c1.numbers.size() == previousNumNums + numsToMerge.size()

    	when: "delete afterwards to ensure that all dependencies are eliminated"
    	delete(contact1)
    	delete(contact2)
    	Contact.withSession { it.flush() }

    	then:
  		ContactNumber.count() == cnBaseline // 4 contact numbers deleted again after adding 4
  		Record.count() == recBaseline - 2
    	Contact.count() == contBaseline - 2
    }

    void "test merging tags"() {
    	given: "target contact and other contacts that are members of other tags"
    	Contact contact1 = p1.createContact([:], [TestHelpers.randPhoneNumber(), TestHelpers.randPhoneNumber()]).payload
    	Contact contact2 = p1.createContact([:], [TestHelpers.randPhoneNumber(), TestHelpers.randPhoneNumber()]).payload
    	[contact1, contact2]*.save(flush:true, failOnError:true)

    	Collection<ContactTag> cTags = addTags(contact1, contact2)

    	int cnBaseline = ContactNumber.count()
    	int tagBaseline = ContactTag.count()
    	int recBaseline = Record.count()
    	int contBaseline = Contact.count()

    	when: "merging tags"
    	assert service.mergeTags(c1, cTags, [contact1, contact2]).status == ResultStatus.NO_CONTENT
    	Contact.withSession { it.flush() }

    	then: "tags do not have to-be-merged contacts and have target contact"
    	ContactTag.count() == tagBaseline
    	Record.count() == recBaseline
    	Contact.count() == contBaseline
    	cTags.each { ContactTag cTag ->
    		cTag.refresh()
    		assert !(contact1 in cTag.members)
    		assert !(contact2 in cTag.members)
    		assert c1 in cTag.members
    	}

    	when: "delete afterwards to ensure that all dependencies are eliminated"
    	delete(contact1)
    	delete(contact2)
    	Contact.withSession { it.flush() }

    	then:
  		ContactNumber.count() == cnBaseline - 4 // 4 contact numbers deleted again
  		Record.count() == recBaseline - 2
    	Contact.count() == contBaseline - 2
    }

    void "test merging shared contacts"() {
    	given: "shared contacts for the contacts to be merged"
    	Contact contact1 = p1.createContact([:], [TestHelpers.randPhoneNumber(), TestHelpers.randPhoneNumber()]).payload
    	Contact contact2 = p1.createContact([:], [TestHelpers.randPhoneNumber(), TestHelpers.randPhoneNumber()]).payload
    	[contact1, contact2]*.save(flush:true, failOnError:true)

    	Collection<SharedContact> sharedContacts = addSharedContacts(contact1, contact2)

    	int cnBaseline = ContactNumber.count()
    	int sharedBaseline = SharedContact.count()
    	int recBaseline = Record.count()
    	int contBaseline = Contact.count()

    	when: "merging shared contacts"
    	assert service.mergeSharedContacts(c1, [contact1, contact2]).status == ResultStatus.NO_CONTENT
    	Contact.withSession { it.flush() }

    	then: "shared contacts are updated to point to the target, no new created"
    	SharedContact.count() == sharedBaseline
    	Record.count() == recBaseline
    	Contact.count() == contBaseline
    	sharedContacts.each {
    		it.refresh()
    	 	assert it.contact.id == c1.id
    	}

    	when: "delete afterwards to ensure that all dependencies are eliminated"
    	delete(contact1)
    	delete(contact2)
    	Contact.withSession { it.flush() }

    	then:
  		ContactNumber.count() == cnBaseline - 4 // 4 contact numbers deleted again
  		Record.count() == recBaseline - 2
    	Contact.count() == contBaseline - 2
    }

    void "test merging objects related to record"() {
    	given: "record items and future messages for the contacts to be merged"
    	Contact contact1 = p1.createContact([:], [TestHelpers.randPhoneNumber(), TestHelpers.randPhoneNumber()]).payload
    	Contact contact2 = p1.createContact([:], [TestHelpers.randPhoneNumber(), TestHelpers.randPhoneNumber()]).payload
    	[contact1, contact2]*.save(flush:true, failOnError:true)

    	Collection<RecordItem> rItems = addRecordItems(contact1, contact2)
		Collection<FutureMessage> fMsgs = addFutureMessage(contact1, contact2)

		int cnBaseline = ContactNumber.count()
		int itemBaseline = RecordItem.count()
		int futureBaseline = FutureMessage.count()
		int recBaseline = Record.count()
    	int contBaseline = Contact.count()

    	when: "merging objects related to the record"
    	assert service.mergeRecords(c1.record, [contact1, contact2]*.record).status == ResultStatus.NO_CONTENT
    	Contact.withSession { it.flush() }

    	then: "no new objects created, all are updated to the point to the target"
    	RecordItem.count() == itemBaseline
    	FutureMessage.count() == futureBaseline
    	Record.count() == recBaseline
    	Contact.count() == contBaseline
    	rItems.each {
    		it.refresh()
    		assert it.record.id == c1.record.id
    	}
    	fMsgs.each {
    		it.refresh()
    		assert it.record.id == c1.record.id
    	}
    	[contact1, contact2].each {
    		assert it.record.countItems() == 0
    		assert it.record.countFutureMessages() == 0
    	}

    	when: "delete afterwards to ensure that all dependencies are eliminated"
    	delete(contact1)
    	delete(contact2)
    	Contact.withSession { it.flush() }

    	then:
  		ContactNumber.count() == cnBaseline - 4 // 4 contact numbers deleted again
  		Record.count() == recBaseline - 2
    	Contact.count() == contBaseline - 2
    }

    void "test merging overall with deletion"() {
    	given: "contacts to be merged with numbers, tags, shared contacts, record items, future messages"
    	Contact contact1 = p1.createContact([:], [TestHelpers.randPhoneNumber(), TestHelpers.randPhoneNumber()]).payload
    	Contact contact2 = p1.createContact([:], [TestHelpers.randPhoneNumber(), TestHelpers.randPhoneNumber()]).payload
    	[contact1, contact2]*.save(flush:true, failOnError:true)

    	Collection<ContactTag> cTags = addTags(contact1, contact2)
    	Collection<SharedContact> sharedContacts = addSharedContacts(contact1, contact2)
    	Collection<RecordItem> rItems = addRecordItems(contact1, contact2)
		Collection<FutureMessage> fMsgs = addFutureMessage(contact1, contact2)

    	// configure statuses
    	c1.status = ContactStatus.UNREAD
    	contact1.status = ContactStatus.ARCHIVED
    	contact2.status = ContactStatus.BLOCKED
    	[c1, contact1, contact2]*.save(flush:true, failOnError:true)

		int itemBaseline = RecordItem.count()
		int futureBaseline = FutureMessage.count()
    	int sharedBaseline = SharedContact.count()
    	int tagBaseline = ContactTag.count()
    	int cnBaseline = ContactNumber.count()
    	int recBaseline = Record.count()
    	int contBaseline = Contact.count()

    	when: "try to merge these contacts into the target"
    	Collection<ContactNumber> numsToMerge = contact1.numbers + contact2.numbers
    	int previousNumNums = c1.numbers.size()

    	Contact mergedTargetContact = service.merge(c1, [contact1, contact2]).payload
    	Contact.withSession { it.flush() }

    	then: "new numbers are created, all other objects are updated BUT merged contacts NOT deleted"
		RecordItem.count() == itemBaseline
		FutureMessage.count() == futureBaseline
		SharedContact.count() == sharedBaseline
		ContactTag.count() == tagBaseline
		ContactNumber.count() == cnBaseline + 4 // 4 numbers created
		Record.count() == recBaseline
		Contact.count() == contBaseline

		mergedTargetContact.status == ContactStatus.ACTIVE
		c1.numbers.size() == previousNumNums + numsToMerge.size()
		cTags.each { ContactTag cTag ->
			cTag.refresh()
    		assert !(contact1 in cTag.members)
    		assert !(contact2 in cTag.members)
    		assert c1 in cTag.members
    	}
    	sharedContacts.each {
    		it.refresh()
    	 	assert it.contact.id == c1.id
    	}
		rItems.each {
    		it.refresh()
    		assert it.record.id == c1.record.id
    	}
    	fMsgs.each {
    		it.refresh()
    		assert it.record.id == c1.record.id
    	}
        // merged contacts are NOT deleted. The merge method use to also handle delete
        // but this would re-save removed hasMany associations in the resulting save cascade
        // Therefore, the merge method was re-scope to only handle merging, leaving deleting the resulting
        // merged-in contacts to the caller of the merge method
    	[contact1, contact2].each {
    		assert it.isDeleted == false // merged-in contacts are NOT deleted
    		assert it.record.countItems() == 0
    		assert it.record.countFutureMessages() == 0
    	}

    	when: "try merging without any contacts to be merged"
    	addToMessageSource("duplicateService.merge.missingMergeContacts")
    	Result<Contact> res = service.merge(c1, [])

    	then: "bad request"
    	res.success == false
    	res.status == ResultStatus.BAD_REQUEST
    	res.errorMessages[0] == "duplicateService.merge.missingMergeContacts"

    	when: "delete afterwards to ensure that all dependencies are eliminated"
    	delete(contact1)
    	delete(contact2)
    	Contact.withSession { it.flush() }

    	then:
		ContactNumber.count() == cnBaseline // 4 contact numbers deleted again after adding 4
		Record.count() == recBaseline - 2
    	Contact.count() == contBaseline - 2
    }
}
