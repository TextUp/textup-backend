package org.textup

import grails.test.mixin.*
import grails.test.mixin.gorm.*
import grails.test.mixin.hibernate.*
import grails.test.mixin.support.*
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
@TestMixin(HibernateTestMixin)
class TeamSpec extends Specification {

	static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test creation and constraints"() {
        given:
        Organization org1 = TestUtils.buildOrg()
        Organization org2 = TestUtils.buildOrg()
        Location loc1 = TestUtils.buildLocation()
        String name1 = TestUtils.randString()
        String name2 = TestUtils.randString()

        when:
        Result res = Team.tryCreate(null, null, null)

        then:
        res.status == ResultStatus.UNPROCESSABLE_ENTITY

        when:
        res = Team.tryCreate(org1, name1, loc1)

        then:
        res.status == ResultStatus.CREATED
        res.payload.name == name1
        res.payload.org == org1
        res.payload.location == loc1

        when: "duplicate"
        res = Team.tryCreate(org1, name1, loc1)

        then:
        res.status == ResultStatus.UNPROCESSABLE_ENTITY

        when: "distinct name"
        res = Team.tryCreate(org1, name2, loc1)

        then:
        res.status == ResultStatus.CREATED
        res.payload.name == name2

        when: "different org"
        res = Team.tryCreate(org2, name1, loc1)

        then:
        res.status == ResultStatus.CREATED
        res.payload.org == org2
    }

    void "test marking as deleted allows for adding teams with the same name"() {
        given:
        Organization org1 = TestUtils.buildOrg()
        Location loc1 = TestUtils.buildLocation()
        String name1 = TestUtils.randString()
        Team t1

        when:
        Result res = Team.tryCreate(org1, name1, loc1)

        then:
        res.status == ResultStatus.CREATED

        when: "duplicate"
        t1 = res.payload

        res = Team.tryCreate(org1, name1, loc1)

        then:
        res.status == ResultStatus.UNPROCESSABLE_ENTITY

        when: "mark as deleted and try again"
        t1.isDeleted = true
        t1.save(flush: true, failOnError: true)

        res = Team.tryCreate(org1, name1, loc1)

        then:
        res.status == ResultStatus.CREATED
    }

    void "test getting active members or by status"() {
        given:
        Staff blocked = TestUtils.buildStaff()
        blocked.status = StaffStatus.BLOCKED
        Staff pending = TestUtils.buildStaff()
        pending.status = StaffStatus.PENDING
        Staff staff = TestUtils.buildStaff()
        staff.status = StaffStatus.STAFF
        Staff admin = TestUtils.buildStaff()
        admin.status = StaffStatus.ADMIN

        Team t1 = TestUtils.buildTeam()
        t1.addToMembers(blocked)
        t1.addToMembers(pending)
        t1.addToMembers(staff)
        t1.addToMembers(admin)

        expect:
        t1.members.size() == 4
        t1.activeMembers.size() == 2

        t1.getMembersByStatus(null) == t1.members
        t1.getMembersByStatus([]) == t1.members
        t1.getMembersByStatus([StaffStatus.BLOCKED])*.id == [blocked]*.id
        t1.getMembersByStatus([StaffStatus.PENDING])*.id == [pending]*.id
        t1.getMembersByStatus([StaffStatus.STAFF])*.id == [staff]*.id
        t1.getMembersByStatus([StaffStatus.ADMIN])*.id == [admin]*.id
    }
}
