package org.textup

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestFor
import grails.test.mixin.TestMixin
import grails.validation.ValidationErrors
import org.springframework.context.MessageSource
import org.textup.types.ResultType
import org.textup.types.ContactStatus
import org.textup.types.StaffStatus
import org.textup.types.SharePermission
import org.textup.util.CustomSpec
import spock.lang.Shared
import spock.lang.Specification
import static org.springframework.http.HttpStatus.*

@TestFor(ContactService)
@Domain([Contact, Phone, ContactTag, ContactNumber, Record, RecordItem, RecordText,
  RecordCall, RecordItemReceipt, SharedContact, Staff, Team, Organization,
  Schedule, Location, WeeklySchedule, PhoneOwnership])
@TestMixin(HibernateTestMixin)
class ContactServiceSpec extends CustomSpec {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }
    def setup() {
        super.setupData()
        service.resultFactory = getResultFactory()
    }
    def cleanup() {
        super.cleanupData()
    }

    // Create
    // ------

    void "test validate number actions"() {
        when: "we create with number actions that isn't a list"
        Result res = service.validateNumberActions("I am not a list")

        then:
        res.success == false
        res.type == ResultType.MESSAGE_STATUS
        res.payload.code == "contactService.numberActionNotList"
        res.payload.status == BAD_REQUEST

        when: "we create with number actions that defines an unspecified action"
        List doNumberActions = [[number:"12223334444", preference:0, action:"invalid"]]
        res = service.validateNumberActions(doNumberActions)

        then:
        res.success == false
        res.type == ResultType.MESSAGE_STATUS
        res.payload.code == "contactService.numberActionInvalid"
        res.payload.status == BAD_REQUEST
    }

    void "test create"() {
        given: "baselines"
        int cBaseline = Contact.count()
        int nBaseline = ContactNumber.count()

    	when: "we create with a null phone"
    	Result<Contact> res = service.create(null, [:])

    	then:
    	res.success == false
    	res.type == ResultType.MESSAGE_STATUS
    	res.payload.code == "contactService.create.noPhone"
    	res.payload.status == UNPROCESSABLE_ENTITY
        Contact.count() == cBaseline
        ContactNumber.count() == nBaseline

    	when: "we create with valid input that is a mix of add and delete number actions"
    	String num1 = "2223334444", num2 = "2223334445"
    	Map contactInfo = [doNumberActions:[
    		[number:num1, preference:0, action:Constants.NUMBER_ACTION_MERGE],
    		[number:num2, preference:2, action:Constants.NUMBER_ACTION_MERGE],
    		[number:"12223334443", action:Constants.NUMBER_ACTION_DELETE]
		]]
    	res = service.create(s1.phone, contactInfo)
    	assert res.success
        s1.phone.save(flush:true, failOnError:true)

    	then: "delete number actions are ignored"
    	res.payload instanceof Contact
    	res.payload.numbers.size() == 2
    	res.payload.numbers[0].number == num1
    	res.payload.numbers[1].number == num2
        Contact.count() == cBaseline + 1
        ContactNumber.count() == nBaseline + 2
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
        res.type == ResultType.MESSAGE_STATUS
        res.payload.code == "contactService.update.notFound"
        res.payload.status == NOT_FOUND
        Contact.count() == cBaseline
        ContactNumber.count() == nBaseline

        when: "we successfully update a contact"
        String name = "kiki bai"
        res = service.update(c1.id, [name:name])

        then:
        res.success == true
        res.payload instanceof Contact
        res.payload.name == name
        Contact.count() == cBaseline
        ContactNumber.count() == nBaseline
    }

    void "test update contact info"() {
        given: "baselines"
        int cBaseline = Contact.count()
        int nBaseline = ContactNumber.count()

        when: "we update with invalid fields"
        Map updateInfo = [
            name: "non marie",
            note: "you are awesome. keep up the good work!",
            status:"invalid"
        ]
        Result res = service.updateContactInfo(c1, updateInfo)

        then:
        res.success == false
        res.type == ResultType.VALIDATION
        res.payload.errorCount == 1
        Contact.count() == cBaseline
        ContactNumber.count() == nBaseline

        when: "we update with valid fields"
        updateInfo.status = "uNrEAd"
        res = service.updateContactInfo(c1, updateInfo)

        then:
        res.success == true
        res.payload instanceof Contact
        res.payload.name == updateInfo.name
        res.payload.note == updateInfo.note
        res.payload.status == ContactStatus.UNREAD
        Contact.count() == cBaseline
        ContactNumber.count() == nBaseline
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
        res.type == ResultType.MESSAGE
        res.payload.code == "contact.numberNotFound"
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
        res.payload instanceof Contact
        res.payload.numbers.size() == 2
        res.payload.numbers[0].number == num1
        res.payload.numbers[1].number == num2
        Contact.count() == cBaseline
        ContactNumber.count() == nBaseline - numOriginalNums + 2

        when: "we delete one of the numbers we just added"
        numActions = [doNumberActions:[
            [number:num1, action:Constants.NUMBER_ACTION_DELETE]
        ]]
        res = service.handleNumberActions(c1, numActions)
        c1.save(flush:true, failOnError:true)

        then:
        res.success == true
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
        res.type == ResultType.MESSAGE_STATUS
        res.payload.code == "contactService.update.shareActionNotList"
        res.payload.status == BAD_REQUEST
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
        res.type == ResultType.MESSAGE_STATUS
        res.payload.code == "contactService.update.shareActionInvalid"
        res.payload.status == BAD_REQUEST
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
        res.type == ResultType.MESSAGE_STATUS
        res.payload.code == "contactService.update.phoneNotFound"
        res.payload.status == NOT_FOUND
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
        res.type == ResultType.MESSAGE_STATUS
        res.payload.code == "phone.share.cannotShare"
        res.payload.status == FORBIDDEN
        Contact.count() == cBaseline
        ContactNumber.count() == nBaseline
        SharedContact.count() == sBaseline
    }

    void "test share actions"() {
        given: "baselines"
        int cBaseline = Contact.count()
        int nBaseline = ContactNumber.count()
        int sBaseline = SharedContact.count()

        when: "we try to share a team's contacts"
        Map updateInfo = [doShareActions:[
            [id:t1.phone.id, action:Constants.SHARE_ACTION_MERGE,
                permission:SharePermission.DELEGATE]
        ]]
        Result res = service.handleShareActions(c1, updateInfo)
        c1.save(flush:true, failOnError:true)

        then:
        res.success == true
        res.payload instanceof Contact
        SharedContact.findBySharedWithAndSharedByAndContactAndPermission(t1.phone,
            c1.phone, c1, SharePermission.DELEGATE) != null
        t1.phone.sharedWithMe.any { it.contact.id == c1.id } == true
        s1.phone.sharedByMe.any { it.id == c1.id } == true
        Contact.count() == cBaseline
        ContactNumber.count() == nBaseline
        SharedContact.count() == sBaseline + 1

        when: "we try to share with a staff member on the same team as us"
        s2.status = StaffStatus.STAFF // can only share with active staff
        s2.save(flush:true, failOnError:true)
        updateInfo = [doShareActions:[
            [id:s2.phone.id, action:Constants.SHARE_ACTION_MERGE,
                permission:SharePermission.DELEGATE]
        ]]
        res = service.handleShareActions(tC2, updateInfo)
        tC2.save(flush:true, failOnError:true)

        then:
        res.success == true
        res.payload instanceof Contact
        SharedContact.findBySharedWithAndSharedByAndContactAndPermission(s2.phone,
            tC2.phone, tC2, SharePermission.DELEGATE) != null
        s2.phone.sharedWithMe.any { it.contact.id == tC2.id } == true
        t2.phone.sharedByMe.any { it.id == tC2.id } == true
        Contact.count() == cBaseline
        ContactNumber.count() == nBaseline
        SharedContact.count() == sBaseline + 2

        when: "we update permissions for the contact we just shared"
        updateInfo = [doShareActions:[
            [id:s2.phone.id, action:Constants.SHARE_ACTION_MERGE,
            permission:SharePermission.VIEW]
        ]]
        res = service.handleShareActions(tC2, updateInfo)
        tC2.save(flush:true, failOnError:true)

        then:
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

        when: "we stop sharing"
        updateInfo = [doShareActions:[
            [id:s2.phone.id, action:Constants.SHARE_ACTION_STOP]
        ]]
        res = service.handleShareActions(tC2, updateInfo)
        tC2.save(flush:true, failOnError:true)

        then:
        res.success == true
        res.payload instanceof Contact
        SharedContact.findBySharedWithAndSharedByAndContactAndPermission(s2.phone,
            tC2.phone, tC2, SharePermission.VIEW) != null
        s2.phone.sharedWithMe.any { it.contact.id == tC2.id } == false
        t2.phone.sharedByMe.any { it.id == tC2.id } == false
        Contact.count() == cBaseline
        ContactNumber.count() == nBaseline
        SharedContact.count() == sBaseline + 2
    }
}
