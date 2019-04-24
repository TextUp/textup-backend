package org.textup

import grails.test.mixin.*
import grails.test.mixin.gorm.*
import grails.test.mixin.hibernate.*
import grails.test.mixin.support.*
import grails.test.mixin.web.ControllerUnitTestMixin
import grails.test.runtime.*
import grails.validation.*
import org.joda.time.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*
import spock.lang.*

@Domain([AnnouncementReceipt, ContactNumber, CustomAccountDetails, FeaturedAnnouncement,
    FutureMessage, GroupPhoneRecord, IncomingSession, IndividualPhoneRecord, Location, MediaElement,
    MediaElementVersion, MediaInfo, Organization, OwnerPolicy, Phone, PhoneNumberHistory,
    PhoneOwnership, PhoneRecord, PhoneRecordMembers, Record, RecordCall, RecordItem,
    RecordItemReceipt, RecordNote, RecordNoteRevision, RecordText, Role, Schedule,
    SimpleFutureMessage, Staff, StaffRole, Team, Token])
@TestMixin([HibernateTestMixin, ControllerUnitTestMixin])
class PhoneOwnershipSpec extends Specification {

	static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test creation + constraints"() {
        given:
        Phone p1 = TestUtils.buildStaffPhone()
        Staff s1 = TestUtils.buildStaff()
        Team t1 = TestUtils.buildTeam()

    	when: "we have an empty phone ownership"
    	Result res = PhoneOwnership.tryCreate(null, null, null)

    	then: "invalid"
        res.status == ResultStatus.UNPROCESSABLE_ENTITY

        when:
        res = PhoneOwnership.tryCreate(p1, -88, PhoneOwnershipType.INDIVIDUAL)

        then: "OK even if nonexistent"
        res.status == ResultStatus.CREATED
        res.payload.phone == p1
        res.payload.ownerId == -88
        res.payload.type == PhoneOwnershipType.INDIVIDUAL

    	when:
        res = PhoneOwnership.tryCreate(p1, s1.id, PhoneOwnershipType.INDIVIDUAL)

    	then:
    	res.status == ResultStatus.CREATED
        res.payload.phone == p1
        res.payload.ownerId == s1.id
        res.payload.type == PhoneOwnershipType.INDIVIDUAL

        when:
        res = PhoneOwnership.tryCreate(p1, t1.id, PhoneOwnershipType.GROUP)

        then:
        res.status == ResultStatus.CREATED
        res.payload.validate()
        res.payload.phone == p1
        res.payload.ownerId == t1.id
        res.payload.type == PhoneOwnershipType.GROUP
    }

    void "test getting all staff"() {
    	given: "team has all active staff"
        Staff s1 = TestUtils.buildStaff()
        s1.status == StaffStatus.STAFF
        Staff s2 = TestUtils.buildStaff()
        s2.status == StaffStatus.STAFF
        Team t1 = TestUtils.buildTeam()
        t1.addToMembers(s1)
        t1.addToMembers(s2)
    	t1.save(flush: true, failOnError: true)
        Phone p1 = TestUtils.buildStaffPhone(s1)

    	when: "we have an individual phone ownership"
        Result res = PhoneOwnership.tryCreate(p1, s1.id, PhoneOwnershipType.INDIVIDUAL)

    	then:
        res.status == ResultStatus.CREATED
    	res.payload.buildAllStaff().size() == 1
    	res.payload.buildAllStaff()[0] == s1

    	when: "we have a group phone ownership"
    	res = PhoneOwnership.tryCreate(p1, t1.id, PhoneOwnershipType.GROUP)

    	then:
    	res.status == ResultStatus.CREATED
    	res.payload.buildAllStaff().size() == t1.members.size()
    	s1 in res.payload.buildAllStaff()
        s2 in res.payload.buildAllStaff()
    }

    void "test building active policies for certain notification frequency"() {
        given:
        Staff s1 = TestUtils.buildStaff()
        Staff s2 = TestUtils.buildStaff()
        Staff s3 = TestUtils.buildStaff()
        Staff s4 = TestUtils.buildStaff()
        Staff s5 = TestUtils.buildStaff()
        Team t1 = TestUtils.buildTeam()
        t1.addToMembers(s1)
        t1.addToMembers(s2)
        t1.addToMembers(s3)
        // note that `s4` is NOT CURRENTLY a part of this team but has a owner policy!

        Phone tp1 = TestUtils.buildTeamPhone(t1)
        OwnerPolicy op1 = TestUtils.buildOwnerPolicy(tp1.owner, s1)
        op1.frequency = DefaultOwnerPolicy.DEFAULT_FREQUENCY
        OwnerPolicy op2 = TestUtils.buildOwnerPolicy(tp1.owner, s2)
        op2.frequency = NotificationFrequency.QUARTER_HOUR
        // note that `s3` does NOT have an owner policy
        OwnerPolicy op4 = TestUtils.buildOwnerPolicy(tp1.owner, s4)
        op4.frequency = DefaultOwnerPolicy.DEFAULT_FREQUENCY
        OwnerPolicy op5 = TestUtils.buildOwnerPolicy(tp1.owner, s5)
        op5.frequency = NotificationFrequency.HOUR

        OwnerPolicy.withSession { it.flush() }

        when: "if no frequency provided"
        Collection policies = tp1.owner.buildActiveReadOnlyPolicies()

        then: "then we build all current and fill in missing"
        policies.size() == 3
        op1 in policies
        op2 in policies
        policies.find { it instanceof DefaultOwnerPolicy && it.readOnlyStaff == s3 }
        !(op4 in policies)
        !(op5 in policies)

        when: "default frequency"
        policies = tp1.owner.buildActiveReadOnlyPolicies(DefaultOwnerPolicy.DEFAULT_FREQUENCY)

        then: "get current with passed-in frequency and fill in missing"
        policies.size() == 2
        op1 in policies
        !(op2 in policies)
        policies.find { it instanceof DefaultOwnerPolicy && it.readOnlyStaff == s3 }
        !(op4 in policies)
        !(op5 in policies)

        when: "non-default frequency that some CURRENT policies have"
        policies = tp1.owner.buildActiveReadOnlyPolicies(op2.frequency)

        then: "get current with passed-in frequency and DO NOT fill in missing"
        policies.size() == 1
        !(op1 in policies)
        op2 in policies
        !(policies.find { it instanceof DefaultOwnerPolicy && it.readOnlyStaff == s3 })
        !(op4 in policies)
        !(op5 in policies)

        when: "non-default frequency that no CURRENT policies have"
        policies = tp1.owner.buildActiveReadOnlyPolicies(op5.frequency)

        then:
        policies == []
    }

    void "test building organization"() {
        given:
        Staff s1 = TestUtils.buildStaff()
        Phone p1 = TestUtils.buildStaffPhone(s1)
        Team t1 = TestUtils.buildTeam()

        when: "individual ownership"
        Result res = PhoneOwnership.tryCreate(p1, s1.id, PhoneOwnershipType.INDIVIDUAL)

        then:
        res.payload.buildOrganization().id == s1.org.id

        when: "group ownership"
        res = PhoneOwnership.tryCreate(p1, t1.id, PhoneOwnershipType.GROUP)

        then:
        res.payload.buildOrganization().id == t1.org.id
    }

    void "test building name"() {
        given: "team has all active staff"
        Staff s1 = TestUtils.buildStaff()
        Team t1 = TestUtils.buildTeam()
        Phone p1 = TestUtils.buildStaffPhone(s1)

        when: "we have an individual phone ownership"
        Result res = PhoneOwnership.tryCreate(p1, s1.id, PhoneOwnershipType.INDIVIDUAL)

        then:
        res.payload.buildName() == s1.name

        when: "we have a group phone ownership"
        res = PhoneOwnership.tryCreate(p1, t1.id, PhoneOwnershipType.GROUP)

        then:
        res.payload.buildName() == t1.name
    }
}
