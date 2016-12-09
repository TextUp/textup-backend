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
import org.textup.util.CustomSpec
import org.textup.types.StaffStatus

@Domain([Contact, Phone, ContactTag, ContactNumber, Record, RecordItem, RecordText,
    RecordCall, RecordItemReceipt, SharedContact, Staff, Team, Organization,
    Schedule, Location, WeeklySchedule, PhoneOwnership, Role, StaffRole])
@TestMixin(HibernateTestMixin)
class TeamSpec extends CustomSpec {

	static doWithSpring = {
		resultFactory(ResultFactory)
	}

    def setup() {
    	setupData()
    }

    def cleanup() {
    	cleanupData()
    }

    void "test constraints and deletion"() {
    	when: "we add a team with unique name"
    	String teamName = "UniqueName1"
    	Team team1 = new Team(name:teamName, org:org)
		team1.location = new Location(address:"Testing Address", lat:0G, lon:1G)

    	then:
    	team1.validate() == true

    	when: "we try to add a team with a duplicate name"
    	team1.save(flush:true, failOnError:true)
    	Team team2 = new Team(name:teamName, org:org)
		team2.location = new Location(address:"Testing Address", lat:0G, lon:1G)

    	then:
    	team2.validate() == false
    	team2.errors.errorCount == 1
    	team2.errors.getFieldErrorCount("name") == 1

    	when: "we change duplicate name to a unique one"
    	team2.name = "UniqueName2"

    	then:
    	team2.validate() == true
    	team2.save(flush:true, failOnError:true)

        when: "if we delete team we can add another team with the same name"
        team2.isDeleted = true
        team2.save(flush:true, failOnError:true)

        Team team3 = new Team(name:team2.name, org:org)
        team3.location = new Location(address:"Testing Address", lat:0G, lon:1G)

        then:
        team3.validate() == true
        team3.save(flush:true, failOnError:true)
    }

    void "test listing members"() {
    	given: "a team and staff of various statuses"
    	String teamName = "UniqueName1"
    	Team team1 = new Team(name:teamName, org:org)
		team1.location = new Location(address:"Testing Address", lat:0G, lon:1G)
		team1.save(flush:true, failOnError:true)

		int baseline = Staff.count()
		int numPending = 2, numStaff = 3, numAdmin = 1, numBlocked = 5,
			numTotal = numPending + numStaff + numAdmin + numBlocked
		List<Staff> pending = [],
			staff = [],
			admins = [],
			blocked = [],
			staffMembers = (1..numTotal).collect {
				new Staff(username:"tstaff$it", password:"password",
					name:"Staff", email:"staff@textup.org", org:org,
					personalPhoneAsString:"1112223333",
                    lockCode:Constants.DEFAULT_LOCK_CODE)
			}
		staffMembers[0..(numPending - 1)].each { Staff s ->
			s.status = StaffStatus.PENDING
			pending << s
		}
		staffMembers[numPending..(numPending + numStaff - 1)].each { Staff s ->
			s.status = StaffStatus.STAFF
			staff << s
		}
		staffMembers[(numPending + numStaff)..(numTotal - numBlocked - 1)].each { Staff s ->
			s.status = StaffStatus.ADMIN
			admins << s
		}
		staffMembers[(numTotal - numBlocked)..(numTotal - 1)].each { Staff s ->
			s.status = StaffStatus.BLOCKED
			blocked << s
		}
		staffMembers*.save(flush:true, failOnError:true)
		assert Staff.count() == baseline + numTotal

		when: "we add all staff members to the team"
		staffMembers.each { team1.addToMembers(it) }
		team1.save(flush:true, failOnError:true)

    	then:
    	team1.activeMembers.size() == numStaff + numAdmin
    	team1.activeMembers.every { it in staff || it in admins }
    	team1.getMembersByStatus([StaffStatus.BLOCKED]).every { it in blocked }
    	team1.getMembersByStatus([StaffStatus.BLOCKED]).size() == numBlocked
    	team1.getMembersByStatus([StaffStatus.PENDING]).every { it in pending }
    	team1.getMembersByStatus([StaffStatus.PENDING]).size() == numPending
    	team1.getMembersByStatus([StaffStatus.STAFF]).every { it in staff }
    	team1.getMembersByStatus([StaffStatus.STAFF]).size() == numStaff
    	team1.getMembersByStatus([StaffStatus.ADMIN]).every { it in admins }
    	team1.getMembersByStatus([StaffStatus.ADMIN]).size() == numAdmin

    	staffMembers.every { team1.getMembers().contains(it) }
    	(staff + admins).every { team1.getActiveMembers().contains(it) }
    }

    void "test getting phones"() {
        given: "phone"
        Phone ph = t1.phone

        when: "phone is active"
        assert ph.isActive

        then:
        t1.hasInactivePhone == false
        t1.phone == tPh1
        t1.phoneWithAnyStatus == tPh1

        when: "phone is inactive"
        ph.deactivate()
        ph.save(flush:true, failOnError:true)
        assert !ph.isActive

        then:
        t1.hasInactivePhone == true
        t1.phone == null
        t1.phoneWithAnyStatus == tPh1
    }
}
