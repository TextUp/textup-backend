package org.textup

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import grails.validation.ValidationErrors
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.springframework.context.MessageSource
import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

@Domain([TagMembership, Contact, Phone, ContactTag,
	ContactNumber, Record, RecordItem, RecordNote, RecordText,
	RecordCall, RecordItemReceipt, PhoneNumber, SharedContact,
	TeamMembership, StaffPhone, Staff, Team, Organization,
	Schedule, Location, TeamPhone, WeeklySchedule])
@TestMixin(HibernateTestMixin)
@Unroll
class StaffPhoneSpec extends Specification {

	@Shared
	int iterationCount = 1

	Organization org
	Team t1, t2
	Staff s1, s2, s3
	StaffPhone p1, p2, p3

	static doWithSpring = {
		resultFactory(ResultFactory)
	}
	def setup() {
		ResultFactory fac = grailsApplication.mainContext.getBean("resultFactory")
		fac.messageSource = [getMessage:{ String c, Object[] p, Locale l -> c }] as MessageSource
		StaffPhone.metaClass.constructor = { ->
			def instance = grailsApplication.mainContext.getBean(StaffPhone.name)
			instance.resultFactory = getResultFactory()
			instance
		}

		org = new Organization(name:"1Org$iterationCount")
    	org.location = new Location(address:"Testing Address", lat:0G, lon:0G)
    	org.save(flush:true)

    	t1 = new Team(name:"Team1", org:org)
		t2 = new Team(name:"Team2", org:org)
		t1.location = new Location(address:"Testing Address", lat:0G, lon:1G)
		t2.location = new Location(address:"Testing Address", lat:1G, lon:1G)
		t1.save(flush:true, failOnError:true)
		t2.save(flush:true, failOnError:true)

		s1 = new Staff(username:"3staff$iterationCount", password:"password",
    		name:"Staff$iterationCount", email:"staff$iterationCount@textup.org", org:org)
    	s2 = new Staff(username:"4staff$iterationCount", password:"password",
    		name:"Staff$iterationCount", email:"staff$iterationCount@textup.org", org:org)
		s3 = new Staff(username:"5staff$iterationCount", password:"password",
			name:"Staff$iterationCount", email:"staff$iterationCount@textup.org", org:org)
    	s1.personalPhoneNumberAsString = "111 222 3333"
    	s2.personalPhoneNumberAsString = "111 222 3333"
    	s3.personalPhoneNumberAsString = "111 222 3333"
    	s1.save(flush:true, failOnError:true)
		s2.save(flush:true, failOnError:true)
		s3.save(flush:true, failOnError:true)

    	p1 = new StaffPhone()
    	p1.numberAsString = "100333444${iterationCount}"
    	s1.phone = p1
    	p1.save(flush:true, failOnError:true)
    	p2 = new StaffPhone()
    	p2.numberAsString = "111333444$iterationCount"
    	s2.phone = p2
    	p2.save(flush:true, failOnError:true)
    	p3 = new StaffPhone()
    	p3.numberAsString = "123333441$iterationCount"
    	s3.phone = p3
    	p3.save(flush:true, failOnError:true)

    	(new TeamMembership(staff:s1, team:t1)).save(flush:true, failOnError:true)
    	(new TeamMembership(staff:s2, team:t1)).save(flush:true, failOnError:true)
    	(new TeamMembership(staff:s2, team:t2)).save(flush:true, failOnError:true)
    	(new TeamMembership(staff:s3, team:t2)).save(flush:true, failOnError:true)
	}
	def cleanup() { iterationCount++ }
	private ResultFactory getResultFactory() {
		grailsApplication.mainContext.getBean("resultFactory")
	}

	void "test constraints and deletion"() {
		given: "a staff member"
		Staff staff = new Staff(username:"6staff$iterationCount", password:"password",
			name:"Staff$iterationCount", email:"staff$iterationCount@textup.org", org:org)
		staff.personalPhoneNumberAsString = "111 222 3333"
		staff.save(flush:true, failOnError:true)
		Staff staff2 = new Staff(username:"7staff$iterationCount", password:"password",
			name:"Staff$iterationCount", email:"staff$iterationCount@textup.org", org:org)
		staff2.personalPhoneNumberAsString = "111 222 3333"
		staff2.save(flush:true, failOnError:true)

    	when: "we have a phone without a number"
    	StaffPhone p = new StaffPhone()
    	staff.phone = p

    	then:
    	p.validate() == false
    	p.errors.errorCount == 1

    	when: "we have a phone with a unique number"
    	p.numberAsString = "8223334444"

    	then:
    	p.validate() == true

    	when: "we try to add a phone with a duplicate number"
    	p.save(flush:true)
    	StaffPhone p2 = new StaffPhone()
    	staff2.phone = p2
    	p2.numberAsString = "8223334444"

    	then:
    	p2.validate() == false
    	p2.errors.errorCount == 1

    	when: "we add a phone with a unique number"
    	p2.numberAsString = "8223334445"

    	then:
    	p2.validate() == true

    	when: "we add associated classes WITHOUT ContactNumbers, then delete the phone"
    	p2.save(flush:true)
    	ContactTag tag1 = new ContactTag(phone:p2, name:"tag1"),
    		tag2 = new ContactTag(phone:p2, name:"tag2")
    	tag1.save(flush:true, failOnError:true)
    	tag2.save(flush:true, failOnError:true)
    	Contact c1 = new Contact(phone:p2), c2 = new Contact(phone:p2),
    		c3 = new Contact(phone:p)
		c1.save(flush:true, failOnError:true)
		c2.save(flush:true, failOnError:true)
		c3.save(flush:true, failOnError:true)
    	(new TagMembership(tag:tag1, contact:c1)).save(flush:true, failOnError:true)
    	(new TagMembership(tag:tag2, contact:c1)).save(flush:true, failOnError:true)

    	(new TeamMembership(staff:staff, team:t1)).save(flush:true, failOnError:true)
    	(new TeamMembership(staff:staff2, team:t1)).save(flush:true, failOnError:true)

    	Result shareRes1 = p2.shareContact(c1, p, Constants.SHARED_DELEGATE)
    	Result shareRes2 = p.shareContact(c3, p2, Constants.SHARED_DELEGATE)
    	assert shareRes1.success && shareRes2.success
    	SharedContact sc1 = shareRes1.payload
    	SharedContact sc2 = shareRes2.payload
    	[sc1, sc2]*.save(flush:true, failOnError:true)

    	//break link between staff and phone
    	staff2.phone = null
    	staff2.save(flush:true, failOnError:true)

    	int tBaseline = ContactTag.count(), cBaseline = Contact.count(),
    		mBaseline = TagMembership.count(), pBaseline = StaffPhone.count(),
    		rBaseline = Record.count(), scBaseline = SharedContact.count()
	    p2.delete(flush:true)

    	then:
    	ContactTag.count() == tBaseline - 2
		Contact.count() == cBaseline - 2
		TagMembership.count() == mBaseline - 2
		StaffPhone.count() == pBaseline - 1
		Record.count() == rBaseline - 2
		SharedContact.count() == scBaseline - 2
    }

	void "test sharing contact operations"() {
		given:
		Contact c1 = p1.createContact().payload
		Contact c2 = p1.createContact().payload
		Contact c3 = p1.createContact().payload
		Contact c4 = p1.createContact().payload
		Contact o1 = p2.createContact().payload
		Contact n1 = p3.createContact().payload
		[c1, c2, c3, c4, o1, n1]*.save(flush:true, failOnError:true)

    	when: "we start sharing one of our contacts with someone on a different team"
    	Result res = p1.shareContact(c1, p3, Constants.SHARED_DELEGATE)

    	then:
    	res.success == false
    	res.payload instanceof Map
    	res.payload.code == "staffPhone.error.differentTeams"

    	when: "we start sharing contact with someone on the same team "
    	res = p1.shareContact(c1, p2, Constants.SHARED_DELEGATE)

    	then:
    	res.success == true
    	res.payload.instanceOf(SharedContact)

    	when: "we start sharing contact that we've already shared with the same person"
    	int sBaseline = SharedContact.count()
		res = p1.shareContact(c1, p2, Constants.SHARED_DELEGATE)
		assert res.success
		SharedContact sc0 = res.payload
		res.payload.save(flush:true, failOnError:true)

    	then: "we don't create a duplicate SharedContact"
    	sc0.instanceOf(SharedContact)
    	SharedContact.count() == sBaseline

    	when: "we share three more and list all shared so far"
		SharedContact sc1 = p1.shareContact(c2, p2, Constants.SHARED_DELEGATE).payload,
			sc2 = p1.shareContact(c3, p2, Constants.SHARED_DELEGATE).payload,
			sc3 = p2.shareContact(o1, p1, Constants.SHARED_DELEGATE).payload
		[sc1, sc2, sc3].eachWithIndex { SharedContact sc, int i ->
    		sc.contact.lastRecordActivity = DateTime.now(DateTimeZone.UTC).plusMinutes(i)
    		sc.save(flush:true, failOnError:true)
		}

    	then:
    	p1.sharedByMe == [sc2, sc1, sc0]
    	p1.sharedWithMe == [sc3]

    	when: "we stop sharing someone else's contact"
    	res = p1.stopSharing(o1)

    	then:
    	res.success == false
    	res.payload instanceof Map
    	res.payload.code == "staffPhone.error.contactNotMine"

    	when: "we stop sharing contact that is not shared"
    	res = p1.stopSharing(c4)

    	then:
    	res.success == false
    	res.payload instanceof Map
    	res.payload.code == "staffPhone.error.allNotShared"

    	when: "we stop sharing by staff phones"
    	assert p1.stopSharingWith(p2).success
    	p1.save(flush:true, failOnError:true)

    	then:
    	p1.sharedByMe == []
    	p1.sharedWithMe == [sc3]

    	when: "stop sharing by contacts"
    	SharedContact sc4 = p2.shareContact(o1, p3, Constants.SHARED_DELEGATE).payload
    	p2.save(flush:true, failOnError:true)
    	assert p2.sharedByMe == [sc4, sc3] || p2.sharedByMe == [sc3, sc4] //same underlying contact
    	assert p1.sharedWithMe == [sc3] && p3.sharedWithMe == [sc4]

    	p2.stopSharing(o1)
    	p2.save(flush:true, failOnError:true)

    	then:
    	p2.sharedByMe == []
    	p1.sharedWithMe == []
    	p3.sharedWithMe == []
	}

	void "test getting contacts also gets shared contacts mixed in"() {
		given:
		Contact c1 = p1.createContact().payload
		Contact c2 = p1.createContact().payload
		Contact c3 = p1.createContact().payload
		Contact c4 = p1.createContact().payload
		Contact o1 = p2.createContact().payload
		Contact n1 = p3.createContact().payload
		[c1, c2, c3, c4, o1, n1].eachWithIndex { Contact c, int i ->
    		c.lastRecordActivity = DateTime.now().minusMinutes(i)
    		c.save(flush:true, failOnError:true)
		}

		SharedContact sc1 = p1.shareContact(c2, p2, Constants.SHARED_DELEGATE).payload,
			sc2 = p1.shareContact(c3, p2, Constants.SHARED_DELEGATE).payload,
			sc3 = p2.shareContact(o1, p1, Constants.SHARED_DELEGATE).payload
		[sc1, sc2, sc3].eachWithIndex { SharedContact sc, int i ->
    		sc.contact.lastRecordActivity = DateTime.now().plusMinutes(i)
    		sc.save(flush:true, failOnError:true)
		}

		when: "we list all our contacts"
		List<Contactable> p1Contactables = p1.contacts,
			p2Contactables = p2.contacts

		then:
		p1Contactables == [sc3, c3, c2, c1, c4]
		p2Contactables == [o1, sc2, sc1] //adding time to sc3 modifies o1

		when: "we mark a few shared contacts and contacts as unread"
		c4.status = Constants.CONTACT_UNREAD
		sc2.contact.status = Constants.CONTACT_UNREAD
		p1.save(flush:true, failOnError:true)

		p1Contactables = p1.contacts
		p2Contactables = p2.contacts

		then:
		p1Contactables == [c3, c4, sc3, c2, c1]
		p2Contactables == [sc2, o1, sc1]

		when: "we stop sharing some shared contacts"
		assert p1.stopSharing(c3).success
		p1.save(flush:true, failOnError:true)

		p1Contactables = p1.contacts
		p2Contactables = p2.contacts

		then:
		p1Contactables == [c3, c4, sc3, c2, c1]
		p2Contactables == [o1, sc1]
	}
}
