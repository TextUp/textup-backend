package org.textup.util.domain

import grails.gorm.DetachedCriteria
import grails.test.mixin.*
import grails.test.mixin.gorm.*
import grails.test.mixin.hibernate.*
import org.joda.time.*
import org.textup.*
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
class TeamsSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test finding for id"() {
        given:
        Team t1 = TestUtils.buildTeam()

        when:
        Result res = Teams.mustFindForId(null)

        then:
        res.status == ResultStatus.NOT_FOUND

        when:
        res = Teams.mustFindForId(-88L)

        then:
        res.status == ResultStatus.NOT_FOUND

        when:
        res = Teams.mustFindForId(t1.id)

        then:
        res.status == ResultStatus.OK
        res.payload == t1
    }

    void "test criteria for active given org ids"() {
        given:
        Organization org1 = TestUtils.buildOrg()
        Team t1 = TestUtils.buildTeam(org1)
        Team t2 = TestUtils.buildTeam(org1)
        t2.isDeleted = true

        Team.withSession { it.flush() }

        when:
        DetachedCriteria criteria = Teams.buildActiveForOrgIds(null)

        then:
        criteria.count() == 0

        when:
        criteria = Teams.buildActiveForOrgIds([-88L])

        then:
        criteria.count() == 0

        when:
        criteria = Teams.buildActiveForOrgIds([t1.org.id])

        then:
        criteria.list() == [t1]
    }

    void "test criteria for active given staff ids"() {
        given:
        Organization org1 = TestUtils.buildOrg()
        Team t1 = TestUtils.buildTeam(org1)
        Team t2 = TestUtils.buildTeam(org1)
        t2.isDeleted = true

        Staff s1 = TestUtils.buildStaff()
        t1.addToMembers(s1)
        Staff s2 = TestUtils.buildStaff()

        when:
        DetachedCriteria criteria = Teams.buildActiveForStaffIds(null)

        then:
        criteria.count() == 0

        when:
        criteria = Teams.buildActiveForStaffIds([s2.id])

        then:
        criteria.count() == 0

        when:
        criteria = Teams.buildActiveForStaffIds([s1.id])

        then:
        criteria.list() == [t1]
    }

    void "test criteria for active given org id and team name"() {
        given:
        Organization org1 = TestUtils.buildOrg()
        Team t1 = TestUtils.buildTeam(org1)
        Team t2 = TestUtils.buildTeam(org1)
        t2.isDeleted = true

        Team.withSession { it.flush() }

        when:
        DetachedCriteria criteria = Teams.buildActiveForOrgIdAndName(null, null)

        then:
        criteria.count() == 0

        when:
        criteria = Teams.buildActiveForOrgIdAndName(org1.id, t2.name)

        then: "team is not active"
        criteria.count() == 0

        when:
        criteria = Teams.buildActiveForOrgIdAndName(org1.id, t1.name)

        then:
        criteria.list() == [t1]
    }

    void "test is allowed"() {
        given:
        Organization org1 = TestUtils.buildOrg()

        Team t1 = TestUtils.buildTeam(org1)
        Team t2 = TestUtils.buildTeam(org1)
        t2.isDeleted = true

        Staff s1 = TestUtils.buildStaff()
        Staff s2 = TestUtils.buildStaff(org1)
        s2.status = StaffStatus.STAFF
        Staff s3 = TestUtils.buildStaff(org1)
        s3.status = StaffStatus.ADMIN

        Team.withSession { it.flush() }

        MockedMethod tryGetAuthId = TestUtils.mock(AuthUtils, "tryGetAuthId") { Result.createSuccess(s1.id) }

        when:
        Result res = Teams.isAllowed(null)

        then:
        tryGetAuthId.callCount == 1
        res.status == ResultStatus.FORBIDDEN

        when:
        res = Teams.isAllowed(t1.id)

        then: "staff is at different org"
        tryGetAuthId.callCount == 2
        res.status == ResultStatus.FORBIDDEN

        when:
        tryGetAuthId.restore()
        tryGetAuthId = TestUtils.mock(AuthUtils, "tryGetAuthId") { Result.createSuccess(s2.id) }
        res = Teams.isAllowed(t1.id)

        then: "staff is not admin"
        tryGetAuthId.callCount == 1
        res.status == ResultStatus.FORBIDDEN

        when:
        tryGetAuthId.restore()
        tryGetAuthId = TestUtils.mock(AuthUtils, "tryGetAuthId") { Result.createSuccess(s3.id) }
        res = Teams.isAllowed(t1.id)

        then: "staff is admin"
        tryGetAuthId.callCount == 1
        res.status == ResultStatus.OK
        res.payload == t1.id

        when: "team is deleted"
        res = Teams.isAllowed(t2.id)

        then: "admin still has permission even if team is deleted"
        tryGetAuthId.callCount == 2
        res.status == ResultStatus.OK
        res.payload == t2.id

        cleanup:
        tryGetAuthId.restore()
    }

    void "test determining if has teams in common and if team contains member"() {
        given:
        Team t1 = TestUtils.buildTeam()
        Team t2 = TestUtils.buildTeam()

        Staff s1 = TestUtils.buildStaff()
        t1.addToMembers(s1)
        Staff s2 = TestUtils.buildStaff()
        t1.addToMembers(s2)
        t2.addToMembers(s2)
        Staff s3 = TestUtils.buildStaff()
        t2.addToMembers(s3)

        Team.withSession { it.flush() }

        expect:
        Teams.hasTeamsInCommon(null, null) == false
        Teams.hasTeamsInCommon(s1.id, s1.id)
        Teams.hasTeamsInCommon(s1.id, s2.id)
        Teams.hasTeamsInCommon(s1.id, s3.id) == false
        Teams.hasTeamsInCommon(s2.id, s3.id)

        and:
        Teams.teamContainsMember(null, null) == false
        Teams.teamContainsMember(t1.id, s1.id)
        Teams.teamContainsMember(t1.id, s2.id)
        Teams.teamContainsMember(t1.id, s3.id) == false
        Teams.teamContainsMember(t2.id, s1.id) == false
        Teams.teamContainsMember(t2.id, s2.id)
        Teams.teamContainsMember(t2.id, s3.id)
    }
}
