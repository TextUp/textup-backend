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
class GroupPhoneRecordsSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test finding for id"() {
        given:
        GroupPhoneRecord gpr1 = TestUtils.buildGroupPhoneRecord()

        when:
        Result res = GroupPhoneRecords.mustFindForId(null)

        then:
        res.status == ResultStatus.NOT_FOUND

        when:
        res = GroupPhoneRecords.mustFindForId(gpr1.id)

        then:
        res.status == ResultStatus.OK
        res.payload == gpr1
    }

    void "test criteria for phone id and options"() {
        given:
        Phone tp1 = TestUtils.buildActiveTeamPhone()
        Phone tp2 = TestUtils.buildTeamPhone()
        GroupPhoneRecord gpr1 = TestUtils.buildGroupPhoneRecord(tp1)
        GroupPhoneRecord gpr2 = TestUtils.buildGroupPhoneRecord(tp2)

        when:
        DetachedCriteria criteria = GroupPhoneRecords.buildForPhoneIdAndOptions(null)

        then:
        criteria.count() == 0

        when:
        criteria = GroupPhoneRecords.buildForPhoneIdAndOptions(tp2.id)

        then:
        criteria.count() == 0

        when:
        criteria = GroupPhoneRecords.buildForPhoneIdAndOptions(tp1.id)

        then:
        criteria.list() == [gpr1]

        when:
        criteria = GroupPhoneRecords.buildForPhoneIdAndOptions(tp1.id, gpr1.name)

        then:
        criteria.list() == [gpr1]

        when:
        criteria = GroupPhoneRecords.buildForPhoneIdAndOptions(tp1.id, TestUtils.randString())

        then:
        criteria.count() == 0
    }

    @Unroll
    void "test criteria for phone record member ids and options where phone is active: #phoneIsActive"() {
        given:
        Phone tp1 = phoneIsActive ? TestUtils.buildActiveTeamPhone() : TestUtils.buildTeamPhone()
        GroupPhoneRecord gpr1 = TestUtils.buildGroupPhoneRecord(tp1)

        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord(tp1)
        gpr1.members.addToPhoneRecords(ipr1)
        IndividualPhoneRecord ipr2 = TestUtils.buildIndPhoneRecord(tp1)
        gpr1.members.addToPhoneRecords(ipr2)
        ipr2.isDeleted = true

        PhoneRecord spr1 = TestUtils.buildSharedPhoneRecord(null, tp1)
        gpr1.members.addToPhoneRecords(spr1)
        PhoneRecord spr2 = TestUtils.buildSharedPhoneRecord(null, tp1)
        gpr1.members.addToPhoneRecords(spr2)
        spr2.dateExpired = DateTime.now().minusDays(1)

        PhoneRecord.withSession { it.flush() }

        when:
        DetachedCriteria criteria = GroupPhoneRecords.buildForMemberIdsAndOptions(null)

        then:
        criteria.count() == 0

        when:
        criteria = GroupPhoneRecords.buildForMemberIdsAndOptions([ipr1, ipr2, spr1, spr2]*.id)

        then:
        phoneIsActive ? criteria.list() == [gpr1] : criteria.count() == 0

        when:
        criteria = GroupPhoneRecords.buildForMemberIdsAndOptions([ipr2, spr2]*.id)

        then:
        criteria.count() == 0

        when:
        criteria = GroupPhoneRecords.buildForMemberIdsAndOptions([ipr1, spr1]*.id, tp1.id)

        then:
        phoneIsActive ? criteria.list() == [gpr1] : criteria.count() == 0

        when:
        criteria = GroupPhoneRecords.buildForMemberIdsAndOptions([ipr1, spr1]*.id, -88L)

        then:
        criteria.count() == 0

        where:
        phoneIsActive | _
        true          | _
        false         | _
    }

    void "test ordering"() {
        given:
        Phone p1 = TestUtils.buildStaffPhone()
        GroupPhoneRecord gpr1 = TestUtils.buildGroupPhoneRecord(p1)
        gpr1.name = "abc"
        GroupPhoneRecord gpr2 = TestUtils.buildGroupPhoneRecord(p1)
        gpr2.name = "abc"

        GroupPhoneRecord.withSession { it.flush() }

        when:
        DetachedCriteria criteria = new DetachedCriteria(GroupPhoneRecord).build { eq("phone", p1) }

        then:
        criteria.build(GroupPhoneRecords.forSort()).list() == [gpr1, gpr2]
    }
}
