package org.textup

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import grails.validation.ValidationErrors
import org.joda.time.DateTime
import org.springframework.context.MessageSource
import org.textup.rest.TwimlBuilder
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*
import spock.lang.Ignore
import spock.lang.Shared

@Domain([Contact, Phone, ContactTag, ContactNumber, Record, RecordItem, RecordText,
	RecordCall, RecordItemReceipt, SharedContact, Staff, Team, Organization, Schedule,
	Location, WeeklySchedule, PhoneOwnership, FeaturedAnnouncement, IncomingSession,
	AnnouncementReceipt, Role, StaffRole, NotificationPolicy,
    MediaInfo, MediaElement, MediaElementVersion])
@TestMixin(HibernateTestMixin)
class PhoneSpec extends CustomSpec {

	static doWithSpring = {
		resultFactory(ResultFactory)
	}

    def setup() {
    	setupData()
        Helpers.metaClass.'static'.getMessageSource = { -> TestHelpers.mockMessageSource() }
    }

    def cleanup() {
    	cleanupData()
    }

    protected TwimlBuilder getTwimlBuilder() {
        [build:{ code, params=[:] ->
            new Result(status:ResultStatus.OK, payload:code)
        }, noResponse: { ->
            new Result(status:ResultStatus.OK, payload:"noResponse")
        }] as TwimlBuilder
    }

    void "test constraints"() {
    	when: "we have a phone without a number"
    	Phone p1 = new Phone()

    	then: // no owner
    	p1.validate() == false
    	p1.errors.errorCount == 1
        p1.isActive == false

        when: "we have a null voice type"
        p1.voice = null

        then:
        p1.validate() == false
        p1.errors.errorCount == 2
        p1.errors.getFieldErrorCount("voice") == 1

    	when: "we have a phone with a unique number"
        String num = "5223334444"
        p1.voice = VoiceType.FEMALE
    	p1.numberAsString = num
        p1.updateOwner(s1)

    	then:
    	p1.validate() == true
        p1.isActive == true

    	when: "we try to add a phone with a duplicate number"
    	p1.save(flush:true, failOnError:true)
    	p1 = new Phone(numberAsString:num)
        p1.updateOwner(s1)

    	then:
    	p1.validate() == false
    	p1.errors.errorCount == 1

    	when: "we add a phone with a unique number"
    	p1.numberAsString = "5223334445"

    	then:
    	p1.validate() == true
    }

    void "test getting phones for records"() {
        when: "we pass in shared contact"
        HashSet<Phone> phones = Phone.getPhonesForRecords([sc1.contact.record])

        then: "we should get back both shared with and shared by phones"
        phones.size() == 2
        [p1, p2].every { it in phones }

        when: "have records belonging to our tags, records, and shared FOR ONE PHONE"
        List<Record> myContactRecs = Contact.findAllByPhone(p1)*.record,
            myTagRecs = p1.tags*.record,
            sWithMeRecs = p1.sharedWithMe.collect { it.contact.record },
            allRecs = myContactRecs + myTagRecs + sWithMeRecs
        assert allRecs.isEmpty() == false
        phones = Phone.getPhonesForRecords(allRecs)

        then:
        phones.size() == 2
        [p1, p2].every { it in phones }

        when: "we pass in records belonging to various phones"
        List<Record> otherCRecs = Contact.findAllByPhone(p2)*.record +
                Contact.findAllByPhone(tPh1)*.record,
            otherTRecs = p2.tags*.record + tPh1.tags*.record
        allRecs += otherCRecs += otherTRecs
        phones = Phone.getPhonesForRecords(allRecs)

        then:
        phones.size() == 3
        [p1, p2, tPh1].every { it in phones }

        when: "all contacts and tags are marked as deleted"
        (Contact.findAllByPhone(p1) + Contact.findAllByPhone(p2) + Contact.findAllByPhone(tPh1)).each { Contact contact ->
            contact.isDeleted = true
            contact.save(flush:true, failOnError:true)
        }
        (p1.tags + p2.tags + tPh1.tags).each { ContactTag cTag ->
            cTag.isDeleted = true
            cTag.save(flush:true, failOnError:true)
        }
        phones = Phone.getPhonesForRecords(allRecs)

        then: "no phones are found"
        phones.isEmpty() == true
    }

    void "test owner and availability"() {
        given: "a phone belonging to a team with no policies"
        Phone p1 = new Phone(numberAsString: TestHelpers.randPhoneNumber())
        p1.updateOwner(t1)
        p1.save(flush:true, failOnError:true)
        assert p1.owner.policies == null

        when: "all staff on team are available"
        t1.members.each {
            it.status = StaffStatus.STAFF
            it.manualSchedule = true
            it.isAvailable = true
        }
        t1.save(flush:true, failOnError:true)

        then: "all available because no policies in place"
        p1.owner.getCanNotifyAndAvailable([88L]).size() == t1.activeMembers.size()
        p1.owner.getCanNotifyAndAvailable([88L]).every { it in t1.activeMembers }

        when: "some staff are unavailable"
        Staff unavailableStaff = t1.activeMembers[0]
        unavailableStaff.isAvailable = false
        unavailableStaff.save(flush:true, failOnError:true)

        then:
        p1.owner.getCanNotifyAndAvailable([88L]).size() == t1.activeMembers.size() - 1
        p1.owner.getCanNotifyAndAvailable([88L]).every { it in t1.activeMembers && it != unavailableStaff }
    }

    void "test transferring phone"() {
        given: "baselines, staff without phone"
        int oBaseline = PhoneOwnership.count()
        int pBaseline = Phone.count()

        Staff noPhoneStaff = new Staff(username:"888-8sta$iterNum",
            password:"password", name:"Staff", email:"staff@textup.org",
            org:org, personalPhoneAsString:"1112223333", status: StaffStatus.STAFF,
            lockCode:Constants.DEFAULT_LOCK_CODE)
        noPhoneStaff.save(flush:true, failOnError:true)

        when: "transferring to a nonexistent entity"
        Result<PhoneOwnership> res = s1.phone.transferTo(-88888,
            PhoneOwnershipType.INDIVIDUAL)

        then:
        res.success == false
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages.size() == 1

        when: "transferring to an entity that doesn't already have a phone"
        Phone myPhone = s1.phone,
            targetPhone = null
        def initialOwner = s1,
            targetOwner = noPhoneStaff
        res = myPhone.transferTo(targetOwner.id, PhoneOwnershipType.INDIVIDUAL)
        myPhone.save(flush:true, failOnError:true)

        then: "phone is transferred"
        res.success == true
        res.status == ResultStatus.OK
        res.payload instanceof PhoneOwnership
        res.payload.ownerId == targetOwner.id
        res.payload.type == PhoneOwnershipType.INDIVIDUAL
        myPhone.owner.ownerId == targetOwner.id
        myPhone.owner.type == PhoneOwnershipType.INDIVIDUAL
        PhoneOwnership.count() == oBaseline
        Phone.count() == pBaseline

        when: "transferring to an entity that has an INactive phone"
        initialOwner = targetOwner
        targetPhone = s2.phone
        targetOwner = s2

        targetPhone.deactivate()
        targetPhone.save(flush:true, failOnError:true)
        res = myPhone.transferTo(targetOwner.id, PhoneOwnershipType.INDIVIDUAL)

        then: "phones are swapped"
        res.success == true
        res.status == ResultStatus.OK
        res.payload instanceof PhoneOwnership
        res.payload.ownerId == targetOwner.id
        res.payload.type == PhoneOwnershipType.INDIVIDUAL
        myPhone.owner.ownerId == targetOwner.id
        myPhone.owner.type == PhoneOwnershipType.INDIVIDUAL
        targetPhone.owner.ownerId == initialOwner.id
        targetPhone.owner.type == PhoneOwnershipType.INDIVIDUAL
        PhoneOwnership.count() == oBaseline
        Phone.count() == pBaseline

        when: "transferring to an entity that has an active phone"
        initialOwner = targetOwner
        targetPhone = t1.phone
        targetOwner = t1

        assert targetPhone.isActive
        res = myPhone.transferTo(targetOwner.id, PhoneOwnershipType.GROUP)

        then: "phones are swapped"
        res.success == true
        res.status == ResultStatus.OK
        res.payload instanceof PhoneOwnership
        res.payload.ownerId == targetOwner.id
        res.payload.type == PhoneOwnershipType.GROUP
        myPhone.owner.ownerId == targetOwner.id
        myPhone.owner.type == PhoneOwnershipType.GROUP
        targetPhone.owner.ownerId == initialOwner.id
        targetPhone.owner.type == PhoneOwnershipType.INDIVIDUAL
        PhoneOwnership.count() == oBaseline
        Phone.count() == pBaseline
    }

    void "test sharing contact operations"() {
    	when: "we start sharing one of our contacts with someone on a different team"
    	Result res = p1.share(c1, p3, SharePermission.DELEGATE)

    	then:
    	res.success == false
        res.status == ResultStatus.FORBIDDEN
        res.errorMessages.size() == 1
        res.errorMessages[0] == "phone.share.cannotShare"

    	when: "we start sharing contact with someone on the same team"
        c1.status = ContactStatus.ARCHIVED
        assert c1.save(flush:true)
    	res = p1.share(c1, p2, SharePermission.DELEGATE)

    	then: "new shared contact created with contact's status"
    	res.success == true
        res.status == ResultStatus.OK
    	res.payload.instanceOf(SharedContact)
        res.payload.status == c1.status

    	when: "we start sharing contact that we've already shared with the same person"
        c1.status = ContactStatus.UNREAD
        assert c1.save(flush:true)
    	int sBaseline = SharedContact.count()
		res = p1.share(c1, p2, SharePermission.DELEGATE)
		assert res.success
		SharedContact shared0 = res.payload
		res.payload.save(flush:true, failOnError:true)

    	then: "we don't create a duplicate SharedContact and status is updated"
        res.status == ResultStatus.OK
    	shared0.instanceOf(SharedContact)
    	SharedContact.count() == sBaseline
        shared0.status == c1.status

    	when: "we share three more and list all shared so far"
		SharedContact shared1 = p1.share(c1_1, p2, SharePermission.DELEGATE).payload,
			shared2 = p1.share(c1_2, p2, SharePermission.DELEGATE).payload,
			shared3 = p2.share(c2, p1, SharePermission.DELEGATE).payload
		[shared1, shared2, shared3]*.save(flush:true, failOnError:true)

    	then:
    	p1.sharedByMe.every { it in [shared2, shared1, shared0]*.contact }
    	p1.sharedWithMe == [shared3]

    	when: "we stop sharing someone else's contact"
    	res = p1.stopShare(tC1)

    	then:
    	res.success == false
        res.status == ResultStatus.BAD_REQUEST
        res.errorMessages.size() == 1
        res.errorMessages[0] == "phone.contactNotMine"

    	when: "we stop sharing contact that is not shared"
        Contact c1_3 = p1.createContact([:], ["12223334447"]).payload
        c1_3.save(flush:true, failOnError:true)
    	res = p1.stopShare(c1_3)

    	then: "silently ignore that contact is not shared"
    	res.success == true
        res.status == ResultStatus.NO_CONTENT

    	when: "we stop sharing by phones"
    	assert p1.stopShare(p2).success
    	p1.merge(flush:true)

    	then:
    	p1.sharedByMe.isEmpty() == true
    	p1.sharedWithMe.size() == 1
        p1.sharedWithMe[0].id == shared3.id

    	when: "stop sharing by contacts"
    	SharedContact shared4 = p2.share(c2, p3, SharePermission.DELEGATE).payload
        shared4.save(flush:true, failOnError:true)
        //same underlying contact
    	assert p2.sharedByMe.every { it in [shared4, shared3]*.contact }
    	assert p1.sharedWithMe[0].id == shared3.id && p3.sharedWithMe[0].id == shared4.id

    	p2.stopShare(c2)
    	p2.merge(flush:true)

    	then:
    	p2.sharedByMe.isEmpty() == true
    	p1.sharedWithMe.isEmpty() == true
    	p3.sharedWithMe.isEmpty() == true
	}

	void "test getting contacts also gets shared contacts mixed in"() {
        given: "the appropriate timestamps"
        [c1, c1_1, c1_2, c2, c2_1].eachWithIndex { Contact c, int i ->
            c.status = ContactStatus.ACTIVE
            c.record.lastRecordActivity = DateTime.now().minusMinutes(i)
            c.save(flush:true, failOnError:true)
        }
        [sc1, sc2].eachWithIndex { SharedContact sc, int i ->
            sc.contact.record.lastRecordActivity = DateTime.now().plusMinutes(i)
            sc.save(flush:true, failOnError:true)
        }

		when: "we list all our contacts"
		List<Contactable> p1Contactables = p1.contacts,
			p2Contactables = p2.contacts

		then:
		p1Contactables == [sc2, c1, c1_1, c1_2]
		p2Contactables == [c2, sc1, c2_1] //adding time to sc2 modifies c2

		when: "we mark a few shared contacts and contacts as unread"
		c1_1.status = ContactStatus.UNREAD
		sc1.contact.status = ContactStatus.UNREAD
		p1.save(flush:true, failOnError:true)

		p1Contactables = p1.contacts
		p2Contactables = p2.contacts

		then:
        p1Contactables == [c1, c1_1, sc2, c1_2]
        p2Contactables == [sc1, c2, c2_1]

		when: "we stop sharing some shared contacts"
		assert p1.stopShare(c1).success
		p1.save(flush:true, failOnError:true)

		p1Contactables = p1.contacts
		p2Contactables = p2.contacts

		then:
        p1Contactables == [c1, c1_1, sc2, c1_2]
        p2Contactables == [c2, c2_1]
	}

    void "test listing and searching contacts shows shared and excludes expired"() {
        given: "a contact shared by p1, shared with p2 is active \
            with some numbers and also name"
        assert sc1.isActive
        assert sc1.sharedBy == p1
        assert sc1.sharedWith == p2
        assert sc1.numbers.isEmpty() == false
        assert sc1.name != null

        when: "we have a shared contact"
        String searchNum = sc1.numbers[0].number
        String searchName = sc1.name

        List<Contactable> listContactables = p2.getContacts(statuses:
            [ContactStatus.ACTIVE, ContactStatus.UNREAD])
        List<Contactable> searchContactablesByNumber = p2.getContacts(searchNum)
        List<Contactable> searchContactablesByName = p2.getContacts(searchName)

        then: "shared shows up"
        listContactables.contains(sc1)
        searchContactablesByNumber.contains(sc1)
        searchContactablesByName.contains(sc1)

        when: "we expire"
        sc1.stopSharing()
        sc1.save(flush:true, failOnError:true)
        listContactables = p2.getContacts(statuses:
            [ContactStatus.ACTIVE, ContactStatus.UNREAD])
        searchContactablesByNumber = p2.getContacts(searchNum)
        searchContactablesByName = p2.getContacts(searchName)

        then: "shared does not show up anymore"
        !listContactables.contains(sc1)
        !searchContactablesByNumber.contains(sc1)
        !searchContactablesByName.contains(sc1)
    }

    void "test creating contacts"() {
        given:
        p1.language = VoiceLanguage.CHINESE
        p1.save(flush:true, failOnError:true)

        when: "we create a blank contact"
        Result<Contact> res = p1.createContact(language:"blah invalid")

        then:
        res.success == true
        res.status == ResultStatus.CREATED
        res.payload.instanceOf(Contact)
        // use phone default because no valid language provided
        res.payload.record.language == p1.language

        when: "contact with duplicate numbers"
        int cNumBaseline = ContactNumber.count(),
            contactBaseline = Contact.count()
        String number = "1112223333",
            name = "Kiki"
        res = p1.createContact([name:name], [number, number, number])
        p1.save(flush:true, failOnError:true)

        then:
        res.success == true
        res.status == ResultStatus.CREATED
        res.payload.instanceOf(Contact)
        res.payload.name == name
        res.payload.numbers.size() == 1
        res.payload.numbers[0].number == number
        res.payload.numbers[0].preference == 0
        Contact.count() == contactBaseline + 1
        ContactNumber.count() == cNumBaseline + 1

        when: "create contact with multiple numbers"
        cNumBaseline = ContactNumber.count()
        contactBaseline = Contact.count()
        String num1 = "1112223333",
            num2 = "1232343456",
            name1 = UUID.randomUUID().toString()[0..10],
            note1 = UUID.randomUUID().toString()[0..10]
        res = p1.createContact([
                name:name1,
                language:VoiceLanguage.PORTUGUESE.toString(),
                note:note1,
                status:ContactStatus.ARCHIVED.toString()
            ],
            [num2, num2, num1, num2, num1])
        p1.save(flush:true, failOnError:true)

        then:
        res.success == true
        res.status == ResultStatus.CREATED
        res.payload.instanceOf(Contact)
        res.payload.name == name1
        res.payload.note == note1
        res.payload.status == ContactStatus.ARCHIVED
        res.payload.numbers.size() == 2
        res.payload.numbers[0].number == num2
        res.payload.numbers[0].preference == 0
        res.payload.numbers[1].number == num1
        res.payload.numbers[1].preference == 1
        Contact.count() == contactBaseline + 1
        ContactNumber.count() == cNumBaseline + 2
        // valid language provided so do not use phone language as default
        res.payload.record.language != p1.language
        res.payload.record.language == VoiceLanguage.PORTUGUESE
    }

    void "test creating tags"() {
    	given:
        int tagBaseline = ContactTag.count()
        int recBaseline = Record.count()
        int origNumTags = p1.tags.size()

        p1.language = VoiceLanguage.CHINESE
        p1.save(flush:true, failOnError:true)

    	when: "we add a tag with unique name"
        Result<ContactTag> tagRes1 = p1.createTag(name:"tag1", language: "blah invalid")
        assert tagRes1.success
    	p1.save(flush:true, failOnError:true)

    	then:
    	p1.tags.size() == origNumTags + 1
        ContactTag.count() == tagBaseline + 1
        Record.count() == recBaseline + 1
        // because no valid language provided, defaults to phone's language
        tagRes1.payload.language == p1.language

    	when: "we add a tag with a duplicate name"
    	Result<ContactTag> tagRes2 = p1.createTag(name:"tag1")

    	then:
    	tagRes2.success == false
    	tagRes2.status == ResultStatus.UNPROCESSABLE_ENTITY
    	tagRes2.errorMessages.size() == 1
        ContactTag.count() == tagBaseline + 1
        Record.count() == recBaseline + 1

    	when: "we change to a unique name"
    	tagRes2 = p1.createTag(name:"tag2", language:VoiceLanguage.PORTUGUESE.toString())
        assert tagRes2.payload.instanceOf(ContactTag)
        tagRes2.payload.save(flush:true, failOnError:true)

    	then:
    	tagRes2.success == true
        tagRes2.status == ResultStatus.CREATED
        p1.tags.size() == origNumTags + 2
        ContactTag.count() == tagBaseline + 2
        Record.count() == recBaseline + 2
        // valid language provided so we don't use phone language as default
        tagRes2.payload.language != p1.language
        tagRes2.payload.language == VoiceLanguage.PORTUGUESE
    }

    // Communications functionality
    // ----------------------------

    void "test outgoing communication when phone is inactive"() {
        given: "an inactive phone"
        p1.deactivate()
        p1.save(flush:true, failOnError:true)
        assert !p1.isActive

        when: "send text"
        OutgoingMessage text = TestHelpers.buildOutgoingMessage("hi")
        text.contacts.recipients << c1
        ResultGroup<RecordItem> resGroup = p1.sendMessage(text, null, s1)

        then:
        resGroup.anySuccesses == false
        resGroup.failures.size() == 1
        resGroup.failureStatus == ResultStatus.NOT_FOUND
        resGroup.failures[0].success == false
        resGroup.failures[0].status == ResultStatus.NOT_FOUND
        resGroup.failures[0].errorMessages[0] == "phone.isInactive"

        when: "start bridge call"
        Result<RecordCall> res1 = p1.startBridgeCall(c1, s1)

        then:
        res1.success == false
        res1.status == ResultStatus.NOT_FOUND
        res1.errorMessages[0] == "phone.isInactive"

        when: "send announcement"
        Result<FeaturedAnnouncement> res2 = p1.sendAnnouncement("hi", DateTime.now().plusDays(1), s1)

        then:
        res2.success == false
        res2.status == ResultStatus.NOT_FOUND
        res2.errorMessages[0] == "phone.isInactive"
    }

    void "test sending text"() {
        given: "a phone"
        p1.phoneService = [sendMessage:{ Phone phone, OutgoingMessage text, MediaInfo mInfo, Staff staff ->
            new ResultGroup<RecordItem>([new Result(status: ResultStatus.OK)])
        }] as PhoneService

        when: "we have an invalid outgoing text"
        OutgoingMessage text = new OutgoingMessage()
        assert text.validate() == false
        ResultGroup<RecordText> resGroup = p1.sendMessage(text, null, s1)

        then: "we assume text is valid when passed in -- do not revalidate"
        resGroup.anySuccesses == true

        when: "we pass in a staff that is not an owner"
        text = TestHelpers.buildOutgoingMessage("hi")
        text.contacts.recipients = [c1, c1_1]
        assert text.validate() == true
        resGroup = p1.sendMessage(text, null, otherS2)

        then:
        resGroup.anySuccesses == false
        resGroup.failureStatus == ResultStatus.FORBIDDEN
        resGroup.failures.size() == 1
        resGroup.failures[0].success == false
        resGroup.failures[0].payload == null
        resGroup.failures[0].status == ResultStatus.FORBIDDEN
        resGroup.failures[0].errorMessages[0] == "phone.notOwner"

        when: "we pass in a valid outgoing text and staff that is owner"
        resGroup = p1.sendMessage(text, null, s1)

        then:
        resGroup.anySuccesses == true
        resGroup.anyFailures == false
    }

    void "test starting and completing bridge call"() {
        given: "a phone"
        p1.phoneService = [startBridgeCall:{ Phone phone, Contactable c1, Staff staff ->
            new Result(status:ResultStatus.CREATED, payload:null)
        }] as PhoneService
        p1.twimlBuilder = getTwimlBuilder()
        s1.personalPhoneAsString = "1112223333"
        s1.save(flush:true, failOnError:true)

        when: "try to call that does not belong to this phone"
        Result<RecordCall> res1 = p1.startBridgeCall(tC1, s1)

        then: "by the time we get here, Contactable should have already been validated"
        res1.success == true

        when: "try to call shared contact that is not shared with this phone"
        res1 = p1.startBridgeCall(sc1, s1)

        then: "by the time we get here, Contactable should have already been validated"
        res1.success == true

        when: "try to call shared contact that we don't have modify permissions for"
        sc2.permission = SharePermission.VIEW
        sc2.save(flush:true, failOnError:true)
        res1 = p1.startBridgeCall(sc2, s1)

        then: "by the time we get here, Contactable should have already been validated"
        res1.success == true

        when: "pass in a staff that is not an owner of this phone"
        sc2.permission = SharePermission.DELEGATE
        sc2.save(flush:true, failOnError:true)
        res1 = p1.startBridgeCall(sc2, otherS1)

        then:
        res1.success == false
        res1.payload == null
        res1.status == ResultStatus.FORBIDDEN
        res1.errorMessages[0] == "phone.notOwner"

        when: "pass in valid staff, but staff has no personal phone number"
        s1.personalPhoneAsString = null
        s1.save(flush:true, failOnError:true)
        res1 = p1.startBridgeCall(c1, s1)

        then:
        res1.success == false
        res1.payload == null
        res1.status == ResultStatus.UNPROCESSABLE_ENTITY
        res1.errorMessages[0] == "phone.startBridgeCall.noPersonalNumber"

        when: "we pass in all valid"
        s1.personalPhoneAsString = "1112223333"
        s1.save(flush:true, failOnError:true)
        res1 = p1.startBridgeCall(c1, s1)

        then:
        res1.success == true
        res1.status == ResultStatus.CREATED
        // we don't check the type of the payload because we are making phoneService right now
        // and therefore our mocked service is being called, not the actual service
        // res1.payload instanceof RecordCall

        when: "complete call bridge"
        Result<Closure> res2 = p1.finishBridgeCall(c1)

        then:
        res2.success == true
        res2.status == ResultStatus.OK
        res2.payload == CallResponse.FINISH_BRIDGE
    }

    void "test announcement success"() {
        given: "phone and incoming sessions, some coinciding with contacts"
        p1.twimlBuilder = getTwimlBuilder()
        // subscriber
        String subNum = "1223334445"
        IncomingSession sess = new IncomingSession(phone:p1, numberAsString:subNum,
            isSubscribedToText:true, isSubscribedToCall:true)
        sess.save(flush:true, failOnError:true)
        // mock services
        p1.phoneService = [sendTextAnnouncement:{ Phone phone, String message,
            String identifier, List<IncomingSession> sessions, Staff staff ->
            Map<String, Result<TempRecordReceipt>> resMap = [:]
            resMap[subNum] = new Result(status:ResultStatus.OK)
            resMap
        }, startCallAnnouncement:{ Phone phone, String message,
            String identifier, List<IncomingSession> sessions, Staff staff ->
            Map<String, Result<TempRecordReceipt>> resMap = [:]
            resMap[subNum] = new Result(status:ResultStatus.OK)
            resMap
        }] as PhoneService
        // baselines
        int featBaseline = FeaturedAnnouncement.count(),
            aReceiptBaseline = AnnouncementReceipt.count()

        when: "valid and some subscribers successfully reached"
        Result<FeaturedAnnouncement> res = p1.sendAnnouncement("hello",
            DateTime.now().plusDays(1), s1)
        assert res.success
        p1.save(flush:true, failOnError:true)

        then:
        FeaturedAnnouncement.count() == featBaseline + 1
        AnnouncementReceipt.count() == aReceiptBaseline + 2
        res.status == ResultStatus.OK
        res.payload.instanceOf(FeaturedAnnouncement)

        when: "read announcements"
        IncomingSession session = new IncomingSession(phone:p1, numberAsString:"5557778888")
        Result<Closure> closureRes = p1.completeCallAnnouncement(null, null,
            null, session)

        then:
        closureRes.success == true
        closureRes.status == ResultStatus.OK
        closureRes.payload == CallResponse.ANNOUNCEMENT_AND_DIGITS

        when: "confirm unsubscribed"
        closureRes = p1.completeCallAnnouncement(Constants.CALL_ANNOUNCEMENT_UNSUBSCRIBE,
            "message", "identifier", session)

        then:
        session.isSubscribedToCall == false
        closureRes.success == true
        closureRes.status == ResultStatus.OK
        closureRes.payload == CallResponse.UNSUBSCRIBED
    }

    void "test announcement error conditions"() {
        given: "phone and incoming sessions, some coinciding with contacts"
        p1.twimlBuilder = getTwimlBuilder()

        when: "expires in the past"
        Result<FeaturedAnnouncement> res = p1.sendAnnouncement("hello",
            DateTime.now().minusDays(1), s1)

        then:
        res.success == false
        res.payload == null
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages[0] == "phone.sendAnnouncement.expiresInPast"

        when: "pass in staff that is not an owner"
        res = p1.sendAnnouncement("hello", DateTime.now().plusDays(1), otherS1)

        then:
        res.success == false
        res.payload == null
        res.status == ResultStatus.FORBIDDEN
        res.errorMessages[0] == "phone.notOwner"
    }

    void "test announcement none reached"() {
        given: "phone and incoming sessions, some coinciding with contacts"
        p1.twimlBuilder = getTwimlBuilder()
        p1.phoneService = [sendTextAnnouncement:{ Phone phone, String message,
            String identifier, List<IncomingSession> sessions, Staff staff ->
            [:]
        }, startCallAnnouncement:{ Phone phone, String message,
            String identifier, List<IncomingSession> sessions, Staff staff ->
            [:]
        }] as PhoneService

        when: "none reached with no subscribers"
        Result<FeaturedAnnouncement> res = p1.sendAnnouncement("hello",
            DateTime.now().plusDays(1), s1)

        then:
        res.success == true
        res.status == ResultStatus.OK
        res.payload instanceof FeaturedAnnouncement

        when: "none reached with some subscribers"
        // add a subscriber
        String subNum = "1223334445"
        IncomingSession sess = new IncomingSession(phone:p1, numberAsString:subNum,
            isSubscribedToText:true, isSubscribedToCall:true)
        sess.save(flush:true, failOnError:true)
        // another announcement
        res = p1.sendAnnouncement("hello", DateTime.now().plusDays(1), s1)

        then:
        res.success == false
        res.payload == null
        res.status == ResultStatus.INTERNAL_SERVER_ERROR
    }

    void "test receiving text"() {
        given: "a phone"
        int _numTimesHandleAnnouncementText = 0
        p1.phoneService = [
            relayText: { Phone phone, IncomingText text, IncomingSession session, MediaInfo mInfo ->
                new Result(status:ResultStatus.OK, payload:null)
            },
            handleAnnouncementText: { Phone p1, IncomingText t1, IncomingSession s1, MediaInfo m1 ->
                _numTimesHandleAnnouncementText++
                new Result(status:ResultStatus.OK, payload:null)
            }
        ] as PhoneService
        p1.twimlBuilder = getTwimlBuilder()
        IncomingSession session = new IncomingSession(phone:p1, numberAsString:"5557778888"),
            otherSess = new IncomingSession(phone:p2, numberAsString:"5557778888")
        session.save(flush:true, failOnError:true)

        when: "invalid incoming text"
        IncomingText text = new IncomingText()
        assert text.validate() == false
        Result res = p1.receiveText(text, session)

        then: "assume that the incoming text has already been validated"
        res.success == true

        when: "session does not belong to this phone"
        text = new IncomingText(apiId:"apiId", message:"hello")
        assert text.validate()
        res = p1.receiveText(text, otherSess)

        then:
        res.success == false
        res.status == ResultStatus.FORBIDDEN
        res.errorMessages[0] == 'phone.receive.notMine'

        when: "we don't have any announcements"
        res = p1.receiveText(text, session)

        then: "relay text"
        _numTimesHandleAnnouncementText == 0
        res.success == true
        res.status == ResultStatus.OK

        when: "we have announcements and message isn't a valid keyword"
        FeaturedAnnouncement announce = new FeaturedAnnouncement(owner:p1,
            message:"Hello!", expiresAt:DateTime.now().plusDays(2))
        announce.save(flush:true, failOnError:true)
        text.message = "invalid keyword"
        assert text.validate()
        res = p1.receiveText(text, session)

        then: "delegate to phoneService to handle possible announcements response"
        _numTimesHandleAnnouncementText == 1
        res.success == true
    }

    void "test receiving call"() {
        given: "a phone and incoming sessions"
        p1.phoneService = [relayCall:{ Phone phone, String apiId,
            IncomingSession session ->
            new Result(status:ResultStatus.OK, payload:"relayCall")
        }, handleAnnouncementCall: { Phone phone, String apiId, String digits,
            IncomingSession session ->
            new Result(status:ResultStatus.OK, payload:"handleAnnouncementCall")
        }, handleSelfCall:{ Phone phone, String apiId, String digits, Staff staff ->
            new Result(status:ResultStatus.OK, payload:"handleSelfCall")
        }] as PhoneService
        p1.twimlBuilder = getTwimlBuilder()
        IncomingSession session = new IncomingSession(phone:p1, numberAsString:"5557778888"),
            personalSess = new IncomingSession(phone:p1, numberAsString:s1.personalPhoneAsString),
            otherSess = new IncomingSession(phone:p2, numberAsString:"5557778888")
        [session, personalSess, otherSess]*.save(flush:true, failOnError:true)

        when: "session does not belong to this phone"
        Result<Closure> res = p1.receiveCall("apiId", "digits", otherSess)

        then:
        res.success == false
        res.status == ResultStatus.FORBIDDEN
        res.errorMessages[0] == 'phone.receive.notMine'

        when: "calling from personal phone"
        res = p1.receiveCall("apiId", "digits", personalSess)

        then:
        res.success == true
        res.status == ResultStatus.OK
        res.payload == "handleSelfCall"

        when: "do not have announcements"
        res = p1.receiveCall("apiId", "digits", session)

        then:
        res.success == true
        res.status == ResultStatus.OK
        res.payload == "relayCall"

        when: "have announcements"
        FeaturedAnnouncement announce = new FeaturedAnnouncement(owner:p1,
            message:"Hello!", expiresAt:DateTime.now().plusDays(2))
        announce.save(flush:true, failOnError:true)
        res = p1.receiveCall("apiId", "digits", session)

        then:
        res.success == true
        res.status == ResultStatus.OK
        res.payload == "handleAnnouncementCall"
    }

    void "test screening incoming call"() {
        given: "phone and incoming sessions"
        p1.phoneService = [screenIncomingCall:{ Phone phone, IncomingSession session ->
            new Result(status:ResultStatus.OK, payload:CallResponse.SCREEN_INCOMING)
        }] as PhoneService
        p1.twimlBuilder = getTwimlBuilder()
        IncomingSession session = new IncomingSession(phone:p1, numberAsString:"5557778888"),
            otherSess = new IncomingSession(phone:p2, numberAsString:"5557778888")
        [session, otherSess]*.save(flush:true, failOnError:true)

        when: "session does not belong to this phone"
        Result<Closure> res = p1.screenIncomingCall(otherSess)

        then:
        res.success == false
        res.status == ResultStatus.FORBIDDEN
        res.errorMessages[0] == "phone.receive.notMine"

        when: "session DOES belong to this phone"
        res = p1.screenIncomingCall(session)

        then:
        res.success == true
        res.status == ResultStatus.OK
        res.payload == CallResponse.SCREEN_INCOMING
    }

    void "test receiving voicemail"() {
        given: "a phone"
        p1.phoneService = Mock(PhoneService)
        p1.twimlBuilder = getTwimlBuilder()
        IncomingSession session = new IncomingSession(phone:p1, numberAsString:"5557778888")
        session.save(flush:true, failOnError:true)

        when: "try starting voicemail prompt when call is already connected (success status)"
        PhoneNumber fromNum = new PhoneNumber(number:"1112223333")
        PhoneNumber toNum = new PhoneNumber(number:"2222223333")
        assert fromNum.validate()
        assert toNum.validate()
        Result<Closure> res = p1.tryStartVoicemail(fromNum, toNum, ReceiptStatus.SUCCESS)

        then: "don't need to connect to voicemail"
        0 * p1.phoneService._
        res.success == true
        res.status == ResultStatus.OK
        res.payload == "noResponse"

        when: "try starting voicemail prompt when call has NOT been connected"
        res = p1.tryStartVoicemail(fromNum, toNum, ReceiptStatus.PENDING)

        then: "will connect to voicemail"
        0 * p1.phoneService._
        res.success == true
        res.status == ResultStatus.OK
        res.payload == CallResponse.CHECK_IF_VOICEMAIL

        when: "completing voicemail when recording is done processing"
        ResultGroup<RecordItemReceipt> resGroup = p1
            .completeVoicemail("callId", "recordingId", "http://www.example.com", 88)

        then:
        1 * p1.phoneService.moveVoicemail(*_) >> new Result(status:ResultStatus.OK)
        1 * p1.phoneService.storeVoicemail(*_) >> new ResultGroup()
    }
}
