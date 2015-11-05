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
	Schedule, Location, TeamPhone, WeeklySchedule, TeamContactTag])
@TestMixin(HibernateTestMixin)
@Unroll
class TeamSpec extends Specification {

	@Shared 
	int iterationCount = 1

	Organization org1 

	def setup() {
		org1 = new Organization(name:"11Org$iterationCount")
    	org1.location = new Location(address:"Testing Address", lat:0G, lon:0G)
    	org1.save(flush:true)
	}
	def cleanup() { iterationCount++ }

    void "test constraints and deletion"() {
    	when: "we add a team with unique name"
    	String teamName = "Team1"
    	Team team1 = new Team(name:teamName, org:org1)
		team1.location = new Location(address:"Testing Address", lat:0G, lon:1G)

    	then: 
    	team1.validate() == true 

    	when: "we try to add a team with a duplicate name"
    	team1.save(flush:true, failOnError:true)
    	Team team2 = new Team(name:teamName, org:org1)
		team2.location = new Location(address:"Testing Address", lat:0G, lon:1G)

    	then: 
    	team2.validate() == false 
    	team2.errors.errorCount == 1
    	team2.errors.getFieldErrorCount("name") == 1

    	when: "we change duplicate name to a unique one"
    	team2.name = "Team2"

    	then: 
    	team2.validate() == true 

    	when: "we add a phone, associated classes and then delete"
		team2.save(flush:true, failOnError:true)

		TeamPhone p2 = new TeamPhone()
		p2.numberAsString = "8223338445"
		p2.save(flush:true, failOnError:true)

		//addphone to team
		team2.phone = p2
		team2.save(flush:true, failOnError:true)

		TeamContactTag t1 = new TeamContactTag(phone:p2, name:"tag1"), 
    		t2 = new TeamContactTag(phone:p2, name:"tag2")
    	[t1, t2]*.save(flush:true, failOnError:true)
    	Contact c1 = new Contact(phone:p2), c2 = new Contact(phone:p2)
    	[c1, c2]*.save(flush:true, failOnError:true)
    	(new TagMembership(tag:t1, contact:c1)).save(flush:true, failOnError:true)
    	(new TagMembership(tag:t2, contact:c1)).save(flush:true, failOnError:true)

    	int tBaseline = TeamContactTag.count(), cBaseline = Contact.count(), 
    		mBaseline = TagMembership.count(), pBaseline = TeamPhone.count(), 
    		rBaseline = Record.count(), lBaseline = Location.count(), 
    		teamBaseline = Team.count()
	    team2.delete(flush:true)

    	then: 
		TeamContactTag.count() == tBaseline - 2
		Contact.count() == cBaseline - 2
		TagMembership.count() == mBaseline - 2
		TeamPhone.count() == pBaseline - 1
		Record.count() == rBaseline - 4
		Location.count() == lBaseline - 1
		Team.count() == teamBaseline - 1
    }

    void "test listing members"() {
    	given: "a team with members of various statuses"
    	Team team1 = new Team(name:"Team1", org:org1)
		team1.location = new Location(address:"Testing Address", lat:0G, lon:1G)
		team1.save(flush:true, failOnError:true)

		int baseline = Staff.count() 
		int numPending = 2, numStaff = 3, numAdmin = 1, numBlocked = 5,
			numTotal = numPending + numStaff + numAdmin + numBlocked
		List<Staff> staffMembers = (1..numTotal).collect {
			Staff s = new Staff(username:"tstaff$it", password:"password", 
    			name:"Staff", email:"staff@textup.org", org:org1)	
			s.personalPhoneNumberAsString = "111 222 3333"
			s
		}
		staffMembers[0..(numPending - 1)].each { Staff s ->
			s.status = Constants.STATUS_PENDING
		}
		int pAndS = numPending + numStaff
		staffMembers[numPending..(pAndS - 1)].each { Staff s ->
			s.status = Constants.STATUS_STAFF
		}
		int pAndSAndA = pAndS + numAdmin
		staffMembers[pAndS..(pAndSAndA - 1)].each { Staff s ->
			s.status = Constants.STATUS_ADMIN
		}
		staffMembers[pAndSAndA..(numTotal - 1)].each { Staff s ->
			s.status = Constants.STATUS_BLOCKED
		}
		staffMembers*.save(flush:true, failOnError:true)
		assert Staff.count() == baseline + numTotal

		staffMembers.each { Staff s ->
			(new TeamMembership(staff:s, team:team1)).save(flush:true, failOnError:true)
		}

    	expect:
    	team1.countActiveMembers() == numStaff + numAdmin 
    	team1.countMembers() == numTotal
    	team1.countMembers(Constants.STATUS_BLOCKED) == numBlocked
    	team1.countMembers(Constants.STATUS_PENDING) == numPending
    	team1.countMembers([Constants.STATUS_STAFF, Constants.STATUS_ADMIN]) == numStaff + numAdmin
    	team1.countMembers([Constants.STATUS_STAFF, Constants.STATUS_BLOCKED]) == numStaff + numBlocked

    	staffMembers.every { team1.getMembers().contains(it) }
    	staffMembers[numPending..(pAndSAndA - 1)].every { team1.getActiveMembers().contains(it) }
    	staffMembers[numPending..(pAndSAndA - 1)].every { 
    		team1.getMembers(status:[Constants.STATUS_STAFF, Constants.STATUS_ADMIN]).contains(it)
    	}
    	staffMembers[numPending..(pAndS - 1)].every { 
    		team1.getMembers(status:[Constants.STATUS_STAFF]).contains(it)
    	}
    	staffMembers[0..(numPending - 1)].every { 
    		team1.getMembers(status:[Constants.STATUS_PENDING]).contains(it)
    	}
    	staffMembers[pAndS..(numTotal - 1)].every { 
    		team1.getMembers(status:[Constants.STATUS_BLOCKED, Constants.STATUS_ADMIN]).contains(it)
    	}
    }
}
