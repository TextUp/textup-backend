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

        then: "fail on nonexistent"
        res.status == ResultStatus.UNPROCESSABLE_ENTITY

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
        Phone p1 = TestUtils.buildStaffPhone(s1)
        Phone p2 = TestUtils.buildStaffPhone(s2)
        PhoneOwnership noPolicesOwn1 = p1.owner
        PhoneOwnership own1 = p2.owner
        OwnerPolicy op1 = TestUtils.buildOwnerPolicy(own1, s1)
        OwnerPolicy op2 = TestUtils.buildOwnerPolicy(own1, s2)
        op2.frequency = NotificationFrequency.QUARTER_HOUR

        when:
        Collection policies = noPolicesOwn1.buildActiveReadOnlyPoliciesForFrequency(null)

        then:
        policies == []

        when:
        policies = noPolicesOwn1.buildActiveReadOnlyPoliciesForFrequency(DefaultOwnerPolicy.DEFAULT_FREQUENCY)

        then: "create a default policy for `s1`"
        policies.size() == 1
        policies[0] instanceof DefaultOwnerPolicy
        policies[0].readOnlyStaff == s1

        when:
        policies = own1.buildActiveReadOnlyPoliciesForFrequency(null)

        then:
        policies == []

        when:
        policies = own1.buildActiveReadOnlyPoliciesForFrequency(NotificationFrequency.QUARTER_HOUR)

        then:
        policies.size() == 1
        !(policies[0] instanceof DefaultOwnerPolicy)
        policies[0] == op2

        when:
        policies = own1.buildActiveReadOnlyPoliciesForFrequency(DefaultOwnerPolicy.DEFAULT_FREQUENCY)

        then: "create a default policy for `s2`"
        policies.size() == 1
        policies[0] instanceof DefaultOwnerPolicy
        policies[0].readOnlyStaff == s2
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
