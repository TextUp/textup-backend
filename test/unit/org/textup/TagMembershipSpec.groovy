package org.textup

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

@Domain([TagMembership, Contact, Phone, ContactTag,
	Record, RecordItem, RecordNote, RecordText, RecordCall, 
	RecordItemReceipt, PhoneNumber, ContactNumber])
@TestMixin(HibernateTestMixin)
@Unroll
class TagMembershipSpec extends Specification {

    void "test constraints"() {
    	given:
    	Phone p = new Phone()
    	p.numberAsString = "222 333 4444"
    	p.save(flush:true)
    	Contact c = new Contact(phone:p)
    	c.save(flush:true)
    	ContactTag t = new ContactTag(phone:p, name:"tag1")
    	t.save(flush:true)

    	Phone p2 = new Phone()
    	p2.numberAsString = "222 333 4445"
    	p2.save(flush:true)
    	Contact c2 = new Contact(phone:p2)
    	c2.save(flush:true)

    	when: "we have a TagMembership with contact and tag from different phones"
    	TagMembership m = new TagMembership(tag:t, contact:c2)

    	then: 
    	m.validate() == false 
    	m.errors.errorCount == 1

    	when: "we have a TagMembership with contact and tag from same phone"
    	m = new TagMembership(tag:t, contact:c)

    	then: 
    	m.validate() == true 
    }

    void "test subscribing and unsubscribing and deletion"() {
    	when: "we have a TagMembership"
    	Phone p = new Phone(number:new PhoneNumber(number:"222 333 4446"))
    	p.save(flush:true)
    	Contact c = new Contact(phone:p, record:new Record())
    	c.save(flush:true)
    	ContactTag t = new ContactTag(phone:p, name:"tag1")
    	t.save(flush:true)
	    TagMembership m = new TagMembership(tag:t, contact:c)
	    assert m.save(flush:true)

    	then: "default is subscribed"
    	m.subscribed == true 
    	m.hasUnsubscribed == false 

    	when: "we unsubscribe"
    	m.unsubscribe() 
    	m.save(flush:true)

    	then:
    	m.subscribed == false 
    	m.hasUnsubscribed == true  

    	when: "we subscribe"
    	m.subscribe() 
    	m.save(flush:true)

    	then: 
    	m.subscribed == true 
    	m.hasUnsubscribed == false 

    	when: "we delete this membership"
    	int baseline = TagMembership.count() 
    	m.delete(flush:true)

    	then: 
    	TagMembership.count() == baseline - 1
    }
}
