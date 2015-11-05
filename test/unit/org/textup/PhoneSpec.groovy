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
	Schedule, Location, TeamPhone])
@TestMixin(HibernateTestMixin)
@Unroll
class PhoneSpec extends Specification {

	static doWithSpring = {
		resultFactory(ResultFactory)
	}
	def setup() {
		ResultFactory fac = getResultFactory()
		fac.messageSource = [getMessage:{ String code, 
			Object[] parameters, Locale locale -> code }] as MessageSource
	}
	private ResultFactory getResultFactory() {
		grailsApplication.mainContext.getBean("resultFactory")
	}

    void "test constraints and deletion"() {
    	when: "we have a phone without a number"
    	Phone p = new Phone()

    	then:
    	p.validate() == false
    	p.errors.errorCount == 1

    	when: "we have a phone with a unique number" 
    	p.numberAsString = "5223334444"

    	then: 
    	p.validate() == true 

    	when: "we try to add a phone with a duplicate number"
    	p.save(flush:true)
    	p = new Phone()
    	p.numberAsString = "5223334444"

    	then: 
    	p.validate() == false
    	p.errors.errorCount == 1

    	when: "we add a phone with a unique number"
    	p.numberAsString = "5223334445"

    	then: 
    	p.validate() == true 

    	when: "we add associated classes WITHOUT ContactNumbers, then delete the phone"
    	p.save(flush:true)
    	ContactTag t1 = new ContactTag(phone:p, name:"tag1"), 
    		t2 = new ContactTag(phone:p, name:"tag2")
    	assert t1.save(flush:true) && t2.save(flush:true)
    	Contact c1 = new Contact(phone:p), c2 = new Contact(phone:p)
    	assert c1.save(flush:true) && c2.save(flush:true)
    	assert (new TagMembership(tag:t1, contact:c1)).save(flush:true)
    	assert (new TagMembership(tag:t2, contact:c1)).save(flush:true)

    	int tBaseline = ContactTag.count(), cBaseline = Contact.count(), 
    		mBaseline = TagMembership.count(), pBaseline = Phone.count(), 
    		rBaseline = Record.count()
	    p.delete(flush:true)

    	then: 
    	ContactTag.count() == tBaseline - 2
		Contact.count() == cBaseline - 2
		TagMembership.count() == mBaseline - 2
		Phone.count() == pBaseline - 1
		Record.count() == rBaseline - 2
    }

    @Ignore
    void "test scheduled texts"() {
    }

    void "test operations on tags"() {
    	given: 
    	Phone p = new Phone()
    	p.resultFactory = getResultFactory()
    	p.numberAsString = "5223334447"
    	p.save(flush:true, failOnError:true)

    	when: "we add a tag with unique name"
    	assert p.createTag(name:"tag1").success
    	p.save(flush:true, failOnError:true)

    	then:
    	p.tags.size() == 1

    	when: "we add a tag with a duplicate name"
    	Result res = p.createTag(name:"tag1")

    	then: 
    	res.success == false 
    	res.payload instanceof ValidationErrors
    	res.payload.errorCount == 1

    	when: "we change to a unique name"
    	res = p.createTag(name:"tag2")

    	then: 
    	res.success == true

    	when: "we delete a tag"
    	res.payload.save(flush:true, failOnError:true)
    	int baseline = ContactTag.count()
    	assert p.deleteTag("tag1").success
    	p.save(flush:true, failOnError:true)
    	def tags = p.tags

    	then:
    	ContactTag.count() == baseline - 1
    	tags.size() == 1
    	tags[0].name == "tag2"

    	when: "we delete a nonexistent tag"
    	res = p.deleteTag("nonexistent")

    	then:
    	res.success == false 
    	res.payload instanceof Map 
    	res.payload.code == "phone.error.tagNotFound"

    	when: "we delete a tag belonging to another phone"
    	Phone p2 = new Phone()
    	p2.resultFactory = getResultFactory()
    	p2.numberAsString = "5223334448"
    	p2.save(flush:true, failOnError:true)
    	res = p2.createTag(name:"tag1")
    	assert res.success
    	ContactTag diffTag = res.payload
    	p2.save(flush:true, failOnError:true)

    	res = p.deleteTag(diffTag)

    	then: 
    	res.success == false 
    	res.payload instanceof Map 
    	res.payload.code == "phone.error.tagOwnership"
    }
}
