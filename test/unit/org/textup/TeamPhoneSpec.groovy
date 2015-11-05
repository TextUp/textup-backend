package org.textup

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import grails.validation.ValidationErrors
import org.springframework.context.MessageSource
import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

@Domain([TagMembership, Contact, Phone, ContactTag, 
	ContactNumber, Record, RecordItem, RecordNote, RecordText, 
	RecordCall, RecordItemReceipt, PhoneNumber, SharedContact, 
	TeamMembership, StaffPhone, Staff, Team, Organization, 
	Schedule, Location, TeamPhone, TeamContactTag])
@TestMixin(HibernateTestMixin)
@Unroll
class TeamPhoneSpec extends Specification {

	static doWithSpring = {
		resultFactory(ResultFactory)
	}
	def setup() {
		ResultFactory fac = grailsApplication.mainContext.getBean("resultFactory")
		fac.messageSource = [getMessage:{ String c, Object[] p, Locale l -> c }] as MessageSource
	}
	private ResultFactory getResultFactory() {
		grailsApplication.mainContext.getBean("resultFactory")
	}

    void "test constraints and deletion"() {
    	when: "we have a phone without a number"
    	TeamPhone p = new TeamPhone()

    	then:
    	p.validate() == false
    	p.errors.errorCount == 1

    	when: "we have a phone with a unique number" 
    	p.numberAsString = "8223334444"

    	then: 
    	p.validate() == true 

    	when: "we try to add a phone with a duplicate number"
    	p.save(flush:true)
    	p = new TeamPhone()
    	p.numberAsString = "8223334444"

    	then: 
    	p.validate() == false
    	p.errors.errorCount == 1

    	when: "we add a phone with a unique number"
    	p.numberAsString = "8223334445"

    	then: 
    	p.validate() == true 

    	when: "we add associated classes WITHOUT ContactNumbers, then delete the phone"
    	p.save(flush:true)
    	TeamContactTag t1 = new TeamContactTag(phone:p, name:"tag1"), 
    		t2 = new TeamContactTag(phone:p, name:"tag2")
    	assert t1.save(flush:true) && t2.save(flush:true)
    	Contact c1 = new Contact(phone:p), c2 = new Contact(phone:p)
    	assert c1.save(flush:true) && c2.save(flush:true)
    	assert (new TagMembership(tag:t1, contact:c1)).save(flush:true)
    	assert (new TagMembership(tag:t2, contact:c1)).save(flush:true)

    	int tBaseline = TeamContactTag.count(), cBaseline = Contact.count(), 
    		mBaseline = TagMembership.count(), pBaseline = TeamPhone.count(), 
    		rBaseline = Record.count()
	    p.delete(flush:true)

    	then: 
    	TeamContactTag.count() == tBaseline - 2
		Contact.count() == cBaseline - 2
		TagMembership.count() == mBaseline - 2
		TeamPhone.count() == pBaseline - 1
		Record.count() == rBaseline - 4
    }

    void "test operations on tags"() {
    	given: "a team phone"
    	TeamPhone p = new TeamPhone()
		p.numberAsString = "8223334446"
		p.resultFactory = getResultFactory()
		p.save(flush:true)

    	when: "we create a unique tag"
    	int baseline = TeamContactTag.count() 
    	Result res = p.createTag(name:"tag1")
    	assert res.success
    	p.save(flush:true)

    	then: 
    	res.payload.instanceOf(TeamContactTag)
    	TeamContactTag.count() == baseline + 1

    	when: "we create a duplicate tag"
    	res = p.createTag(name:"tag1")

    	then: 
    	res.success == false 
    	res.payload instanceof ValidationErrors
    	res.payload.errorCount == 1
    }
}
