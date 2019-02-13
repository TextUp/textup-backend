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
class PhonesSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test active criteria"() {
        given:
        Phone inactive = TestUtils.buildStaffPhone()
        Phone active = TestUtils.buildActiveStaffPhone()

        when:
        DetachedCriteria criteria = new DetachedCriteria(Phone).build {
            "in"("id", [active, inactive]*.id)
        }

        then:
        criteria.count() == 2
        criteria.build(Phones.forActive()).count() == 1
        criteria.build(Phones.forActive()).list() == [active]
    }

    void "test finding active given id"() {
        given:
        Phone inactive = TestUtils.buildStaffPhone()
        Phone active = TestUtils.buildActiveStaffPhone()

        when:
        Result res = Phones.mustFindActiveForId(null)

        then:
        res.status == ResultStatus.NOT_FOUND

        when:
        res = Phones.mustFindActiveForId(inactive.id)

        then:
        res.status == ResultStatus.NOT_FOUND

        when:
        res = Phones.mustFindActiveForId(active.id)

        then:
        res.status == ResultStatus.OK
        res.payload == active
    }

    void "test finding active given phone number"() {
        given:
        Phone inactive = TestUtils.buildStaffPhone()
        Phone active = TestUtils.buildActiveStaffPhone()

        when:
        Result res = Phones.mustFindActiveForNumber(null)

        then:
        res.status == ResultStatus.NOT_FOUND

        when:
        res = Phones.mustFindActiveForNumber(inactive.number)

        then:
        res.status == ResultStatus.NOT_FOUND

        when:
        res = Phones.mustFindActiveForNumber(active.number)

        then:
        res.status == ResultStatus.OK
        res.payload == active
    }

    void "test finding active given owner"() {
        given:
        Team t1 = TestUtils.buildTeam()
        int pBaseline = Phone.count()
        PhoneCache pCache = GroovyMock()
        IOCUtils.metaClass."static".getPhoneCache = { -> pCache }

        when:
        Result res = Phones.mustFindActiveForOwner(t1.id, PhoneOwnershipType.GROUP, false)

        then:
        1 * pCache.mustFindAnyPhoneIdForOwner(t1.id, PhoneOwnershipType.GROUP) >>
            Result.createError([], ResultStatus.NOT_FOUND)
        res.status == ResultStatus.NOT_FOUND
        Phone.count() == pBaseline

        when:
        res = Phones.mustFindActiveForOwner(t1.id, PhoneOwnershipType.GROUP, true)

        then:
        1 * pCache.mustFindAnyPhoneIdForOwner(t1.id, PhoneOwnershipType.GROUP) >>
            Result.createError([], ResultStatus.NOT_FOUND)
        res.status == ResultStatus.CREATED
        res.payload instanceof Phone
        Phone.count() == pBaseline + 1

        when:
        res.payload.tryActivate(TestUtils.randPhoneNumber(), TestUtils.randString())
        res.payload.save(flush: true, failOnError: true)

        Result res2 = Phones.mustFindActiveForOwner(t1.id, PhoneOwnershipType.GROUP, false)

        then:
        1 * pCache.mustFindAnyPhoneIdForOwner(t1.id, PhoneOwnershipType.GROUP) >>
            Result.createSuccess(res.payload.id)
        res2.status == ResultStatus.OK
        res2.payload == res.payload
        Phone.count() == pBaseline + 1
    }

    void "test criteria for active given phone number"() {
        given:
        Phone inactive = TestUtils.buildStaffPhone()
        Phone active = TestUtils.buildActiveStaffPhone()

        when:
        DetachedCriteria criteria = Phones.buildActiveForNumber(null)

        then: "zero because not active"
        criteria.count() == 0

        when:
        criteria = Phones.buildActiveForNumber(inactive.number)

        then: "zero because not active"
        criteria.count() == 0

        when:
        criteria = Phones.buildActiveForNumber(active.number)

        then: "find one or more inactive phones"
        criteria.list() == [active]
    }

    void "test criteria for any phone given owner"() {
        given:
        Phone inactive = TestUtils.buildStaffPhone()
        Phone active = TestUtils.buildActiveStaffPhone()

        when:
        DetachedCriteria criteria = Phones.buildAnyForOwnerIdAndType(null, null)

        then:
        criteria.count() == 0

        when:
        criteria = Phones.buildAnyForOwnerIdAndType(inactive.owner.ownerId, inactive.owner.type)

        then:
        criteria.list() == [inactive]

        when:
        criteria = Phones.buildAnyForOwnerIdAndType(active.owner.ownerId, active.owner.type)

        then:
        criteria.list() == [active]
    }

    void "test criteria for all active team and staff phones given staff id"() {
        given:
        Team t1 = TestUtils.buildTeam()
        Staff s1 = TestUtils.buildStaff()
        t1.addToMembers(s1)
        Phone p1 = TestUtils.buildTeamPhone(t1)
        Phone p2 = TestUtils.buildActiveStaffPhone(s1)

        when:
        DetachedCriteria criteria = new DetachedCriteria(PhoneOwnership)
            .build(Phones.activeForPhonePropNameAndStaffId("phone", null))

        then:
        criteria.count() == 0

        when:
        criteria = new DetachedCriteria(PhoneOwnership)
            .build(Phones.activeForPhonePropNameAndStaffId("phone", s1.id))

        then: "only personal phone because team phone is inactive"
        criteria.list() == [p2]*.owner

        when:
        p1.tryActivate(TestUtils.randPhoneNumber(), TestUtils.randString())
        p1.save(flush: true, failOnError: true)
        criteria = new DetachedCriteria(PhoneOwnership)
            .build(Phones.activeForPhonePropNameAndStaffId("phone", s1.id))

        then:
        criteria.list() == [p1, p2]*.owner
    }

    void "test determining if can share from an individual phone"() {
        given:
        Team t1 = TestUtils.buildTeam()
        Team t2 = TestUtils.buildTeam()

        Staff s1 = TestUtils.buildStaff()
        t1.addToMembers(s1)
        Staff s2 = TestUtils.buildStaff()
        t1.addToMembers(s2)
        Staff s3 = TestUtils.buildStaff()

        Phone tp1 = TestUtils.buildTeamPhone(t1)
        Phone tp2 = TestUtils.buildTeamPhone(t2)
        Phone p1 = TestUtils.buildStaffPhone(s1)
        Phone p2 = TestUtils.buildStaffPhone(s2)
        Phone p3 = TestUtils.buildStaffPhone(s3)

        when:
        Result res = Phones.canShare(null, null)

        then:
        res.status == ResultStatus.FORBIDDEN

        when: "ind -> ind"
        res = Phones.canShare(p1.owner, p3.owner)

        then:
        res.status == ResultStatus.FORBIDDEN

        when: "ind -> ind"
        res = Phones.canShare(p1.owner, p2.owner)

        then:
        res.status == ResultStatus.NO_CONTENT

        when: "ind -> group"
        res = Phones.canShare(p1.owner, tp2.owner)

        then:
        res.status == ResultStatus.FORBIDDEN

        when: "ind -> group"
        res = Phones.canShare(p1.owner, tp1.owner)

        then:
        res.status == ResultStatus.NO_CONTENT
    }

    void "test determining if can share from a team phone"() {
        given:
        Team t1 = TestUtils.buildTeam()
        Team t2 = TestUtils.buildTeam()

        Staff s1 = TestUtils.buildStaff()
        t1.addToMembers(s1)
        Staff s2 = TestUtils.buildStaff()

        Phone tp1 = TestUtils.buildTeamPhone(t1)
        tp1.owner.allowSharingWithOtherTeams = false
        Phone tp2 = TestUtils.buildTeamPhone(t2)
        tp2.owner.allowSharingWithOtherTeams = true
        Phone p1 = TestUtils.buildStaffPhone(s1)
        Phone p2 = TestUtils.buildStaffPhone(s2)

        when: "group -> ind"
        Result res = Phones.canShare(tp1.owner, p2.owner)

        then:
        res.status == ResultStatus.FORBIDDEN

        when: "group -> ind"
        res = Phones.canShare(tp1.owner, p1.owner)

        then:
        res.status == ResultStatus.NO_CONTENT

        when: "group -> group"
        res = Phones.canShare(tp1.owner, tp2.owner)

        then:
        res.status == ResultStatus.FORBIDDEN

        when: "group -> group"
        res = Phones.canShare(tp2.owner, tp1.owner)

        then:
        res.status == ResultStatus.NO_CONTENT
    }
}
