package org.textup

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
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
class ContactSpec extends Specification {

	static doWithSpring = {
		resultFactory(ResultFactory)
	}
	def setup() {
		ResultFactory f = getResultFactory()
		f.messageSource = [getMessage:{ String c, Object[] p, Locale l -> c }] as MessageSource
	}
    private ResultFactory getResultFactory() {
        grailsApplication.mainContext.getBean("resultFactory")
    }

	void "test constraints and deletion"() {
    	given: 
    	Phone p = new Phone()
    	p.numberAsString = "422 333 4444"
    	p.save(flush:true)
    	ContactTag t = new ContactTag(phone:p, name:"tag1")
    	t.save(flush:true)

    	when: "we have a contact with only a phone defined"
    	Contact c = new Contact(phone:p)
    	c.resultFactory = getResultFactory()

    	then: "an empty record is automatically added"
    	c.validate() == true 
    	c.record != null

    	when: "we add a tag membership and define a too-long note"
    	c.save(flush:true)
    	assert (new TagMembership(tag:t, contact:c)).save(flush:true)

    	c.note = '''
			Far far away, behind the word mountains, far from the countries Vokalia and 
			Consonantia, there live the blind texts. Separated they live in Bookmarksgrove 
			right at the coast of the Semantics, a large language ocean. A small river named 
			Duden flows by their place and supplies it with the necessary regelialia. It is a 
			paradisematic country, in which roasted parts of sentences fly into your mouth. 
			Even the all-powerful Pointing has no control about the blind texts it is an almost 
			unorthographic life One day however a small line of blind text by the name of Lorem 
			Ipsum decided to leave for the far World of Grammar. The Big Oxmox advised her not 
			to do so, because there were thousands of bad Commas, wild Question Marks and 
			devious Semikoli, but the Little Blind Text didn’t listen. She packed her seven 
			versalia, put her initial into the belt and made herself on the way. When she 
			reached the first hills of the Italic Mountains, she had a last view back on the 
			skyline of her hometown Bookmarksgrove, the headline of Alphabet Village and the 
			subline of her own road, the Line Lane. Pityful a ret
    	'''
    	
    	then: 
    	c.validate() == false 
    	c.errors.errorCount == 1

    	when: "we remove the note"
    	c.note = null

    	then: 
    	c.validate() == true 

    	when: "we delete the contact"
    	int mBaseline = TagMembership.count(), 
    		rBaseline = Record.count(), 
    		cBaseline = Contact.count()
    	c.delete(flush:true) 

    	then: 
    	TagMembership.count() == mBaseline - 1
    	Record.count() == rBaseline - 1
    	Contact.count() == cBaseline - 1
    }

    //////////////////////////////////////////////////////////////
    // Testing named queries out of the scope of this unit test //
    //////////////////////////////////////////////////////////////

    void "test methods that modify status"() {
    	given: 
    	Phone p = new Phone()
    	p.numberAsString = "422 333 4445"
    	p.save(flush:true)
    	Contact c = new Contact(phone:p)
    	c.resultFactory = getResultFactory()
    	c.save(flush:true)

    	when: "we archive a contact"
    	c.archive()

    	then: 
    	c.status == Constants.CONTACT_ARCHIVED

    	when: "we block a contact"
    	c.block()

    	then: 
    	c.status == Constants.CONTACT_BLOCKED

    	when: "we unblock a contact"
    	c.activate()

    	then: 
    	c.status == Constants.CONTACT_ACTIVE

    	when: "we mark a contact as unread"
    	c.markUnread()

    	then: 
    	c.status == Constants.CONTACT_UNREAD

    	when: "we mark a contact as read"
    	c.markRead()

    	then:
    	c.status == Constants.CONTACT_ACTIVE
    }

    @Ignore
    void "test scheduled texts"() {
    }

    void "test methods related to tags"() {
    	given: 
    	Phone p = new Phone()
    	p.numberAsString = "422 333 4448"
    	p.save(flush:true)
    	Contact c = new Contact(phone:p)
    	c.resultFactory = getResultFactory()
    	c.save(flush:true)
    	ContactTag t1 = new ContactTag(phone:p, name:"tag1"), 
    		t2 = new ContactTag(phone:p, name:"tag2"), 
    		t3 = new ContactTag(phone:p, name:"tag3")
		t1.save(flush:true)
    	t2.save(flush:true)
    	t3.save(flush:true)

    	when: "add to tag"
    	assert c.addToTag(t1).success
    	assert c.addToTag(t2.name).success
    	c.save(flush:true)

    	then: 
    	c.tags.size() == 2

    	when: "unsubscribe from tag that hasn't been added yet"
    	Result res = c.unsubscribeFromTag(t3)

    	then: 
    	res.success == false 
    	res.payload instanceof Map
    	res.payload.code == "contact.error.membershipNotFound"

    	when: "add nonexistent tag"
    	res = c.addToTag("nonexistent")

    	then: 
    	res.success == false 
    	res.payload instanceof Map
    	res.payload.code == "contact.error.tagNotFound"

    	when: "remove some tags"
    	assert c.removeFromTag(t1).success
    	c.save(flush:true)

    	then: 
    	c.tags.size() == 1
    	c.tags[0].tag == t2 

    	when: "remove tag where is not member"
    	res = c.removeFromTag(t3)

    	then: 
    	res.success == false 
    	res.payload instanceof Map
    	res.payload.code == "contact.error.membershipNotFound"

    	when: "remove from nonexistent tag"
	    res = c.removeFromTag("nonexistent")

    	then: 
    	res.success == false 
    	res.payload instanceof Map
    	res.payload.code == "contact.error.tagNotFound"
    }
}
