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
class ContactTagSpec extends Specification {

    void "test constraints and deletion"() {
    	given: "a phone and a ContactTag"
    	Phone p = new Phone()
    	p.numberAsString = "322 333 4444"
    	p.save(flush:true)
		assert (new ContactTag(phone:p, name:"tag1")).save(flush:true)

    	when: "we add a tag with a duplicate name"
	   	ContactTag t = new ContactTag(phone:p, name:"tag1")

    	then: 
    	t.validate() == false 
    	t.errors.errorCount == 1

    	when: "we add a tag with a unique name"
    	t.name = "tag2"

    	then: 
    	t.validate() == true 

    	when: "we delete the new tag"
    	t.save(flush:true)
    	int baseline = ContactTag.count()
    	t.delete(flush:true)

    	then: 
    	ContactTag.count() == baseline - 1
    }

    void "test retrieving members"() {
    	given: "a phone, a ContactTag with several members"
    	Phone p = new Phone()
    	p.numberAsString = "322 333 4445"
    	p.save(flush:true)
		ContactTag t = new ContactTag(phone:p, name:"tag1")
    	t.save(flush:true)

    	int numSubscribers = 10, numNonsubscribers = 5, 
    		baseline = TagMembership.count()
    	List<TagMembership> subs = [], nonSubs = []
    	numSubscribers.times {
    		Contact c = new Contact(phone:p)
	    	c.save(flush:true)
	    	TagMembership m = new TagMembership(contact:c, tag:t)
	    	m.save(flush:true)
	    	subs << m
    	}
    	numNonsubscribers.times {
    		Contact c = new Contact(phone:p)
	    	c.save(flush:true)
	    	TagMembership m = new TagMembership(contact:c, tag:t, hasUnsubscribed:true)
	    	m.save(flush:true)
	    	nonSubs << m
    	}
    	
    	expect: 
    	TagMembership.count() == baseline + numSubscribers + numNonsubscribers
    	t.countAllMembers() == numSubscribers + numNonsubscribers
    	t.countSubscribers() == numSubscribers
		t.countNonsubscribers() == numNonsubscribers

		t.subscribers.each { TagMembership s1 ->
			assert subs.find { s2 -> s1 == s2  }
		}
		t.nonsubscribers.each { TagMembership n1 ->
			assert nonSubs.find { TagMembership n2 -> n1 == n2  }
		}
		t.allMembers.each { TagMembership m ->
			assert subs.find { TagMembership s -> s == m  } ||
				nonSubs.find { TagMembership n -> n == m  }
		}
    }
}
