package org.textup

import grails.test.spock.IntegrationSpec
import grails.validation.ValidationErrors

class ContactIntegrationSpec extends IntegrationSpec {

	void "test deletion"() {
    	given: 
    	Phone p = new Phone()
    	p.numberAsString = "422 333 4444"
    	p.save(flush:true)
    	ContactTag t = new ContactTag(phone:p, name:"tag1")
    	t.save(flush:true)
    	Contact c = new Contact(phone:p)
    	c.save(flush:true)
    	assert (new TagMembership(tag:t, contact:c)).save(flush:true)

    	when: "we add some numbers"
    	assert c.mergeNumber("222 333 4448").success
    	assert c.mergeNumber("322 333 4448").success
    	assert c.mergeNumber("422 333 4448").success
    	assert c.mergeNumber("522 333 4448").success

    	then:
    	c.numbers.size() == 4

    	when: "we delete the contact"
    	int nBaseline = ContactNumber.count(), 
    		mBaseline = TagMembership.count(), 
    		rBaseline = Record.count(), 
    		cBaseline = Contact.count()
    	c.delete(flush:true) 

    	then: 
    	ContactNumber.count() == nBaseline - 4
    	TagMembership.count() == mBaseline - 1
    	Record.count() == rBaseline - 1
    	Contact.count() == cBaseline - 1
    }
    
    void "test methods related to contact phone number"() {
    	given: 
    	Phone p = new Phone()
    	p.numberAsString = "422 333 4447"
    	p.save(flush:true)
    	Contact c = new Contact(phone:p)
    	c.save(flush:true)

    	when: "add numbers"
    	String n1 = "2223334448", 
    		n2 = "3223334448", 
    		n3 = "4223334448", 
    		n4 = "5223334448"
    	assert c.mergeNumber(n1).success
    	assert c.mergeNumber(n2).success
    	assert c.mergeNumber(n3).success
    	assert c.mergeNumber(n4).success
    	c.save(flush:true)
    	def nums = c.numbers 

    	then: 
    	nums.size() == 4
    	nums[0].number == n1
    	nums[1].number == n2
    	nums[2].number == n3
    	nums[3].number == n4

    	when: "delete a number"
    	c.deleteNumber(n3)
    	c.save(flush:true)
        nums = c.numbers

    	then: 
    	nums.size() == 3
    	nums[0].number == n1
    	nums[1].number == n2
    	nums[2].number == n4

    	when: "delete nonexistent number"
    	Result res = c.deleteNumber("123 456 7890")

    	then: 
    	res.success == false 
    	res.payload instanceof Map
    	res.payload.code == "contact.error.numberNotFound"

    	when: "add invalid number"
    	res = c.mergeNumber("1232")

    	then: 
    	res.success == false 
    	res.payload instanceof ValidationErrors
    	res.payload.errorCount == 1
    }
}
