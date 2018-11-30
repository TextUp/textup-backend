package org.textup

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestFor
import grails.test.mixin.TestMixin
import grails.validation.ValidationErrors
import org.joda.time.DateTime
import org.springframework.context.MessageSource
import org.springframework.context.support.StaticMessageSource
import org.textup.type.ContactStatus
import org.textup.type.SharePermission
import org.textup.type.StaffStatus
import org.textup.type.VoiceLanguage
import org.textup.util.*
import spock.lang.Shared
import spock.lang.Specification

@TestFor(ContactService)
@Domain([Contact, Phone, ContactTag, ContactNumber, Record, RecordItem, RecordText,
  RecordCall, RecordItemReceipt, SharedContact, Staff, Team, Organization,
  Schedule, Location, WeeklySchedule, PhoneOwnership, Role, StaffRole, NotificationPolicy,
  RecordNote, RecordNoteRevision, FutureMessage, SimpleFutureMessage,
  MediaInfo, MediaElement, MediaElementVersion])
@TestMixin(HibernateTestMixin)
class ContactServiceSpec extends CustomSpec {

    @Shared
    MessageSource staticMessageSource = new StaticMessageSource()

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        setupData()
        service.resultFactory = TestUtils.getResultFactory(grailsApplication)
        service.authService = [
            getLoggedInAndActive: { -> s1 }
        ] as AuthService
        service.socketService = [
            sendItems: { List<? extends RecordItem> items ->
                new Result(status:ResultStatus.OK, payload:items).toGroup()
            }
        ] as SocketService
    }

    def cleanup() {
        cleanupData()
    }

    // Create
    // ------

    void "test validate number actions"() {
        when: "we create with number actions that isn't a list"
        Result res = service.create(p1, [doNumberActions:"I am not a list"])

        then:
        res.success == false
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages.size() == 1
        res.errorMessages[0] == "emptyOrNotACollection"

        when: "we create with number actions that defines an unspecified action"
        List doNumberActions = [[number:"12223334444", preference:0, action:"invalid"]]
        res = service.create(p1, [doNumberActions:doNumberActions])

        then:
        res.success == false
        res.errorMessages.size() == 1
        res.errorMessages[0] == "actionContainer.invalidActions"
    }

    void "test create"() {
        given: "baselines"
        boolean wasCalled = false
        service.metaClass.createHelper = { Phone p1, Map body, List<String> nums ->
            wasCalled = true
            new Result(status:ResultStatus.CREATED, payload:[numbers:nums])
        }

    	when: "we create with a null phone"
    	Result<Contact> res = service.create(null, [:])

    	then:
    	res.success == false
    	res.errorMessages[0] == "contactService.create.noPhone"
    	res.status == ResultStatus.UNPROCESSABLE_ENTITY
        wasCalled == false

    	when: "we create with valid input that is a mix of add and delete number actions"
    	String num1 = "2223334444", num2 = "2223334445"
    	Map contactInfo = [doNumberActions:[
    		[number:num1, preference:0, action:Constants.NUMBER_ACTION_MERGE],
    		[number:num2, preference:2, action:Constants.NUMBER_ACTION_MERGE],
    		[number:"12223334443", action:Constants.NUMBER_ACTION_DELETE]
		]]
    	res = service.create(s1.phone, contactInfo)
    	assert res.success == true
        s1.phone.save(flush:true, failOnError:true)

    	then: "delete number actions are ignored"
        wasCalled == true
        res.status == ResultStatus.CREATED
    	res.payload.numbers.size() == 2
    	res.payload.numbers[0] == num1
    	res.payload.numbers[1] == num2
    }

    // Update
    // ------

    void "test update overall"() {
        given: "baselines"
        int cBaseline = Contact.count()
        int nBaseline = ContactNumber.count()

        when: "we try to update a nonexistent contact"
        Result res = service.update(-88L, [:])

        then:
        res.success == false
        res.errorMessages[0] == "contactService.update.notFound"
        res.status == ResultStatus.NOT_FOUND
        Contact.count() == cBaseline
        ContactNumber.count() == nBaseline

        when: "we try to update with a valid contact id but a nonexistent shared id"
        String name = "kiki bai"
        res = service.update(c1.id, [name: name], -88L)

        then:
        res.success == false
        res.errorMessages[0] == "contactService.update.notFound"
        res.status == ResultStatus.NOT_FOUND

        when: "we successfully update a contact"
        res = service.update(c1.id, [name: name])

        then:
        res.success == true
        res.status == ResultStatus.OK
        res.payload instanceof Contact
        res.payload.name == name
        Contact.count() == cBaseline
        ContactNumber.count() == nBaseline

        when: "we successfully update a shared contact"
        assert sc1.canModify
        String name2 = TestUtils.randString()
        res = service.update(sc1.contactId, [name: name2], sc1.id)

        then:
        res.status == ResultStatus.OK
        res.payload instanceof SharedContact
        res.payload.id == sc1.id
        res.payload.name == name2
        Contact.count() == cBaseline
        ContactNumber.count() == nBaseline
    }

    void "test update contact info"() {
        given: "baselines"
        int cBaseline = Contact.count()
        int nBaseline = ContactNumber.count()
        DateTime originalTouched = c1.lastTouched

        when: "we update with invalid fields"
        Map updateInfo = [
            name: "non marie",
            note: "you are awesome. keep up the good work!",
            status:"invalid"
        ]
        Result res = service.updateContactInfo(c1, updateInfo)

        then:
        res.success == false
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages.size() == 1
        Contact.count() == cBaseline
        ContactNumber.count() == nBaseline

        when: "we update with valid fields"
        updateInfo.status = "uNrEAd"
        updateInfo.language = VoiceLanguage.JAPANESE.toString()
        res = service.updateContactInfo(c1, updateInfo)

        then:
        res.success == true
        res.status == ResultStatus.OK
        res.payload instanceof Contact
        res.payload.name == updateInfo.name
        res.payload.note == updateInfo.note
        res.payload.record.language == VoiceLanguage.JAPANESE
        res.payload.status == ContactStatus.UNREAD
        res.payload.lastTouched.isAfter(originalTouched)
        Contact.count() == cBaseline
        ContactNumber.count() == nBaseline
    }

    void "test updating contact info with shared contact provided"() {
        given: "shared contact with collaborator permissions"
        Contact contact1 = sc1.contact
        contact1.status = ContactStatus.UNREAD
        contact1.lastTouched = DateTime.now().minusDays(6)
        sc1.permission = SharePermission.DELEGATE
        sc1.lastTouched = DateTime.now().minusDays(3)
        DateTime contactOriginalTouched = contact1.lastTouched,
            sharedOriginalTouched = sc1.lastTouched
        [sc1, contact1]*.save(flush:true, failOnError:true)
        assert sc1.isActive && sc1.canModify

        when: "updating fields with shared contact info provided too"
        Map updateInfo = [
            name: UUID.randomUUID().toString(),
            note: UUID.randomUUID().toString(),
            status: ContactStatus.ARCHIVED.toString()
        ]
        ContactStatus newStatus1 = TypeConversionUtils.convertEnum(ContactStatus, updateInfo.status)
        Result res = service.updateContactInfo(contact1, updateInfo, sc1)

        then: "all field updated except for the status, which is updated on shared contact"
        res.status == ResultStatus.OK
        res.payload instanceof Contact
        res.payload.name == updateInfo.name
        res.payload.note == updateInfo.note
        res.payload.status != newStatus1
        res.payload.lastTouched.isEqual(contactOriginalTouched)
        res.payload.lastTouched.isEqual(sharedOriginalTouched) == false
        // status and last touched updated on shared contact instead of original contact
        SharedContact.get(sc1.id).status == newStatus1
        SharedContact.get(sc1.id).lastTouched.isAfter(sharedOriginalTouched)

        when: "shared contact is view-only"
        sc1 = SharedContact.get(sc1.id)

        sc1.permission = SharePermission.VIEW
        sc1.save(flush:true, failOnError:true)
        sharedOriginalTouched = sc1.lastTouched

        updateInfo = [
            name: UUID.randomUUID().toString(),
            note: UUID.randomUUID().toString(),
            status: ContactStatus.BLOCKED.toString()
        ]
        newStatus1 = TypeConversionUtils.convertEnum(ContactStatus, updateInfo.status)
        res = service.updateContactInfo(contact1, updateInfo, sc1)

        then: "only status flag is respected"
        res.status == ResultStatus.OK
        res.payload instanceof Contact
        res.payload.name != updateInfo.name
        res.payload.note != updateInfo.note
        res.payload.status != newStatus1
        // collaborators of any permission level can update status, which implicitly updates
        // last touched
        SharedContact.get(sc1.id).status == newStatus1
        SharedContact.get(sc1.id).lastTouched.isAfter(sharedOriginalTouched)
    }

    void "test updating with notification actions"() {
        given: "baselines"
        int cBaseline = Contact.count()
        boolean wasServiceCalled = false
        service.notificationService = [
            handleNotificationActions: { Phone p1, Long recordId, Object rawActions ->
                wasServiceCalled = true
                new Result(status:ResultStatus.NO_CONTENT)
            }
        ] as NotificationService

        when: "we have notification actions"
        Map notifActions = [doNotificationActions:[[hello:"yes"]]]
        Result res = service.handleNotificationActions(c1, notifActions)

        then: "they are handled"
        wasServiceCalled == true
        res.success == true
        res.payload instanceof Contact
        res.payload.id == c1.id
        Contact.count() == cBaseline

        when: "with delegate permissions"
        sc1.permission = SharePermission.DELEGATE
        sc1.save(flush:true, failOnError:true)

        wasServiceCalled = true
        res = service.handleNotificationActions(sc1.contact, notifActions, sc1)

        then: "ALLOWED because delegate collaborators do receive notifications and should be able to \
            configure these notifications"
        wasServiceCalled == true
        res.success == true
        res.payload instanceof Contact
        res.payload.id == sc1.contact.id
        Contact.count() == cBaseline

        when: "with view-only permissions"
        sc1.permission = SharePermission.VIEW
        sc1.save(flush:true, failOnError:true)

        wasServiceCalled = false
        res = service.handleNotificationActions(sc1.contact, notifActions, sc1)

        then: "ignored"
        wasServiceCalled == false
        res.success == true
        res.payload instanceof Contact
        res.payload.id == sc1.contact.id
        Contact.count() == cBaseline
    }

    void "test updating with number actions"() {
        given: "baselines"
        int cBaseline = Contact.count()
        int nBaseline = ContactNumber.count()

        when: "we try to delete nonexistent number"
        Map numActions = [doNumberActions:[
            [number:"2223334443", action:Constants.NUMBER_ACTION_DELETE]
        ]]
        Result res = service.handleNumberActions(c1, numActions)

        then:
        res.success == false
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages[0] == "contact.numberNotFound"
        Contact.count() == cBaseline
        ContactNumber.count() == nBaseline

        when: "we update with valid number actions"
        int numOriginalNums = c1.numbers.size()
        List<ContactNumber> c1Nums = c1.numbers.collect { it }
        c1Nums.each {
            c1.deleteNumber(it.number)
        }
        assert c1.numbers.size() == 0 //c1 starts with no numbers
        c1.save(flush:true, failOnError:true)
        String num1 = "2223334444", num2 = "2223334445"
        numActions = [doNumberActions:[
            [number:num1, preference:0, action:Constants.NUMBER_ACTION_MERGE],
            [number:num2, preference:2, action:Constants.NUMBER_ACTION_MERGE],
        ]]
        res = service.handleNumberActions(c1, numActions)
        c1.save(flush:true, failOnError:true)

        then:
        res.success == true
        res.status == ResultStatus.OK
        res.payload instanceof Contact
        res.payload.numbers.size() == 2
        res.payload.numbers[0].number == num1
        res.payload.numbers[1].number == num2
        Contact.count() == cBaseline
        ContactNumber.count() == nBaseline - numOriginalNums + 2

        when: "we delete one of the numbers we just added with view-only permissions"
        sc1.permission = SharePermission.VIEW
        sc1.save(flush:true, failOnError:true)

        numActions = [doNumberActions:[
            [number:num1, action:Constants.NUMBER_ACTION_DELETE]
        ]]
        res = service.handleNumberActions(c1, numActions, sc1)
        c1.save(flush:true, failOnError:true)

        then: "ignored"
        res.success == true
        res.status == ResultStatus.OK
        res.payload instanceof Contact
        res.payload.numbers.size() == 2 // still two -- no deletion happened

        when: "try deleting again with delegate permissions"
        sc1.permission = SharePermission.DELEGATE
        sc1.save(flush:true, failOnError:true)

        res = service.handleNumberActions(c1, numActions, sc1)
        c1.save(flush:true, failOnError:true)

        then: "allowed"
        res.success == true
        res.status == ResultStatus.OK
        res.payload instanceof Contact
        res.payload.numbers.size() == 1
        res.payload.numbers[0].number == num2
        Contact.count() == cBaseline
        ContactNumber.count() == nBaseline - numOriginalNums + 1
    }

    void "test share actions invalid"() {
        given: "baselines"
        int cBaseline = Contact.count()
        int nBaseline = ContactNumber.count()
        int sBaseline = SharedContact.count()

        when: "we update with share actions that aren't a list"
        Map updateInfo = [doShareActions:"I am not a list"]
        Result res = service.handleShareActions(c1, updateInfo)

        then:
        res.success == false
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages.size() == 1
        res.errorMessages[0] == "emptyOrNotACollection"
        Contact.count() == cBaseline
        ContactNumber.count() == nBaseline
        SharedContact.count() == sBaseline

        when: "we try to update with unspecified share actions"
        updateInfo = [doShareActions:[
            [id:s2.phone.id, action:"invalid", permission:SharePermission.DELEGATE]
        ]]
        res = service.handleShareActions(c1, updateInfo)

        then:
        res.success == false
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages.size() == 1
        res.errorMessages[0] == "actionContainer.invalidActions"
        Contact.count() == cBaseline
        ContactNumber.count() == nBaseline
        SharedContact.count() == sBaseline

        when: "we try to share with nonexistent staff member"
        updateInfo = [doShareActions:[
            [id:-88L, action:Constants.SHARE_ACTION_MERGE, permission:SharePermission.DELEGATE]
        ]]
        res = service.handleShareActions(c1, updateInfo)

        then:
        res.success == false
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages.size() == 1
        res.errorMessages[0] == "actionContainer.invalidActions"
        Contact.count() == cBaseline
        ContactNumber.count() == nBaseline
        SharedContact.count() == sBaseline

        when: "we try to share with staff member we can't share with (on a different team)"
        updateInfo = [doShareActions:[
            [id:s3.phone.id, action:Constants.SHARE_ACTION_MERGE,
                permission:SharePermission.DELEGATE]
        ]]
        res = service.handleShareActions(c1, updateInfo)

        then:
        res.success == false
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages.size() == 1
        res.errorMessages[0] == "phone.share.cannotShare"
        Contact.count() == cBaseline
        ContactNumber.count() == nBaseline
        SharedContact.count() == sBaseline
    }

    void "test building sharing messages"() {
        given:
        String code = "note.sharing.stop"
        int nBaseline = RecordNote.count()

        when: "missing some information"
        HashSet<String> names = new HashSet<>(["name1", "name2", "name3"])
        Result<RecordNote> res = service.recordSharingChangesHelper(null, names,
            code, s1.toAuthor())

        then: "validation errors"
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages.size() == 1
        res.errorMessages[0].contains("null")
        RecordNote.count() == nBaseline

        when: "passing in an invalid code"
        res = service.recordSharingChangesHelper(c1.record, names, "invalid code", s1.toAuthor())

        then: "error is logged but the record note is still created"
        res.status == ResultStatus.OK
        res.payload instanceof RecordNote
        RecordNote.count() == nBaseline + 1

        when: "all valid info passed in"
        res = service.recordSharingChangesHelper(c1.record, names, code, s1.toAuthor())

        then: "a new record note is created"
        res.status == ResultStatus.OK
        res.payload instanceof RecordNote
        RecordNote.count() == nBaseline + 2
    }

    void "test recording sharing changes"() {
        given:
        int nBaseline = RecordNote.count()

        when: "no changes to record"
        ResultGroup<RecordNote> resGroup = service.recordSharingChanges(c1.record, [:], [])

        then: "empty result group"
        resGroup.isEmpty == true
        RecordNote.count() == nBaseline

        when: "changes in permission and stop sharing"
        Map<Phone,SharePermission> sharedWithToPermission = [(p1):SharePermission.DELEGATE,
            (p2):SharePermission.VIEW]
        Collection<Phone> stopSharingPhones = [tPh1]
        resGroup = service.recordSharingChanges(c1.record, sharedWithToPermission, stopSharingPhones)

        then: "three notes created"
        resGroup.isEmpty == false
        resGroup.successStatus == ResultStatus.OK
        resGroup.payload.size() == 3
        resGroup.successes.size() == 3
        RecordNote.count() == nBaseline + 3
    }

    void "test share actions"() {
        given: "baselines"
        int cBaseline = Contact.count()
        int nBaseline = ContactNumber.count()
        int sBaseline = SharedContact.count()
        int noteBaseline = RecordNote.count()

        when: "we try to share a team's contacts"
        Map updateInfo = [doShareActions:[
            [id:t1.phone.id, action:Constants.SHARE_ACTION_MERGE,
                permission:SharePermission.DELEGATE]
        ]]
        Result res = service.handleShareActions(c1, updateInfo)
        c1.save(flush:true, failOnError:true)

        then: "successfully shared + sharing changes noted in record"
        res.success == true
        res.payload instanceof Contact
        SharedContact.findBySharedWithAndSharedByAndContactAndPermission(t1.phone,
            c1.phone, c1, SharePermission.DELEGATE) != null
        t1.phone.sharedWithMe.any { it.contact.id == c1.id } == true
        s1.phone.sharedByMe.any { it.id == c1.id } == true
        Contact.count() == cBaseline
        ContactNumber.count() == nBaseline
        SharedContact.count() == sBaseline + 1
        RecordNote.count() == noteBaseline + 1

        when: "we try to share with a staff member on the same team as us"
        s2.status = StaffStatus.STAFF // can only share with active staff
        s2.save(flush:true, failOnError:true)
        updateInfo = [doShareActions:[
            [id:s2.phone.id, action:Constants.SHARE_ACTION_MERGE,
                permission:SharePermission.DELEGATE]
        ]]
        res = service.handleShareActions(tC2, updateInfo)
        tC2.save(flush:true, failOnError:true)

        then: "shared + sharing changes noted in record"
        res.success == true
        res.payload instanceof Contact
        SharedContact.findBySharedWithAndSharedByAndContactAndPermission(s2.phone,
            tC2.phone, tC2, SharePermission.DELEGATE) != null
        s2.phone.sharedWithMe.any { it.contact.id == tC2.id } == true
        t2.phone.sharedByMe.any { it.id == tC2.id } == true
        Contact.count() == cBaseline
        ContactNumber.count() == nBaseline
        SharedContact.count() == sBaseline + 2
        RecordNote.count() == noteBaseline + 2

        when: "we update permissions for the contact we just shared"
        updateInfo = [doShareActions:[
            [id:s2.phone.id, action:Constants.SHARE_ACTION_MERGE,
            permission:SharePermission.VIEW]
        ]]
        res = service.handleShareActions(tC2, updateInfo)
        tC2.save(flush:true, failOnError:true)

        then: "updated + sharing changes noted in record"
        res.success == true
        res.payload instanceof Contact
        SharedContact.findBySharedWithAndSharedByAndContactAndPermission(s2.phone,
            tC2.phone, tC2, SharePermission.DELEGATE) == null
        SharedContact.findBySharedWithAndSharedByAndContactAndPermission(s2.phone,
            tC2.phone, tC2, SharePermission.VIEW) != null
        s2.phone.sharedWithMe.any { it.contact.id == tC2.id } == true
        t2.phone.sharedByMe.any { it.id == tC2.id } == true
        Contact.count() == cBaseline
        ContactNumber.count() == nBaseline
        SharedContact.count() == sBaseline + 2
        RecordNote.count() == noteBaseline + 3

        when: "we stop sharing with view-only permissions"
        sc1.permission = SharePermission.VIEW
        sc1.save(flush:true, failOnError:true)

        updateInfo = [doShareActions:[
            [id:s2.phone.id, action:Constants.SHARE_ACTION_STOP]
        ]]
        res = service.handleShareActions(tC2, updateInfo, sc1)
        tC2.save(flush:true, failOnError:true)

        then: "ignored"
        res.success == true
        res.payload instanceof Contact
        SharedContact.findBySharedWithAndSharedByAndContactAndPermission(s2.phone,
            tC2.phone, tC2, SharePermission.VIEW) != null
        s2.phone.sharedWithMe.any { it.contact.id == tC2.id } == true
        t2.phone.sharedByMe.any { it.id == tC2.id } == true
        Contact.count() == cBaseline
        ContactNumber.count() == nBaseline
        SharedContact.count() == sBaseline + 2
        RecordNote.count() == noteBaseline + 3

        when: "try again with delegate permissions"
        sc1.permission = SharePermission.DELEGATE
        sc1.save(flush:true, failOnError:true)

        res = service.handleShareActions(tC2, updateInfo, sc1)
        tC2.save(flush:true, failOnError:true)

        then: "ignored"
        res.success == true
        res.payload instanceof Contact
        SharedContact.findBySharedWithAndSharedByAndContactAndPermission(s2.phone,
            tC2.phone, tC2, SharePermission.VIEW) != null
        s2.phone.sharedWithMe.any { it.contact.id == tC2.id } == true
        t2.phone.sharedByMe.any { it.id == tC2.id } == true
        Contact.count() == cBaseline
        ContactNumber.count() == nBaseline
        SharedContact.count() == sBaseline + 2
        RecordNote.count() == noteBaseline + 3

        when: "try again without any sharing"
        res = service.handleShareActions(tC2, updateInfo)
        tC2.save(flush:true, failOnError:true)

        then: "stopped sharing + sharing changes noted in record"
        res.success == true
        res.payload instanceof Contact
        SharedContact.findBySharedWithAndSharedByAndContactAndPermission(s2.phone,
            tC2.phone, tC2, SharePermission.VIEW) != null
        s2.phone.sharedWithMe.any { it.contact.id == tC2.id } == false
        t2.phone.sharedByMe.any { it.id == tC2.id } == false
        Contact.count() == cBaseline
        ContactNumber.count() == nBaseline
        SharedContact.count() == sBaseline + 2
        RecordNote.count() == noteBaseline + 4
    }

    void "test merge actions"() {
        given:
        service.duplicateService = [
            merge: { Contact targetContact, Collection<Contact> toMergeIn ->
                new Result(status:ResultStatus.OK, payload:targetContact)
            }
        ] as DuplicateService

        when: "invalid merge actions"
        Result<Contact> res = service.handleMergeActions(c1, [doMergeActions:"i am invalid"])

        then:
        res.status == ResultStatus.UNPROCESSABLE_ENTITY

        when: "merging with default settings"
        String name = c1.name,
            note = c1.note
        res = service.handleMergeActions(c1, [doMergeActions: [
            [action:Constants.MERGE_ACTION_DEFAULT, mergeIds:[c1_1.id]]
        ]])
        Contact.withSession { it.flush() }

        then: "success and merged-in contacts marked as deleted"
        res.status == ResultStatus.OK
        res.payload instanceof Contact
        res.payload.id == c1.id
        res.payload.name == name
        res.payload.note == note
        Contact.get(c1_1.id).isDeleted == true

        when: "merging and reconciling differences with view-only permissions"
        name = c1_2.name
        note = c1_2.note
        Map mergeAction = [doMergeActions: [
            [
                action:Constants.MERGE_ACTION_RECONCILE,
                mergeIds:[c1_2.id],
                nameId:c1_2.id,
                noteId:c1_2.id
            ]
        ]]

        sc1.permission = SharePermission.VIEW
        sc1.save(flush:true, failOnError:true)

        res = service.handleMergeActions(c1, mergeAction, sc1)
        Contact.withSession { it.flush() }

        then: "ignored"
        res.status == ResultStatus.OK
        res.payload instanceof Contact
        res.payload.id == c1.id
        Contact.get(c1_2.id).isDeleted == false

        when: "merging and reconciling differences with delegate permissions"
        sc1.permission = SharePermission.DELEGATE
        sc1.save(flush:true, failOnError:true)

        res = service.handleMergeActions(c1, mergeAction, sc1)
        Contact.withSession { it.flush() }

        then: "ignored"
        res.status == ResultStatus.OK
        res.payload instanceof Contact
        res.payload.id == c1.id
        Contact.get(c1_2.id).isDeleted == false

        when: "try again without any sharing"
        res = service.handleMergeActions(c1, mergeAction)
        Contact.withSession { it.flush() }

        then: "success and merged-in contacts marked as deleted"
        res.status == ResultStatus.OK
        res.payload instanceof Contact
        res.payload.id == c1.id
        res.payload.name == name
        res.payload.note == note
        Contact.get(c1_2.id).isDeleted == true
    }

    // Delete
    // ------

    void "test delete"() {
        given:
        service.futureMessageJobService = Stub(FutureMessageJobService) {
            cancelAll(*_) >> new ResultGroup()
        }

        when: "deleting nonexistent contact"
        Result<Void> res = service.delete(-88L)

        then:
        res.status == ResultStatus.NOT_FOUND
        res.errorMessages.size() == 1
        res.errorMessages[0] == "contactService.delete.notFound"

        when: "deleting existing contact"
        assert c1.isDeleted == false
        res = service.delete(c1.id)
        Contact.withSession { it.flush() }

        then:
        res.status == ResultStatus.NO_CONTENT
        res.payload == null
        Contact.get(c1.id).isDeleted == true
    }
}
