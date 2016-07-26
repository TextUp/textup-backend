package org.textup

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import grails.validation.ValidationErrors
import org.joda.time.DateTime
import org.springframework.context.MessageSource
import org.textup.rest.TwimlBuilder
import org.textup.types.CallResponse
import org.textup.types.ContactStatus
import org.textup.types.PhoneOwnershipType
import org.textup.types.ResultType
import org.textup.types.SharePermission
import org.textup.types.StaffStatus
import org.textup.types.TextResponse
import org.textup.util.CustomSpec
import org.textup.validator.IncomingText
import org.textup.validator.OutgoingMessage
import org.textup.validator.TempRecordReceipt
import spock.lang.Ignore
import spock.lang.Shared
import static org.springframework.http.HttpStatus.*

@Domain([Contact, Phone, ContactTag, ContactNumber, Record, RecordItem, RecordText,
	RecordCall, RecordItemReceipt, SharedContact, Staff, Team, Organization, Schedule,
	Location, WeeklySchedule, PhoneOwnership, FeaturedAnnouncement, IncomingSession,
	AnnouncementReceipt, Role, StaffRole])
@TestMixin(HibernateTestMixin)
class PhoneSpec extends CustomSpec {

	static doWithSpring = {
		resultFactory(ResultFactory)
	}

    def setup() {
    	setupData()

        OutgoingMessage.metaClass.getMessageSource = { -> mockMessageSource() }
    }

    def cleanup() {
    	cleanupData()
    }

    protected TwimlBuilder getTwimlBuilder() {
        [build:{ code, params=[:] ->
            new Result(type:ResultType.SUCCESS, success:true, payload:code)
        }, noResponse: { ->
            new Result(type:ResultType.SUCCESS, success:true, payload:"noResponse")
        }] as TwimlBuilder
    }

    void "test constraints"() {
    	when: "we have a phone without a number"
    	Phone p1 = new Phone()
        p1.resultFactory = getResultFactory()

    	then: // no owner
    	p1.validate() == false
    	p1.errors.errorCount == 1
        p1.isActive == false

    	when: "we have a phone with a unique number"
        String num = "5223334444"
    	p1.numberAsString = num
        p1.updateOwner(s1)

    	then:
    	p1.validate() == true
        p1.isActive == true

    	when: "we try to add a phone with a duplicate number"
    	p1.save(flush:true, failOnError:true)
    	p1 = new Phone(numberAsString:num)
        p1.resultFactory = getResultFactory()
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
        List<Record> myContactRecs = Contact.findByPhone(p1)*.record,
            myTagRecs = p1.tags*.record,
            sWithMeRecs = p1.sharedWithMe.collect { it.contact.record },
            allRecs = myContactRecs + myTagRecs + sWithMeRecs
        assert allRecs.isEmpty() == false
        phones = Phone.getPhonesForRecords(allRecs)

        then:
        phones.size() == 2
        [p1, p2].every { it in phones }

        when: "we pass in records belonging to various phones"
        List<Record> otherCRecs = Contact.findByPhone(p2)*.record +
                Contact.findByPhone(tPh1)*.record,
            otherTRecs = p2.tags*.record + tPh1.tags*.record
        allRecs += otherCRecs += otherTRecs
        phones = Phone.getPhonesForRecords(allRecs)

        then:
        phones.size() == 3
        [p1, p2, tPh1].every { it in phones }
    }

    void "test owner and availability"() {
        given: "a phone belonging to a team"
        Phone p1 = new Phone(numberAsString:"1233348934")
        p1.resultFactory = getResultFactory()
        p1.updateOwner(t1)
        p1.save(flush:true, failOnError:true)

        when: "all staff on team are available"
        t1.members.each {
            it.status = StaffStatus.STAFF
            it.manualSchedule = true
            it.isAvailable = true
        }
        t1.save(flush:true, failOnError:true)

        then:
        p1.availableNow.size() == t1.activeMembers.size()
        p1.availableNow.every { it in t1.activeMembers }

        when: "some staff are unavailable"
        Staff unavailableStaff = t1.activeMembers[0]
        unavailableStaff.isAvailable = false
        unavailableStaff.save(flush:true, failOnError:true)

        then:
        p1.availableNow.size() == t1.activeMembers.size() - 1
        p1.availableNow.every { it in t1.activeMembers && it != unavailableStaff }
    }
    void "test getting phones to available now for contact ids"() {
        given: "phone with one unshared contact"
        Phone phone1 = new Phone(numberAsString:"3921920392")
        phone1.resultFactory = getResultFactory()
        phone1.updateOwner(t1)
        phone1.save(flush:true, failOnError:true)
        Contact contact1 = phone1.createContact([:], ["12223334447"]).payload
        phone1.save(flush:true, failOnError:true)

        when: "none available"
        t1.activeMembers.each {
            it.status = StaffStatus.STAFF
            it.manualSchedule = true
            it.isAvailable = false
        }
        t1.save(flush:true, failOnError:true)
        Map<Phone, List<Staff>> phonesToAvailableNow = phone1.
            getPhonesToAvailableNowForContactIds([contact1.id])

        then: "map should be empty, should not have any entries"
        phonesToAvailableNow.isEmpty() == true

        when: "has available, no contacts shared"
        t1.activeMembers.each { it.isAvailable = true }
        t1.save(flush:true, failOnError:true)
        phonesToAvailableNow = phone1.
            getPhonesToAvailableNowForContactIds([contact1.id])

        then: "only this phone to list of available now"
        phonesToAvailableNow.size() == 1
        phonesToAvailableNow[phone1] instanceof List
        t1.activeMembers.every { it in phonesToAvailableNow[phone1] }

        when: "has available, has contacts shared"
        assert s1 in t1.activeMembers
        assert s1.phone
        Result res = phone1.share(contact1, s1.phone, SharePermission.DELEGATE)
        assert res.success
        phonesToAvailableNow = phone1.
            getPhonesToAvailableNowForContactIds([contact1.id])

        then: "this phone and other sharedWith phones too"
        phonesToAvailableNow.size() == 2
        phonesToAvailableNow[phone1] instanceof List
        t1.activeMembers.every { it in phonesToAvailableNow[phone1] }
        phonesToAvailableNow[s1.phone] instanceof List
        phonesToAvailableNow[s1.phone].size() == 1
        phonesToAvailableNow[s1.phone] == [s1]
    }

    void "test transferring phone"() {
        given: "baselines, staff without phone"
        int oBaseline = PhoneOwnership.count()
        int pBaseline = Phone.count()

        Staff noPhoneStaff = new Staff(username:"888-8sta$iterationCount",
            password:"password", name:"Staff", email:"staff@textup.org",
            org:org, personalPhoneAsString:"1112223333", status: StaffStatus.STAFF)
        noPhoneStaff.save(flush:true, failOnError:true)

        when: "transferring to a nonexistent entity"
        Result<PhoneOwnership> res = s1.phone.transferTo(-88888,
            PhoneOwnershipType.INDIVIDUAL)

        then:
        res.success == false
        res.type == ResultType.VALIDATION
        res.payload.errorCount == 1

        when: "transferring to an entity that doesn't already have a phone"
        Phone myPhone = s1.phone,
            targetPhone = null
        def initialOwner = s1,
            targetOwner = noPhoneStaff
        res = myPhone.transferTo(targetOwner.id, PhoneOwnershipType.INDIVIDUAL)
        myPhone.save(flush:true, failOnError:true)

        then: "phone is transferred"
        res.success == true
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
    	res.payload instanceof Map
    	res.payload.code == "phone.share.cannotShare"

    	when: "we start sharing contact with someone on the same team "
    	res = p1.share(c1, p2, SharePermission.DELEGATE)

    	then:
    	res.success == true
    	res.payload.instanceOf(SharedContact)

    	when: "we start sharing contact that we've already shared with the same person"
    	int sBaseline = SharedContact.count()
		res = p1.share(c1, p2, SharePermission.DELEGATE)
		assert res.success
		SharedContact shared0 = res.payload
		res.payload.save(flush:true, failOnError:true)

    	then: "we don't create a duplicate SharedContact"
    	shared0.instanceOf(SharedContact)
    	SharedContact.count() == sBaseline

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
    	res.payload instanceof Map
    	res.payload.code == "phone.contactNotMine"

    	when: "we stop sharing contact that is not shared"
        Contact c1_3 = p1.createContact([:], ["12223334447"]).payload
        c1_3.save(flush:true, failOnError:true)
    	res = p1.stopShare(c1_3)

    	then: "silently ignore that contact is not shared"
    	res.success == true

    	when: "we stop sharing by phones"
    	assert p1.stopShare(p2).success
    	p1.save(flush:true, failOnError:true)

    	then:
    	p1.sharedByMe == []
    	p1.sharedWithMe == [shared3]

    	when: "stop sharing by contacts"
    	SharedContact shared4 = p2.share(c2, p3, SharePermission.DELEGATE).payload
    	p2.save(flush:true, failOnError:true)
        //same underlying contact
    	assert p2.sharedByMe.every { it in [shared4, shared3]*.contact }
    	assert p1.sharedWithMe == [shared3] && p3.sharedWithMe == [shared4]

    	p2.stopShare(c2)
    	p2.save(flush:true, failOnError:true)

    	then:
    	p2.sharedByMe == []
    	p1.sharedWithMe == []
    	p3.sharedWithMe == []
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

    void "test listing contacts excludes expired"() {
        given: "a contact shared by p1, shared with p2"
        assert sc1.isActive
        assert sc1.sharedBy == p1
        assert sc1.sharedWith == p2

        when: "we have a shared contact"
        List<Contactable> contactables = p2.getContacts(statuses:
            [ContactStatus.ACTIVE, ContactStatus.UNREAD])

        then: "shared shows up"
        contactables.contains(sc1)

        when: "we expire"
        sc1.stopSharing()
        sc1.save(flush:true, failOnError:true)
        contactables = p2.getContacts(statuses:
            [ContactStatus.ACTIVE, ContactStatus.UNREAD])

        then: "shared does not show up anymore"
        !contactables.contains(sc1)
    }

    void "test creating contacts"() {
        when: "we create a blank contact"
        Result res = p1.createContact()

        then:
        res.success == true
        res.payload.instanceOf(Contact)

        when: "contact with duplicate numbers"
        int cNumBaseline = ContactNumber.count(),
            contactBaseline = Contact.count()
        String number = "1112223333",
            name = "Kiki"
        res = p1.createContact([name:name], [number, number, number])
        p1.save(flush:true, failOnError:true)

        then:
        res.success == true
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
            name1 = "Kiki"
        res = p1.createContact([name:name1], [num2, num2, num1, num2, num1])
        p1.save(flush:true, failOnError:true)

        then:
        res.success == true
        res.payload.instanceOf(Contact)
        res.payload.name == name
        res.payload.numbers.size() == 2
        res.payload.numbers[0].number == num2
        res.payload.numbers[0].preference == 0
        res.payload.numbers[1].number == num1
        res.payload.numbers[1].preference == 1
        Contact.count() == contactBaseline + 1
        ContactNumber.count() == cNumBaseline + 2
    }

    void "test creating tags"() {
    	given:
        int tagBaseline = ContactTag.count()
        int recBaseline = Record.count()
        int origNumTags = p1.tags.size()

    	when: "we add a tag with unique name"
    	assert p1.createTag(name:"tag1").success
    	p1.save(flush:true, failOnError:true)

    	then:
    	p1.tags.size() == origNumTags + 1
        ContactTag.count() == tagBaseline + 1
        Record.count() == recBaseline + 1

    	when: "we add a tag with a duplicate name"
    	Result res = p1.createTag(name:"tag1")

    	then:
    	res.success == false
    	res.payload instanceof ValidationErrors
    	res.payload.errorCount == 1
        ContactTag.count() == tagBaseline + 1
        Record.count() == recBaseline + 1

    	when: "we change to a unique name"
    	res = p1.createTag(name:"tag2")
        p1.save(flush:true, failOnError:true)

    	then:
    	res.success == true
        res.payload.instanceOf(ContactTag)
        p1.tags.size() == origNumTags + 2
        ContactTag.count() == tagBaseline + 2
        Record.count() == recBaseline + 2
    }

    // Communications functionality
    // ----------------------------

    void "test outgoing communication when phone is inactive"() {
        given: "an inactive phone"
        p1.deactivate()
        p1.save(flush:true, failOnError:true)
        assert !p1.isActive

        when: "send text"
        OutgoingMessage text = new OutgoingMessage(message:'hi')
        text.contacts << c1
        ResultList resList = p1.sendMessage(text, s1)

        then:
        resList.isAnySuccess == false
        resList.results.size() == 1
        resList.results[0].success == false
        resList.results[0].type == ResultType.MESSAGE_STATUS
        resList.results[0].payload.status == NOT_FOUND
        resList.results[0].payload.code == "phone.isInactive"

        when: "start bridge call"
        resList = p1.startBridgeCall(c1, s1)

        then:
        resList.isAnySuccess == false
        resList.results.size() == 1
        resList.results[0].success == false
        resList.results[0].type == ResultType.MESSAGE_STATUS
        resList.results[0].payload.status == NOT_FOUND
        resList.results[0].payload.code == "phone.isInactive"

        when: "send announcement"
        Result res = p1.sendAnnouncement("hi", DateTime.now().plusDays(1), s1)

        then:
        res.success == false
        res.type == ResultType.MESSAGE_STATUS
        res.payload.status == NOT_FOUND
        res.payload.code == "phone.isInactive"
    }
    void "test sending text"() {
        given: "a phone"
        p1.phoneService = [sendMessage:{ Phone phone, OutgoingMessage text, Staff staff ->
            new ResultList()
        }] as PhoneService

        when: "we have an invalid outgoing text"
        OutgoingMessage text = new OutgoingMessage()
        assert text.validateSetPhone(p1) == false
        ResultList<RecordText> resList = p1.sendMessage(text, s1)

        then:
        resList.isAnySuccess == false
        resList.results.size() == 1
        resList.results[0].success == false
        resList.results[0].payload instanceof ValidationErrors

        when: "we pass in a staff that is not an owner"
        text = new OutgoingMessage(message:"hello", contacts:[c1, c1_1])
        assert text.validateSetPhone(p1) == true
        resList = p1.sendMessage(text, otherS2)

        then:
        resList.isAnySuccess == false
        resList.results.size() == 1
        resList.results[0].success == false
        resList.results[0].payload instanceof Map
        resList.results[0].payload.status == FORBIDDEN
        resList.results[0].payload.message == "phone.notOwner"

        when: "we pass in a valid outgoing text and staff that is owner"
        resList = p1.sendMessage(text, s1)

        then:
        resList.results.isEmpty() == true
    }

    void "test starting and completing bridge call"() {
        given: "a phone"
        p1.phoneService = [startBridgeCall:{ Phone phone, Contactable c1, Staff staff ->
            new Result(type:ResultType.SUCCESS, success:true, payload:null)
        }] as PhoneService
        p1.twimlBuilder = getTwimlBuilder()
        s1.personalPhoneAsString = "1112223333"
        s1.save(flush:true, failOnError:true)

        when: "try to call that does not belong to this phone"
        ResultList<RecordCall> resList = p1.startBridgeCall(tC1, s1)

        then:
        resList.isAnySuccess == false
        resList.results.size() == 1
        resList.results[0].success == false
        resList.results[0].payload instanceof Map
        resList.results[0].payload.status == FORBIDDEN
        resList.results[0].payload.message == "phone.startBridgeCall.forbidden"

        when: "try to call shared contact that is not shared with this phone"
        resList = p1.startBridgeCall(sc1, s1)

        then:
        resList.isAnySuccess == false
        resList.results.size() == 1
        resList.results[0].success == false
        resList.results[0].payload instanceof Map
        resList.results[0].payload.status == FORBIDDEN
        resList.results[0].payload.message == "phone.startBridgeCall.forbidden"

        when: "try to call shared contact that we don't have modify permissions for"
        sc2.permission = SharePermission.VIEW
        sc2.save(flush:true, failOnError:true)
        resList = p1.startBridgeCall(sc2, s1)

        then:
        resList.isAnySuccess == false
        resList.results.size() == 1
        resList.results[0].success == false
        resList.results[0].payload instanceof Map
        resList.results[0].payload.status == FORBIDDEN
        resList.results[0].payload.message == "phone.startBridgeCall.forbidden"

        when: "pass in a staff that is not an owner of this phone"
        sc2.permission = SharePermission.DELEGATE
        sc2.save(flush:true, failOnError:true)
        resList = p1.startBridgeCall(sc2, otherS1)

        then:
        resList.isAnySuccess == false
        resList.results.size() == 1
        resList.results[0].success == false
        resList.results[0].payload instanceof Map
        resList.results[0].payload.status == FORBIDDEN
        resList.results[0].payload.message == "phone.notOwner"

        when: "pass in valid staff, but staff has no personal phone number"
        s1.personalPhoneAsString = null
        s1.save(flush:true, failOnError:true)
        resList = p1.startBridgeCall(c1, s1)

        then:
        resList.isAnySuccess == false
        resList.results.size() == 1
        resList.results[0].success == false
        resList.results[0].payload instanceof Map
        resList.results[0].payload.status == UNPROCESSABLE_ENTITY
        resList.results[0].payload.message == "phone.startBridgeCall.noPersonalNumber"

        when: "we pass in all valid"
        s1.personalPhoneAsString = "1112223333"
        s1.save(flush:true, failOnError:true)
        resList = p1.startBridgeCall(c1, s1)

        then:
        resList.isAnySuccess == true
        resList.isAllSuccess == true
        resList.results.size() == 1

        when: "we confirm call bridge"
        Result<Closure> res = p1.confirmBridgeCall(c1)

        then:
        res.success == true
        res.payload == CallResponse.CONFIRM_BRIDGE

        when: "complete call bridge"
        res = p1.finishBridgeCall(c1)

        then:
        res.success == true
        res.payload == CallResponse.FINISH_BRIDGE
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
            ResultMap<TempRecordReceipt> resMap = new ResultMap<>()
            resMap[subNum] = new Result(type:ResultType.SUCCESS, success:true)
            resMap
        }, startCallAnnouncement:{ Phone phone, String message,
            String identifier, List<IncomingSession> sessions, Staff staff ->
            ResultMap<TempRecordReceipt> resMap = new ResultMap<>()
            resMap[subNum] = new Result(type:ResultType.SUCCESS, success:true)
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
        res.payload.instanceOf(FeaturedAnnouncement)

        when: "read announcements"
        IncomingSession session = new IncomingSession(phone:p1, numberAsString:"5557778888")
        Result<Closure> closureRes = p1.completeCallAnnouncement(null, null,
            null, session)

        then:
        closureRes.success == true
        closureRes.payload == CallResponse.ANNOUNCEMENT_AND_DIGITS

        when: "confirm unsubscribed"
        closureRes = p1.completeCallAnnouncement(Constants.CALL_ANNOUNCEMENT_UNSUBSCRIBE,
            "message", "identifier", session)

        then:
        session.isSubscribedToCall == false
        closureRes.success == true
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
        res.payload instanceof Map
        res.payload.status == UNPROCESSABLE_ENTITY
        res.payload.message == "phone.sendAnnouncement.expiresInPast"

        when: "pass in staff that is not an owner"
        res = p1.sendAnnouncement("hello", DateTime.now().plusDays(1), otherS1)

        then:
        res.success == false
        res.payload instanceof Map
        res.payload.status == FORBIDDEN
        res.payload.message == "phone.notOwner"
    }

    void "test announcement none reached"() {
        given: "phone and incoming sessions, some coinciding with contacts"
        p1.twimlBuilder = getTwimlBuilder()
        p1.phoneService = [sendTextAnnouncement:{ Phone phone, String message,
            String identifier, List<IncomingSession> sessions, Staff staff ->
            new ResultMap<TempRecordReceipt>()
        }, startCallAnnouncement:{ Phone phone, String message,
            String identifier, List<IncomingSession> sessions, Staff staff ->
            new ResultMap<TempRecordReceipt>()
        }] as PhoneService

        when: "none reached with no subscribers"
        Result<FeaturedAnnouncement> res = p1.sendAnnouncement("hello",
            DateTime.now().plusDays(1), s1)

        then:
        res.success == true
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
        res.payload instanceof Map
        res.type == ResultType.MESSAGE_LIST_STATUS
        res.payload.status == INTERNAL_SERVER_ERROR
    }

    void "test receiving text"() {
        given: "a phone"
        p1.phoneService = [relayText:{ Phone phone, IncomingText text,
            IncomingSession session ->
            new Result(type:ResultType.SUCCESS, success:true, payload:null)
        }] as PhoneService
        p1.twimlBuilder = getTwimlBuilder()
        IncomingSession session = new IncomingSession(phone:p1, numberAsString:"5557778888"),
            otherSess = new IncomingSession(phone:p2, numberAsString:"5557778888")
        session.save(flush:true, failOnError:true)

        when: "invalid incoming text"
        IncomingText text = new IncomingText()
        assert text.validate() == false
        Result<Closure> res = p1.receiveText(text, session)

        then:
        res.success == false
        res.type == ResultType.VALIDATION
        res.payload instanceof ValidationErrors

        when: "session does not belong to this phone"
        text = new IncomingText(apiId:"apiId", message:"hello")
        assert text.validate()
        res = p1.receiveText(text, otherSess)

        then:
        res.success == false
        res.type == ResultType.MESSAGE_STATUS
        res.payload.status == FORBIDDEN
        res.payload.code == 'phone.receive.notMine'

        when: "we don't have any announcements"
        res = p1.receiveText(text, session)

        then: "relay text"
        res.success == true

        when: "we have announcements and message isn't a valid keyword"
        FeaturedAnnouncement announce = new FeaturedAnnouncement(owner:p1,
            message:"Hello!", expiresAt:DateTime.now().plusDays(2))
        announce.save(flush:true, failOnError:true)
        text.message = "invalid keyword"
        assert text.validate()
        res = p1.receiveText(text, session)

        then: "relay text"
        res.success == true

        when: "have announcements and see announcements"
        int aReceiptBaseline = AnnouncementReceipt.count()
        text.message = Constants.TEXT_SEE_ANNOUNCEMENTS
        assert text.validate()
        res = p1.receiveText(text, session)
        p1.save(flush:true, failOnError:true)

        then:
        AnnouncementReceipt.count() == aReceiptBaseline + 1
        res.success == true
        res.payload == TextResponse.SEE_ANNOUNCEMENTS

        when: "multiple receipts are not added for same announcement and session"
        res = p1.receiveText(text, session)

        then:
        AnnouncementReceipt.count() == aReceiptBaseline + 1
        res.success == true
        res.payload == TextResponse.SEE_ANNOUNCEMENTS

        when: "have announcements, is NOT subscribed, toggle subscription"
        session.isSubscribedToText = false
        session.save(flush:true, failOnError:true)
        text.message = Constants.TEXT_TOGGLE_SUBSCRIBE
        assert text.validate()
        res = p1.receiveText(text, session)

        then:
        session.isSubscribedToText == true
        res.success == true
        res.payload == TextResponse.SUBSCRIBED

        when: "have announcementsm, is subscribed, toggle subscription"
        session.isSubscribedToText = true
        session.save(flush:true, failOnError:true)
        text.message = Constants.TEXT_TOGGLE_SUBSCRIBE
        assert text.validate()
        res = p1.receiveText(text, session)

        then:
        session.isSubscribedToText == false
        res.success == true
        res.payload == TextResponse.UNSUBSCRIBED
    }

    void "test receiving call"() {
        given: "a phone and incoming sessions"
        p1.phoneService = [relayCall:{ Phone phone, String apiId,
            IncomingSession session ->
            new Result(type:ResultType.SUCCESS, success:true, payload:"relayCall")
        }, handleAnnouncementCall: { Phone phone, String apiId, String digits,
            IncomingSession session ->
            new Result(type:ResultType.SUCCESS, success:true, payload:"handleAnnouncementCall")
        }, handleSelfCall:{ Phone phone, String apiId, String digits, Staff staff ->
            new Result(type:ResultType.SUCCESS, success:true, payload:"handleSelfCall")
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
        res.type == ResultType.MESSAGE_STATUS
        res.payload.status == FORBIDDEN
        res.payload.code == 'phone.receive.notMine'

        when: "calling from personal phone"
        res = p1.receiveCall("apiId", "digits", personalSess)

        then:
        res.success == true
        res.payload == "handleSelfCall"

        when: "do not have announcements"
        res = p1.receiveCall("apiId", "digits", session)

        then:
        res.success == true
        res.payload == "relayCall"

        when: "have announcements"
        FeaturedAnnouncement announce = new FeaturedAnnouncement(owner:p1,
            message:"Hello!", expiresAt:DateTime.now().plusDays(2))
        announce.save(flush:true, failOnError:true)
        res = p1.receiveCall("apiId", "digits", session)

        then:
        res.success == true
        res.payload == "handleAnnouncementCall"
    }

    void "test receiving voicemail"() {
        given: "a phone"
        p1.phoneService = [moveVoicemail:{ String apiId ->
            new Result(type:ResultType.SUCCESS, success:true, payload:null)
        }, storeVoicemail: { String apiId, int voicemailDuration ->
            ResultList<RecordItemReceipt> resList = new ResultList<>()
            resList << new Result(type:ResultType.SUCCESS, success:true, payload:null)
            resList
        }] as PhoneService
        p1.twimlBuilder = getTwimlBuilder()
        IncomingSession session = new IncomingSession(phone:p1, numberAsString:"5557778888")
        session.save(flush:true, failOnError:true)

        when: "starting voicemail prompt"
        Result<Closure> res = p1.receiveVoicemail("apiId", null, session)

        then:
        res.success == true
        res.payload == CallResponse.VOICEMAIL

        when: "completing voicemail"
        res = p1.receiveVoicemail("apiId", 88, session)

        then:
        res.success == true
        res.payload == "noResponse"
    }
}
