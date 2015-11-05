package org.textup

import grails.plugin.springsecurity.SpringSecurityService
import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestFor
import grails.test.mixin.TestMixin
import grails.validation.ValidationErrors
import org.springframework.context.MessageSource
import org.textup.util.CustomSpec
import spock.lang.Shared
import spock.lang.Specification
import static org.springframework.http.HttpStatus.*

@TestFor(ContactService)
@Domain([TagMembership, Contact, Phone, ContactTag, 
	ContactNumber, Record, RecordItem, RecordNote, RecordText, 
	RecordCall, RecordItemReceipt, PhoneNumber, SharedContact, 
	TeamMembership, StaffPhone, Staff, Team, Organization, 
	Schedule, Location, TeamPhone, WeeklySchedule, TeamContactTag])
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
    
    void "test create"() {
    	when: "we create with an invalid id"
    	Result res = service.create(Staff, -88L, [:])

    	then: 
    	res.success == false 
    	res.type == Constants.RESULT_MESSAGE_STATUS
    	res.payload.code == "contactService.create.noPhone"
    	res.payload.status == UNPROCESSABLE_ENTITY

    	when: "we create with number actions that isn't a list"
    	Map contactInfo = [doNumberActions:"I am not a list"]
    	res = service.create(Staff, s1.id, contactInfo)

    	then: 
    	res.success == false 
    	res.type == Constants.RESULT_MESSAGE_STATUS
    	res.payload.code == "contactService.error.numberActionNotList"
    	res.payload.status == BAD_REQUEST

    	when: "we create with number actions that defines an unspecified action"
    	contactInfo = [doNumberActions:[[number:"12223334444", preference:0, action:"invalid"]]]
    	res = service.create(Staff, s1.id, contactInfo)

    	then: 
    	res.success == false 
    	res.type == Constants.RESULT_MESSAGE_STATUS
    	res.payload.code == "contactService.error.numberActionInvalid"
    	res.payload.status == BAD_REQUEST

    	when: "we create with valid input that is a mix of add and delete number actions"
    	String num1 = "2223334444", num2 = "2223334445"
    	contactInfo = [doNumberActions:[
    		[number:num1, preference:0, action:Constants.NUMBER_ACTION_MERGE], 
    		[number:num2, preference:2, action:Constants.NUMBER_ACTION_MERGE], 
    		[number:"12223334443", action:Constants.NUMBER_ACTION_DELETE]
		]]
    	int baseline = Contact.count()
    	res = service.create(Staff, s1.id, contactInfo)
    	assert res.success == true 

    	then: "delete number actions are ignored"
    	Contact.count() == baseline + 1
    	res.payload instanceof Contact
    	res.payload.numbers.size() == 2
    	res.payload.numbers[0].number == num1
    	res.payload.numbers[1].number == num2
    }

    void "test update with number actions"() {
    	when: "we try to update a nonexistent contact"
    	Result res = service.update(-88L, [:])

    	then: 
    	res.success == false 
    	res.type == Constants.RESULT_MESSAGE_STATUS
    	res.payload.code == "contactService.update.notFound"
    	res.payload.status == NOT_FOUND

    	when: "we try to delete nonexistent number"
    	Map updateInfo = [doNumberActions:[
    		[number:"12223334443", action:Constants.NUMBER_ACTION_DELETE]
		]]
    	res = service.update(c1.id, updateInfo)

    	then:
    	res.success == false 
    	res.type == Constants.RESULT_MESSAGE
    	res.payload.code == "contact.error.numberNotFound"

    	when: "we update with valid number actions"
    	c1.deleteNumber(c1.numbers[0].number)
    	assert c1.numbers.size() == 0 //c1 starts with no numbers
    	String num1 = "2223334444", num2 = "2223334445"
    	updateInfo = [doNumberActions:[
    		[number:num1, preference:0, action:Constants.NUMBER_ACTION_MERGE], 
    		[number:num2, preference:2, action:Constants.NUMBER_ACTION_MERGE], 
		]]
    	res = service.update(c1.id, updateInfo)

    	then:
    	res.success == true
    	res.payload instanceof Contact
    	res.payload.numbers.size() == 2
    	res.payload.numbers[0].number == num1
    	res.payload.numbers[1].number == num2

    	when: "we delete one of the numbers we just added"
    	updateInfo = [doNumberActions:[
    		[number:num1, action:Constants.NUMBER_ACTION_DELETE]
		]]
    	res = service.update(c1.id, updateInfo)

    	then:
    	res.success == true
    	res.payload instanceof Contact
    	res.payload.numbers.size() == 1
    	res.payload.numbers[0].number == num2

    	//This needs to be the last because it's too involved 
    	//to roll back the invalid value after setting it
    	when: "we update with invalid fields" 
    	updateInfo = [status:"invalid"]
    	res = service.update(c1.id, updateInfo)

    	then:
    	res.success == false 
    	res.type == Constants.RESULT_VALIDATION
    	res.payload.errorCount == 1
    }

    void "test update and share"() {
    	given: 
    	HashSet<Long> cannotShareStaffIds = []
    	service.authService = [canShareContactWithStaff:{ Long cId, Long sId ->
        	!cannotShareStaffIds.contains(sId)
        }] as AuthService

    	when: "we update with share actions that aren't a list"
    	Map updateInfo = [doShareActions:"I am not a list"]
    	Result res = service.update(c1.id, updateInfo)

    	then: 
    	res.success == false 
    	res.type == Constants.RESULT_MESSAGE_STATUS
    	res.payload.code == "contactService.update.shareActionNotList"
    	res.payload.status == BAD_REQUEST

    	when: "we try to update with unspecified share actions"
    	updateInfo = [doShareActions:[
    		[id:s2.id, action:"invalid", permission:Constants.SHARED_DELEGATE]
		]]
		res = service.update(c1.id, updateInfo)

    	then:
    	res.success == false 
    	res.type == Constants.RESULT_MESSAGE_STATUS
    	res.payload.code == "contactService.update.shareActionInvalid"
    	res.payload.status == BAD_REQUEST

    	when: "we try to share with nonexistent staff member"
    	updateInfo = [doShareActions:[
    		[id:-88L, action:Constants.SHARE_ACTION_MERGE, permission:Constants.SHARED_DELEGATE]
		]]
		res = service.update(c1.id, updateInfo)

    	then: 
    	res.success == false 
    	res.type == Constants.RESULT_MESSAGE_STATUS
    	res.payload.code == "contactService.update.staffNotFound"
    	res.payload.status == NOT_FOUND

    	when: "we try to share a team's contacts"
    	updateInfo = [doShareActions:[
    		[id:s1.id, action:Constants.SHARE_ACTION_MERGE, permission:Constants.SHARED_DELEGATE]
		]]
		res = service.update(tC1.id, updateInfo)

    	then: 
    	res.success == false 
    	res.type == Constants.RESULT_MESSAGE_STATUS
    	res.payload.code == "contactService.update.cannotShareFromTeam"
    	res.payload.status == BAD_REQUEST

    	when: "we try to share with staff member we can't share with (on a different team)"
    	cannotShareStaffIds << s3.id
    	updateInfo = [doShareActions:[
    		[id:s3.id, action:Constants.SHARE_ACTION_MERGE, permission:Constants.SHARED_DELEGATE]
		]]
		res = service.update(c1.id, updateInfo)

    	then: 
    	res.success == false 
    	res.type == Constants.RESULT_MESSAGE_STATUS
    	res.payload.code == "contactService.update.shareDifferentTeam"
    	res.payload.status == FORBIDDEN

    	when: "we try to share with a staff member on the same team as us"
    	//we add s3 to same team t1 as s1
    	cannotShareStaffIds -= s3.id
    	(new TeamMembership(staff:s3, team:t1)).save(flush:true, failOnError:true)
    	t1.save(flush:true, failOnError:true)
    	int baseline = SharedContact.count()
    	res = service.update(c1.id, updateInfo)
    	assert res.success 
    	s1.save(flush:true, failOnError:true)
    	SharedContact sc = SharedContact.findByContactAndSharedWith(c1, s3.phone)
    	assert sc != null

    	then: 
    	SharedContact.count() == baseline + 1
    	res.payload instanceof Contact
    	res.payload.id == c1.id 
    	sc.dateExpired == null
    	sc.permission == Constants.SHARED_DELEGATE

    	when: "we update permissions for the contact we just shared"
    	baseline = SharedContact.count()
    	updateInfo = [doShareActions:[
    		[id:s3.id, action:Constants.SHARE_ACTION_MERGE, permission:Constants.SHARED_VIEW]
		]]
    	res = service.update(c1.id, updateInfo)
    	assert res.success 
    	s1.save(flush:true, failOnError:true)
    	sc = SharedContact.findByContactAndSharedWith(c1, s3.phone)
    	assert sc != null

    	then:
    	SharedContact.count() == baseline
    	res.payload instanceof Contact
    	res.payload.id == c1.id 
    	sc.dateExpired == null
    	sc.permission == Constants.SHARED_VIEW

    	when: "we stop sharing"
    	updateInfo = [doShareActions:[
    		[id:s3.id, action:Constants.SHARE_ACTION_STOP]
		]]
    	res = service.update(c1.id, updateInfo)
    	assert res.success 
    	s1.save(flush:true, failOnError:true)
    	sc = SharedContact.findByContactAndSharedWith(c1, s3.phone)
    	assert sc != null

    	then:
    	SharedContact.count() == baseline
    	res.payload instanceof Contact
    	res.payload.id == c1.id 
    	sc.dateExpired != null
    	sc.permission == Constants.SHARED_VIEW
    }

    void "test delete"() {
    	when: "we delete a nonexistent contact"
    	Result res = service.delete(-88L)

    	then: 
    	res.success == false 
    	res.type == Constants.RESULT_MESSAGE_STATUS
    	res.payload.code == "contactService.delete.notFound"
    	res.payload.status == NOT_FOUND

    	when: "we delete a contact"
    	int baseline = Contact.count() 
    	res = service.delete(c1.id)
    	assert res.success
    	s1.save(flush:true, failOnError:true)

    	then: 
    	Contact.count() == baseline - 1
    }
}
