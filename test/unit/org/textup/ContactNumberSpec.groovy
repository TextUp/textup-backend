package org.textup

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
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
class ContactNumberSpec extends Specification {

    void "test no duplicate numbers for one contact, autoincrement preference"() {
    	given: 
    	Phone p = new Phone()
    	p.numberAsString = "122 333 4444"
    	p.save(flush:true)
		Contact c = new Contact(phone:p)
		c.save(flush:true)

    	when: "we try to add a unique number"
    	String num = "123 434 9230"
    	ContactNumber cn = new ContactNumber(number:num)
		cn.contact = c

    	then:
    	cn.validate() == true 
    	cn.preference == 0

    	when: "we try to add a duplicate number"
    	cn.save(flush:true)

    	cn = new ContactNumber(number:num)
    	cn.contact = c

    	then: 
    	cn.validate() == false 
    	cn.preference == 1

    	when: "we try to add another unique number"
    	cn.number = "123 439 0980"
    	cn.contact = c

    	then:
    	cn.validate() == true 
    	cn.preference == 1

		when: "we try to add another unique number"
    	cn.save(flush:true)
    	cn = new ContactNumber(number:"123 439 0981")
    	cn.contact = c 

    	then:
    	cn.validate() == true 
    	cn.preference == 2 	
    }
}
