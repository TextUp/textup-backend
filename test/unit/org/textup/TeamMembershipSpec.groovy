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
	Schedule, Location, TeamPhone, TeamContactTag, WeeklySchedule])
@TestMixin(HibernateTestMixin)
@Unroll
class TeamMembershipSpec extends Specification {

    void "test constraints and deletion"() {
    	given: "two orgs, some staff and some teams"
    	Organization org1 = new Organization(name:"15Org")
    	org1.location = new Location(address:"Testing Address", lat:0G, lon:0G)
    	org1.save(flush:true)

	    Organization org2 = new Organization(name:"16Org")
    	org2.location = new Location(address:"Testing Address", lat:0G, lon:0G)
    	org2.save(flush:true)

    	Team o1T1 = new Team(name:"Team1", org:org1)
		Team o1T2 = new Team(name:"Team2", org:org1)
		Team o2T1 = new Team(name:"Team1", org:org2)
		o1T1.location = new Location(address:"Testing Address", lat:0G, lon:1G)
		o1T2.location = new Location(address:"Testing Address", lat:1G, lon:1G)
		o2T1.location = new Location(address:"Testing Address", lat:2G, lon:2G)
		o1T1.save(flush:true, failOnError:true)
		o1T2.save(flush:true, failOnError:true)
		o2T1.save(flush:true, failOnError:true)

		Staff s1 = new Staff(username:"0staff-tmem", password:"password", 
    		name:"Staff", email:"staff@textup.org", org:org1)
    	Staff s2 = new Staff(username:"1staff-tmem", password:"password", 
    		name:"Staff", email:"staff@textup.org", org:org1)
		Staff s3 = new Staff(username:"2staff-tmem", password:"password", 
			name:"Staff", email:"staff@textup.org", org:org2)
    	s1.personalPhoneNumberAsString = "111 222 3333"
    	s2.personalPhoneNumberAsString = "111 222 3333"
    	s3.personalPhoneNumberAsString = "111 222 3333"
    	[s1, s2, s3]*.save(flush:true, failOnError:true)

    	StaffPhone p1 = new StaffPhone()
    	StaffPhone p2 = new StaffPhone()
    	StaffPhone p3 = new StaffPhone()
    	p1.numberAsString = "1028884443"
    	s1.phone = p1
    	p1.save(flush:true, failOnError:true)
    	p2.numberAsString = "1128884442"
    	s2.phone = p2
    	p2.save(flush:true, failOnError:true)
    	p3.numberAsString = "1228884411"
    	s3.phone = p3
    	p3.save(flush:true, failOnError:true)

    	when: "all valid and not duplicate"
    	TeamMembership tm = new TeamMembership(staff:s1, team:o1T1)

    	then:
    	tm.validate() == true 

    	when: "we try to add the exact some membership again"
    	tm.save(flush:true, failOnError:true)
    	tm = new TeamMembership(staff:s1, team:o1T1)

    	then: "duplicate"
    	tm.validate() == false 
    	tm.errors.errorCount == 1

    	when: "we try to add a membership between two parties of different orgs"
    	tm = new TeamMembership(staff:s3, team:o1T1)

    	then: 
    	tm.validate() == false 
    	tm.errors.errorCount == 1

    	when: "we switch to the same unique parties"
    	tm = new TeamMembership(staff:s3, team:o2T1)

    	then: 
    	tm.validate() == true 

    	when: "we delete"
    	tm.save(flush:true, failOnError:true)
    	int sBaseline = Staff.count(), tBaseline = Team.count(),
    		mBaseline = TeamMembership.count()
    	tm.delete(flush:true)

    	then: 
    	Staff.count() == sBaseline
		Team.count() == tBaseline
		TeamMembership.count() == mBaseline - 1 
    }
}
