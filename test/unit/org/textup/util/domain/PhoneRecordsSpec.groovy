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
class PhoneRecordsSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    @Unroll
    void "test closure for determining if active given phone is active = #phoneIsActive"() {
        given:
        Phone p1 = phoneIsActive ? TestUtils.buildActiveStaffPhone() : TestUtils.buildStaffPhone()

        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord(p1)
        IndividualPhoneRecord ipr2 = TestUtils.buildIndPhoneRecord(p1)
        ipr2.isDeleted = true

        GroupPhoneRecord gpr1 = TestUtils.buildGroupPhoneRecord(p1)
        GroupPhoneRecord gpr2 = TestUtils.buildGroupPhoneRecord(p1)
        gpr2.isDeleted = true

        PhoneRecord spr1 = TestUtils.buildSharedPhoneRecord(null, p1)
        PhoneRecord spr2 = TestUtils.buildSharedPhoneRecord(null, p1)
        spr2.dateExpired = DateTime.now().minusDays(1)

        PhoneRecord.withSession { it.flush() }

        when:
        DetachedCriteria criteria = new DetachedCriteria(PhoneRecord)
            .build {
                "in"("id", [ipr1, ipr2, gpr1, gpr2, spr1, spr2]*.id)
            }
            .build(PhoneRecords.forActive())

        then:
        if (phoneIsActive) {
            criteria.count() == 3
            criteria.list() == [ipr1, gpr1, spr1]
        }
        else { criteria.count() == 0 }

        where:
        phoneIsActive | _
        true          | _
        false         | _
    }

    void "test is allowed"() {
        given:
        Team t1 = TestUtils.buildTeam()
        Staff s1 = TestUtils.buildStaff()
        t1.addToMembers(s1)

        Phone tp1 = TestUtils.buildTeamPhone(t1)
        GroupPhoneRecord gpr1 = TestUtils.buildGroupPhoneRecord(tp1)

        MockedMethod tryGetAuthId = MockedMethod.create(AuthUtils, "tryGetAuthId") { Result.createSuccess(s1.id) }

        when:
        Result res = PhoneRecords.isAllowed(null)

        then:
        res.status == ResultStatus.FORBIDDEN

        when: "phone is not active"
        res = PhoneRecords.isAllowed(gpr1.id)

        then:
        res.status == ResultStatus.FORBIDDEN

        when: "phone is active"
        tp1.tryActivate(TestUtils.randPhoneNumber(), TestUtils.randString())
        tp1.save(flush: true, failOnError: true)

        res = PhoneRecords.isAllowed(gpr1.id)

        then:
        res.status == ResultStatus.OK
        res.payload == gpr1.id

        cleanup:
        tryGetAuthId.restore()
    }

    void "test criteria finding active for record ids"() {
        given:
        Phone p1 = TestUtils.buildActiveTeamPhone()

        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord(p1)
        IndividualPhoneRecord ipr2 = TestUtils.buildIndPhoneRecord(p1)
        ipr2.isDeleted = true

        PhoneRecord spr1 = TestUtils.buildSharedPhoneRecord(null, p1)
        PhoneRecord spr2 = TestUtils.buildSharedPhoneRecord(null, p1)
        spr2.dateExpired = DateTime.now().minusDays(1)

        PhoneRecord.withSession { it.flush() }

        when:
        DetachedCriteria criteria = PhoneRecords.buildActiveForRecordIds(null)

        then:
        criteria.count() == 0

        when:
        criteria = PhoneRecords.buildActiveForRecordIds([ipr1, ipr2, spr1, spr2]*.record*.id)

        then:
        criteria.list() == [ipr1, spr1]
    }

    void "test criteria finding active for phone ids"() {
        given:
        Phone p1 = TestUtils.buildStaffPhone()
        Phone p2 = TestUtils.buildActiveStaffPhone()

        GroupPhoneRecord gpr1 = TestUtils.buildGroupPhoneRecord(p1)
        GroupPhoneRecord gpr2 = TestUtils.buildGroupPhoneRecord(p2)

        when:
        DetachedCriteria criteria = PhoneRecords.buildActiveForPhoneIds(null)

        then:
        criteria.count() == 0

        when:
        criteria = PhoneRecords.buildActiveForPhoneIds([p1, p2]*.id)

        then:
        criteria.list() == [gpr2]
    }

    void "test criteria finding active for share source ids"() {
        given:
        Phone p1 = TestUtils.buildActiveStaffPhone()

        PhoneRecord spr1 = TestUtils.buildSharedPhoneRecord(null, p1)
        PhoneRecord spr2 = TestUtils.buildSharedPhoneRecord(null, p1)
        spr2.dateExpired = DateTime.now().minusDays(1)

        PhoneRecord.withSession { it.flush() }

        when:
        DetachedCriteria criteria = PhoneRecords.buildActiveForShareSourceIds(null)

        then:
        criteria.count() == 0

        when:
        criteria = PhoneRecords.buildActiveForShareSourceIds([spr1, spr2]*.shareSource*.id)

        then:
        criteria.list() == [spr1]
    }

    void "test finding all allowed record ids given staff id"() {
        given:
        Team t1 = TestUtils.buildTeam()
        Staff s1 = TestUtils.buildStaff()
        t1.addToMembers(s1)

        Phone tp1 = TestUtils.buildActiveTeamPhone(t1)
        Phone p1 = TestUtils.buildStaffPhone(s1)

        GroupPhoneRecord gpr1 = TestUtils.buildGroupPhoneRecord(tp1)
        GroupPhoneRecord gpr2 = TestUtils.buildGroupPhoneRecord(p1)

        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord(tp1)
        IndividualPhoneRecord ipr2 = TestUtils.buildIndPhoneRecord(tp1)
        ipr2.isDeleted = true

        PhoneRecord spr1 = TestUtils.buildSharedPhoneRecord(null, tp1)
        PhoneRecord spr2 = TestUtils.buildSharedPhoneRecord(null, tp1)
        spr2.dateExpired = DateTime.now().minusDays(1)

        PhoneRecord.withSession { it.flush() }

        when:
        Collection recIds = PhoneRecords.findEveryAllowedRecordIdForStaffId(null)

        then:
        recIds == []

        when:
        recIds = PhoneRecords.findEveryAllowedRecordIdForStaffId(s1.id)

        then: "personal phone is not active but team phone is"
        recIds == [gpr1, ipr1, spr1]*.record*.id
    }

    void "test closure for finding given ids"() {
        given:
        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord()
        GroupPhoneRecord gpr1 = TestUtils.buildGroupPhoneRecord()

        when:
        DetachedCriteria criteria = new DetachedCriteria(PhoneRecord)
            .build(PhoneRecords.forIds(null))

        then:
        criteria.count() == 0

        when:
        criteria = new DetachedCriteria(PhoneRecord).build(PhoneRecords.forIds([-88L]))

        then:
        criteria.count() == 0

        when:
        criteria = new DetachedCriteria(PhoneRecord).build(PhoneRecords.forIds([ipr1, gpr1]*.id))

        then:
        criteria.list() == [ipr1, gpr1]
    }
}
