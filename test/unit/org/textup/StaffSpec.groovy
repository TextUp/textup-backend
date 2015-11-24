package org.textup

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import grails.validation.ValidationErrors
import org.joda.time.DateTime
import org.joda.time.DateTimeConstants
import org.joda.time.DateTimeZone
import org.joda.time.LocalTime
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
class StaffSpec extends Specification {

	@Shared
	int iterationCount = 1

	Organization org1, org2
	Team o1T1, o1T2, o2T1

	static doWithSpring = {
		resultFactory(ResultFactory)
	}
	def setup() {
		ResultFactory fac = grailsApplication.mainContext.getBean("resultFactory")
		fac.messageSource = [getMessage:{ String c, Object[] p, Locale l -> c }] as MessageSource
		Staff.metaClass.constructor = { ->
			def instance = grailsApplication.mainContext.getBean(Staff.name)
			instance.resultFactory = getResultFactory()
			instance
		}
		Staff.metaClass.constructor = { Map m->
			def instance = new Staff()
			instance.properties = m
			instance.resultFactory = getResultFactory()
			instance
		}
		StaffPhone.metaClass.constructor = { ->
			def instance = grailsApplication.mainContext.getBean(StaffPhone.name)
			instance.resultFactory = getResultFactory()
			instance
		}
		StaffPhone.metaClass.constructor = { Map m ->
			def instance = new StaffPhone()
			instance.properties = m
			instance.resultFactory = getResultFactory()
			instance
		}
		WeeklySchedule.metaClass.constructor = { Map m ->
            def instance = new WeeklySchedule()
            instance.properties = m
            instance.resultFactory = getResultFactory()
            instance
        }

		org1 = new Organization(name:"11Org$iterationCount")
    	org1.location = new Location(address:"Testing Address", lat:0G, lon:0G)
    	org1.save(flush:true)

	    org2 = new Organization(name:"12Org$iterationCount")
    	org2.location = new Location(address:"Testing Address", lat:0G, lon:0G)
    	org2.save(flush:true)

    	o1T1 = new Team(name:"Team1", org:org1)
		o1T2 = new Team(name:"Team2", org:org1)
		o2T1 = new Team(name:"Team1", org:org2)
		o1T1.location = new Location(address:"Testing Address", lat:0G, lon:1G)
		o1T2.location = new Location(address:"Testing Address", lat:1G, lon:1G)
		o2T1.location = new Location(address:"Testing Address", lat:2G, lon:2G)
		o1T1.save(flush:true, failOnError:true)
		o1T2.save(flush:true, failOnError:true)
		o2T1.save(flush:true, failOnError:true)
	}
	def cleanup() { iterationCount++ }
	private ResultFactory getResultFactory() {
		grailsApplication.mainContext.getBean("resultFactory")
	}

    void "test constraints and deletion"() {
    	when: "we have an empty staff"
    	Staff s = new Staff()

    	then:
    	s.validate() == false

    	when: "we have fill in info except for invalid email"
    	s.username = "8staff"
    	s.password = "password"
    	s.name = "Staff"
    	s.email = "invalid email"
    	s.org = org1
		s.personalPhoneNumberAsString = "111 222 3333"

    	then:
    	s.validate() == false
    	s.errors.errorCount == 1
    	s.errors.getFieldErrorCount("email") == 1

    	when: "we fill in all valid and unique info"
    	s.email = "staff@textup.org"

    	then:
    	s.validate() == true

    	when: "we try to create a staff with duplicate username"
    	mockForConstraintsTests(Staff, [s])
    	s.save(flush:true, failOnError:true)

    	StaffPhone p = new StaffPhone()
		p.numberAsString = "1008334444"
    	s.phone = p
    	p.save(flush:true, failOnError:true)

    	Staff s2 = new Staff(username:"8StAff", password:"password",
    		name:"Staff", email:"staff@textup.org", org:org1)
    	s2.personalPhoneNumberAsString = "111 222 3333"

    	then: "usernames are NOT case sensitive"
    	s2.validate() == false
    	s2.errors.errorCount == 1
    	s2.errors.getFieldErrorCount("username") == 1

    	when: "we change to a unique username and then delete"
    	s2.username = "9staff"
    	s2.save(flush:true, failOnError:true)

    	StaffPhone p2 = new StaffPhone()
    	p2.numberAsString = "1008334445"
    	s2.phone = p2
    	p2.save(flush:true, failOnError:true)

    	Contact c0 = p.createContact().payload
    	Contact c1 = p2.createContact().payload
    	Contact c2 = p2.createContact().payload
    	ContactTag tag1 = p2.createTag(name:"Tag 1").payload
    	ContactTag tag2 = p2.createTag(name:"Tag 2").payload
    	[c0, c1, c2, tag1, tag2, p, p2]*.save(flush:true, failOnError:true)

    	(new TagMembership(contact:c1, tag:tag1)).save(flush:true, failOnError:true)
    	(new TagMembership(contact:c2, tag:tag1)).save(flush:true, failOnError:true)
    	(new TagMembership(contact:c2, tag:tag2)).save(flush:true, failOnError:true)

    	//Must come before share contact or else will have differentTeams error
    	(new TeamMembership(staff:s, team:o1T1)).save(flush:true, failOnError:true)
    	(new TeamMembership(staff:s, team:o1T2)).save(flush:true, failOnError:true)
    	(new TeamMembership(staff:s2, team:o1T1)).save(flush:true, failOnError:true)

    	Result shareRes1 = p2.shareContact(c1, p, Constants.SHARED_DELEGATE)
    	Result shareRes2 = p.shareContact(c0, p2, Constants.SHARED_DELEGATE)
    	assert shareRes1.success && shareRes2.success
    	SharedContact sc1 = shareRes1.payload
    	SharedContact sc2 = shareRes2.payload
    	[sc1, sc2]*.save(flush:true, failOnError:true)

    	int ctBaseline = ContactTag.count(), cBaseline = Contact.count(),
    		tamBaseline = TagMembership.count(), pBaseline = Phone.count(),
    		rBaseline = Record.count(), scBaseline = SharedContact.count(),
    		temBaseline = TeamMembership.count(), schedBaseline = Schedule.count()
    	s2.delete(flush:true)

    	then:
    	ContactTag.count() == ctBaseline - 2
		Contact.count() == cBaseline - 2
		TagMembership.count() == tamBaseline - 3
		Phone.count() == pBaseline - 1
		Record.count() == rBaseline - 2
		TeamMembership.count() == temBaseline - 1
		Schedule.count() == schedBaseline - 1
		SharedContact.count() == scBaseline - 2
    }

    void "test operations to change status"() {
    	given: "a valid staff, staff default is pending"
    	Staff s1 = new Staff(username:"10staff", password:"password",
    		name:"Staff", email:"staff@textup.org", org:org1)
    	s1.personalPhoneNumberAsString = "111 222 3333"
    	s1.save(flush:true, failOnError:true)
    	StaffPhone p1 = new StaffPhone()
    	p1.numberAsString = "1008334446"
    	s1.phone = p1
    	p1.save(flush:true, failOnError:true)

    	assert s1.status == Constants.STATUS_PENDING

    	when: "we attempt to promote to admin"
    	Result res = s1.promoteToAdmin()

    	then: "need to be staff to do so"
    	res.success == false
    	res.payload instanceof Map
    	res.payload.code == "staff.error.notYetApproved"

    	when: "we attempt to demote from admin"
    	res = s1.demoteFromAdmin()

    	then: "need already be admin to do so"
    	res.success == false
    	res.payload instanceof Map
    	res.payload.code == "staff.error.notAdmin"

    	when: "we approve"
    	res = s1.approve()

    	then:
    	res.success == true
    	res.payload instanceof Staff
    	res.payload.status == Constants.STATUS_STAFF

    	when: "we block"
    	res = s1.block()

    	then:
    	res.success == true
    	res.payload instanceof Staff
    	res.payload.status == Constants.STATUS_BLOCKED
    }

    void "test operations on teams"() {
    	given: "a staff and two teams"
    	Staff s1 = new Staff(username:"11staff", password:"password",
    		name:"Staff", email:"staff@textup.org", org:org1)
    	s1.personalPhoneNumberAsString = "111 222 3333"
    	s1.save(flush:true, failOnError:true)
    	StaffPhone p1 = new StaffPhone()
    	p1.numberAsString = "1008334447"
    	s1.phone = p1
    	p1.save(flush:true, failOnError:true)

    	when: "we add a nonexistent team"
    	Result res = s1.addToTeam("nonexistent")

    	then:
    	res.success == false
    	res.payload instanceof Map
    	res.payload.code == "staff.error.teamNotFound"

    	when: "we add a team from another organization"
    	res = s1.addToTeam(o2T1)

    	then:
    	res.success == false
    	res.payload instanceof ValidationErrors
    	res.payload.errorCount == 1

    	when: "we add a valid team"
    	res = s1.addToTeam(o1T1)

    	then:
    	res.success == true
    	res.payload instanceof TeamMembership
    	res.payload.staff == s1
    	res.payload.team == o1T1

    	when: "we add the same team again"
    	res.payload.save(flush:true, failOnError:true)
    	int temBaseline = TeamMembership.count()
    	res = s1.addToTeam(o1T1)

    	then: "same TeamMembership is returned"
    	TeamMembership.count() == temBaseline
    	res.success == true
    	res.payload instanceof TeamMembership
    	res.payload.staff == s1
    	res.payload.team == o1T1

    	when: "we add another team then list teams"
    	res = s1.addToTeam(o1T2)
    	assert res.success
    	s1.save(flush:true, failOnError:true)

    	then:
    	TeamMembership.count() == temBaseline + 1
    	[o1T1, o1T2].every { s1.teams.contains(it) }

    	when: "we remove a nonexistent team"
    	res = s1.removeFromTeam("nonexistent")

    	then:
    	res.success == false
    	res.payload instanceof Map
    	res.payload.code == "staff.error.teamNotFound"

    	when: "we remove a team we are on"
    	temBaseline = TeamMembership.count()
    	res = s1.removeFromTeam(o1T1)
    	s1.save(flush:true, failOnError:true)

    	then:
    	TeamMembership.count() == temBaseline - 1
    	res.success == true
    	res.payload instanceof Team
    	res.payload.name == o1T1.name

    	when: "we try to remove the same team again"
    	res.payload.save(flush:true, failOnError:true)
    	temBaseline = TeamMembership.count()
		res = s1.removeFromTeam(o1T1)
		s1.save(flush:true, failOnError:true)

    	then: "nothing changes"
    	TeamMembership.count() == temBaseline
    	res.success == true
    	res.payload instanceof Team
    	res.payload.name == o1T1.name
    }

    void "test operations on schedules"() {
    	given: "a valid staff"
    	Staff s1 = new Staff(username:"12staff", password:"password",
    		name:"Staff", email:"staff@textup.org", org:org1)
    	s1.personalPhoneNumberAsString = "111 222 3333"
    	s1.save(flush:true, failOnError:true)
    	StaffPhone p1 = new StaffPhone()
    	p1.numberAsString = "1008334448"
    	s1.phone = p1
    	p1.save(flush:true, failOnError:true)

    	when: "manual schedule is off"
    	s1.manualSchedule = false
    	s1.isAvailable = true

    	LocalTime midnight = new LocalTime(0, 0)
		LocalTime t1 = new LocalTime(1, 0)
		DateTime availableTime = DateTime.now(DateTimeZone.UTC).plusDays(1).withHourOfDay(0).withMinuteOfHour(30),
			tomMidnight = DateTime.now(DateTimeZone.UTC).plusDays(1).withTimeAtStartOfDay(),
			tom1AM = DateTime.now(DateTimeZone.UTC).plusDays(1).withTimeAtStartOfDay().plusHours(1)

    	String tomString = getDayOfWeekStringFor(DateTime.now(DateTimeZone.UTC).plusDays(1))
    	s1.updateSchedule((tomString):[new LocalInterval(midnight, t1)])
    	s1.save(flush:true, failOnError:true)

    	then: "we can ask all the schedule questions"
    	s1.isAvailableNow() == false
		s1.isAvailableAt(availableTime).payload == true
		s1.nextChange().payload instanceof ScheduleChange
		s1.nextChange().payload.type == Constants.SCHEDULE_AVAILABLE
		s1.nextChange().payload.when == tomMidnight
		s1.nextAvailable().payload == tomMidnight
		s1.nextUnavailable().payload == tom1AM

    	when: "manual schedule is on"
    	s1.manualSchedule = true

    	then: "we can only ask about availability right now"
    	s1.isAvailableNow() == s1.isAvailable
    	s1.isAvailableAt(availableTime).success == false
    	s1.isAvailableAt(availableTime).payload.code == "staff.error.scheduleInfoUnavailable"
    	s1.nextChange().success == false
    	s1.nextChange().payload.code == "staff.error.scheduleInfoUnavailable"
    	s1.nextAvailable().success == false
    	s1.nextAvailable().payload.code == "staff.error.scheduleInfoUnavailable"
    	s1.nextUnavailable().success == false
    	s1.nextUnavailable().payload.code == "staff.error.scheduleInfoUnavailable"
    }
    private String getDayOfWeekStringFor(DateTime dt) {
    	switch(dt.dayOfWeek) {
            case DateTimeConstants.SUNDAY: return "sunday"
            case DateTimeConstants.MONDAY: return "monday"
            case DateTimeConstants.TUESDAY: return "tuesday"
            case DateTimeConstants.WEDNESDAY: return "wednesday"
            case DateTimeConstants.THURSDAY: return "thursday"
            case DateTimeConstants.FRIDAY: return "friday"
            case DateTimeConstants.SATURDAY: return "saturday"
        }
    }
}
