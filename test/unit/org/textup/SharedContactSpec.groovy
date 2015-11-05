package org.textup

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import grails.validation.ValidationErrors
import org.joda.time.DateTime
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
class SharedContactSpec extends Specification {

	@Shared 
	int iterationCount = 1

	Team t1, t2
	Staff s1, s2, s3
	StaffPhone p1, p2, p3
	Contact c1, c2, c3

	static doWithSpring = {
		resultFactory(ResultFactory)
	}
	def setup() {
		ResultFactory fac = grailsApplication.mainContext.getBean("resultFactory")
		fac.messageSource = [getMessage:{ String code, 
			Object[] parameters, Locale locale -> code }] as MessageSource

		Organization org = new Organization(name:"Org$iterationCount")
    	org.location = new Location(address:"Testing Address", lat:0G, lon:0G)
    	org.save(flush:true, failOnError:true)

    	t1 = new Team(name:"Team1", org:org)
		t2 = new Team(name:"Team2", org:org)
		t1.location = new Location(address:"Testing Address", lat:0G, lon:1G)
		t2.location = new Location(address:"Testing Address", lat:1G, lon:1G)
		t1.save(flush:true, failOnError:true)
		t2.save(flush:true, failOnError:true)

    	s1 = new Staff(username:"0staff$iterationCount", password:"password", 
    		name:"Staff$iterationCount", email:"staff$iterationCount@textup.org", org:org)
    	s2 = new Staff(username:"1staff$iterationCount", password:"password", 
    		name:"Staff$iterationCount", email:"staff$iterationCount@textup.org", org:org)
		s3 = new Staff(username:"2staff$iterationCount", password:"password", 
			name:"Staff$iterationCount", email:"staff$iterationCount@textup.org", org:org)
    	s1.personalPhoneNumberAsString = "111 222 3333"
    	s2.personalPhoneNumberAsString = "111 222 3333"
    	s3.personalPhoneNumberAsString = "111 222 3333"
    	[s1, s2, s3]*.save(flush:true, failOnError:true)

    	p1 = new StaffPhone()
    	p2 = new StaffPhone()
    	p3 = new StaffPhone()
    	p1.numberAsString = "102333444${iterationCount}"
    	s1.phone = p1
    	p1.save(flush:true, failOnError:true)
    	p2.numberAsString = "112333444$iterationCount"
    	s2.phone = p2
    	p2.save(flush:true, failOnError:true)
    	p3.numberAsString = "122333441$iterationCount"
    	s3.phone = p3
    	p3.save(flush:true, failOnError:true)
		
    	c1 = new Contact(phone:p1)
		c2 = new Contact(phone:p2)
		c3 = new Contact(phone:p3)
    	c1.save(flush:true, failOnError:true)
    	c2.save(flush:true, failOnError:true)
    	c3.save(flush:true, failOnError:true)

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
    	when: "we have a valid SharedContact"
    	SharedContact sc = new SharedContact(contact:c1, sharedBy:p1, sharedWith:p2, permission:Constants.SHARED_DELEGATE)

    	then: 
    	sc.validate() == true 

    	when: "we try to share a contact that is not our's"
    	sc = new SharedContact(contact:c2, sharedBy:p1, sharedWith:p2, permission:Constants.SHARED_DELEGATE)

    	then:
    	sc.validate() == false
    	sc.errors.errorCount == 1 

    	when: "we try to share a contact with ourselves"
    	sc = new SharedContact(contact:c1, sharedBy:p1, sharedWith:p1, permission:Constants.SHARED_DELEGATE)

    	then: 
    	sc.validate() == false 
    	sc.errors.errorCount == 1

    	when: "we have a SharedContact with two phones belonging to staff on different teams"
    	sc = new SharedContact(contact:c1, sharedBy:p1, sharedWith:p3, permission:Constants.SHARED_DELEGATE)

    	then: "still valid, if we try to view or modify, won't be able to"
    	sc.validate() == true 

    	when: "delete this SharedContact"
    	sc.save(flush:true, failOnError:true)
    	int baseline = SharedContact.count()
    	sc.delete(flush:true)

    	then: 
    	SharedContact.count() == baseline - 1
    }

    void "test named queries"() {
    	given: "various SharedContact relationships"
    	SharedContact sc1 = new SharedContact(contact:c1, sharedBy:p1, sharedWith:p2, 
    			permission:Constants.SHARED_DELEGATE),
    		sc2 = new SharedContact(contact:c1, sharedBy:p1, sharedWith:p3, 
    			permission:Constants.SHARED_DELEGATE), 
    		sc3 = new SharedContact(contact:c2, sharedBy:p2, sharedWith:p1, 
    			permission:Constants.SHARED_DELEGATE),
    		sc4 = new SharedContact(contact:c3, sharedBy:p3, sharedWith:p1, 
    			permission:Constants.SHARED_DELEGATE)
    	[sc1, sc2, sc3, sc4].eachWithIndex { SharedContact sc, int i ->
    		sc.contact.lastRecordActivity = DateTime.now().plusMinutes(i)
    		sc.save(flush:true, failOnError:true)
		}

    	when: 
    	List<SharedContact> found = SharedContact.notExpired.list()
    	List<SharedContact> sWithMe = SharedContact.sharedWithMe(p1).list()
    	List<SharedContact> sByMe = SharedContact.sharedByMe(p1).list()
    	List<SharedContact> allNonexpFor = SharedContact.allNonexpiredFor(c1, p1).list()
    	List<SharedContact> nonexpForWithP1P2 = SharedContact.nonexpiredFor(c1, p1, p2).list()
    	List<SharedContact> nonexpForWithP1P3 = SharedContact.nonexpiredFor(c1, p1, p3).list()

    	then:
    	found == [sc4, sc3, sc1, sc2]
    	sWithMe == [sc3]
		sByMe == [sc1]
		allNonexpFor == [sc1]
		nonexpForWithP1P2 == [sc1]
		nonexpForWithP1P3 == []

		when: "we mark sc3 as unread"
		sc3.contact.status = Constants.CONTACT_UNREAD
		sc3.save(flush:true, failOnError:true)
		found = SharedContact.notExpired.list()

		then: 
		found == [sc3, sc4, sc1, sc2]

		when: "we expire sc3"
		sc3.stopSharing()
		sc3.save(flush:true, failOnError:true)

		found = SharedContact.notExpired.list()
		sWithMe = SharedContact.sharedWithMe(p1).list()

		then: 
		found == [sc4, sc1, sc2]
		sWithMe == []
    }

    void "test sharing permissions by expiration"() {
    	given: 
    	SharedContact sc1 = new SharedContact(contact:c1, sharedBy:p1, sharedWith:p2, 
    			permission:Constants.SHARED_DELEGATE)
    	SharedContact sc2 = new SharedContact(contact:c2, sharedBy:p2, sharedWith:p1, 
    			permission:Constants.SHARED_DELEGATE)
    	[sc1, sc2].eachWithIndex { SharedContact sc, int i ->
    		sc.contact.lastRecordActivity = DateTime.now().plusMinutes(i)
    		sc.save(flush:true, failOnError:true)
		}

		when: 
		List<SharedContact> sWithMe = SharedContact.sharedWithMe(p1).list()
    	List<SharedContact> sByMe = SharedContact.sharedByMe(p1).list()

    	then: 
    	sWithMe == [sc2]
    	sByMe == [sc1]

    	when: "we expire sc2"
    	sc2.stopSharing()
    	sc2.save(flush:true, failOnError:true)

    	sWithMe = SharedContact.sharedWithMe(p1).list()
		sByMe = SharedContact.sharedByMe(p1).list()

		then: 
		sWithMe == []
    	sByMe == [sc1]
    }

    void "test sharing permissions by team membership"() {
    	given: 
    	SharedContact sc1 = new SharedContact(contact:c1, sharedBy:p1, sharedWith:p3, 
    			permission:Constants.SHARED_DELEGATE)
    	SharedContact sc2 = new SharedContact(contact:c3, sharedBy:p3, sharedWith:p1, 
    			permission:Constants.SHARED_DELEGATE)
    	[sc1, sc2].eachWithIndex { SharedContact sc, int i ->
    		sc.contact.lastRecordActivity = DateTime.now().plusMinutes(i)
    		sc.save(flush:true, failOnError:true)
		}

		when: 
		List<SharedContact> sWithMe = SharedContact.sharedWithMe(p1).list()
    	List<SharedContact> sByMe = SharedContact.sharedByMe(p1).list()

    	then: 
    	sWithMe == []
    	sByMe == []

    	when: "we add staff of p3 to the same team as staff of p1"
    	(new TeamMembership(staff:s3, team:t1)).save(flush:true, failOnError:true)

    	sWithMe = SharedContact.sharedWithMe(p1).list()
		sByMe = SharedContact.sharedByMe(p1).list()

		then: 
		sWithMe == [sc2]
    	sByMe == [sc1]
    }
}
