package org.textup.util.domain

import grails.gorm.DetachedCriteria
import grails.test.mixin.*
import grails.test.mixin.gorm.*
import grails.test.mixin.hibernate.*
import org.joda.time.*
import org.textup.*
import org.textup.cache.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.validator.*
import org.textup.util.*
import spock.lang.*

@Domain([AnnouncementReceipt, ContactNumber, CustomAccountDetails, FeaturedAnnouncement,
    FutureMessage, GroupPhoneRecord, IncomingSession, IndividualPhoneRecord, Location, MediaElement,
    MediaElementVersion, MediaInfo, Organization, OwnerPolicy, Phone, PhoneNumberHistory,
    PhoneOwnership, PhoneRecord, PhoneRecordMembers, Record, RecordCall, RecordItem,
    RecordItemReceipt, RecordNote, RecordNoteRevision, RecordText, Role, Schedule,
    SimpleFutureMessage, Staff, StaffRole, Team, Token])
@TestMixin(HibernateTestMixin)
class StaffsSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
        phoneCache(PhoneCache)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test is allowed"() {
        given:
        Organization org1 = TestUtils.buildOrg(OrgStatus.APPROVED)

        Staff s1 = TestUtils.buildStaff(org1)
        s1.status = StaffStatus.ADMIN
        Staff s2 = TestUtils.buildStaff(org1)
        Staff s3 = TestUtils.buildStaff()

        MockedMethod tryGetAuthId = MockedMethod.create(AuthUtils, "tryGetAuthId") { Result.createSuccess(s1.id) }

        when:
        Result res = Staffs.isAllowed(null)

        then:
        res.status == ResultStatus.FORBIDDEN

        when:
        res = Staffs.isAllowed(s1.id)

        then:
        res.status == ResultStatus.OK
        res.payload == s1.id

        when:
        res = Staffs.isAllowed(s2.id)

        then:
        res.status == ResultStatus.OK
        res.payload == s2.id

        when:
        res = Staffs.isAllowed(s3.id)

        then:
        res.status == ResultStatus.FORBIDDEN

        cleanup:
        tryGetAuthId.restore()
    }

    void "test finding by id"() {
        given:
        Staff s1 = TestUtils.buildStaff()

        when:
        Result res = Staffs.mustFindForId(null)

        then:
        res.status == ResultStatus.NOT_FOUND

        when:
        res = Staffs.mustFindForId(s1.id)

        then:
        res.status == ResultStatus.OK
        res.payload == s1
    }

    void "test finding for username"() {
        given:
        Staff s1 = TestUtils.buildStaff()

        when:
        Result res = Staffs.mustFindForUsername(null)

        then:
        res.status == ResultStatus.NOT_FOUND

        when:
        res = Staffs.mustFindForUsername(s1.username)

        then:
        res.status == ResultStatus.OK
        res.payload == s1
    }

    void "test criteria for ids and statuses"() {
        given:
        Staff s1 = TestUtils.buildStaff()
        s1.status = StaffStatus.STAFF

        when:
        DetachedCriteria criteria = Staffs.buildForIdsAndStatuses(null, null)

        then:
        criteria.count() == 0

        when:
        criteria = Staffs.buildForIdsAndStatuses([s1.id], [StaffStatus.PENDING])

        then:
        criteria.count() == 0

        when:
        criteria = Staffs.buildForIdsAndStatuses([s1.id], [s1.status])

        then:
        criteria.list() == [s1]
    }

    void "test criteria for org id and other options"() {
        given:
        Organization org1 = TestUtils.buildOrg()
        Staff s1 = TestUtils.buildStaff(org1)
        s1.status = StaffStatus.STAFF
        Staff s2 = TestUtils.buildStaff(org1)
        s2.status = StaffStatus.PENDING

        Staff.withSession { it.flush() }

        when:
        DetachedCriteria criteria = Staffs.buildForOrgIdAndOptions(null)

        then:
        criteria.count() == 0

        when:
        criteria = Staffs.buildForOrgIdAndOptions(org1.id)

        then: "defaults to active statuses"
        criteria.list() == [s1]
        criteria.build(Staffs.returnsOrgId()).list() == [org1.id]

        when:
        criteria = Staffs.buildForOrgIdAndOptions(org1.id, s1.personalNumberAsString)

        then:
        criteria.list() == [s1]

        when:
        criteria = Staffs.buildForOrgIdAndOptions(org1.id, TestConstants.TEST_DEFAULT_AREA_CODE)

        then:
        criteria.list() == [s1]

        when:
        criteria = Staffs.buildForOrgIdAndOptions(org1.id, null, [StaffStatus.PENDING])

        then:
        criteria.list() == [s2]

        when:
        criteria = Staffs.buildForOrgIdAndOptions(org1.id, s2.name, [StaffStatus.STAFF])

        then:
        criteria.count() == 0
    }

    void "test finding every staff member that the given staff id can share with"() {
        given:
        Team t1 = TestUtils.buildTeam()
        Team t2 = TestUtils.buildTeam()

        Staff s1 = TestUtils.buildStaff()
        s1.status = StaffStatus.STAFF
        t1.addToMembers(s1)
        t2.addToMembers(s1)
        Staff s2 = TestUtils.buildStaff()
        s2.status = StaffStatus.STAFF
        t1.addToMembers(s2)
        Staff s3 = TestUtils.buildStaff()
        s3.status = StaffStatus.STAFF
        t1.addToMembers(s3)
        t2.addToMembers(s3)
        Staff s4 = TestUtils.buildStaff()
        s4.status = StaffStatus.STAFF
        t2.addToMembers(s4)
        Staff s5 = TestUtils.buildStaff()
        s5.status = StaffStatus.STAFF

        Phone p1 = TestUtils.buildActiveStaffPhone(s2)
        Phone p2 = TestUtils.buildActiveStaffPhone(s4)

        when:
        Collection staffs = Staffs.findEveryForSharingId(null)

        then:
        staffs.isEmpty()

        when:
        staffs = Staffs.findEveryForSharingId(s1.id)

        then: "only staffs that have an individual TextUp phone"
        s2 in staffs
        !(s3 in staffs)
        s4 in staffs
        !(s5 in staffs)
    }

    void "test find every staff member that has access to the given record ids"() {
        given:
        Team t1 = TestUtils.buildTeam()
        Team t2 = TestUtils.buildTeam()

        Staff s1 = TestUtils.buildStaff()
        t1.addToMembers(s1)
        Staff s2 = TestUtils.buildStaff()
        t2.addToMembers(s2)

        Phone tp1 = TestUtils.buildActiveTeamPhone(t1)
        Phone tp2 = TestUtils.buildTeamPhone(t2)

        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord(tp2)
        ipr1.status = PhoneRecordStatus.BLOCKED
        PhoneRecord pr1 = TestUtils.buildSharedPhoneRecord(ipr1, tp1)

        when:
        Collection staffs = Staffs.findEveryForRecordIds(null)

        then:
        staffs.isEmpty()

        when:
        staffs = Staffs.findEveryForRecordIds([ipr1.record.id, null])

        then:
        staffs == [s1]
    }
}
