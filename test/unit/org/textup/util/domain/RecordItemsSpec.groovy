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
class RecordItemsSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test is allowed"() {
        given:
        Staff s1 = TestUtils.buildStaff()
        RecordItem rItem1 = TestUtils.buildRecordItem()

        MockedMethod tryGetAuthId = MockedMethod.create(AuthUtils, "tryGetAuthId") { Result.createSuccess(s1.id) }
        MockedMethod findEveryAllowedRecordIdForStaffId = MockedMethod.create(PhoneRecords, "findEveryAllowedRecordIdForStaffId") {
            []
        }

        when:
        Result res = RecordItems.isAllowed(null)

        then:
        res.status == ResultStatus.FORBIDDEN

        when:
        res = RecordItems.isAllowed(rItem1.id)

        then:
        findEveryAllowedRecordIdForStaffId.callCount > 0
        res.status == ResultStatus.FORBIDDEN

        when:
        findEveryAllowedRecordIdForStaffId.restore()
        findEveryAllowedRecordIdForStaffId = MockedMethod.create(PhoneRecords, "findEveryAllowedRecordIdForStaffId") {
            [rItem1.record.id]
        }
        res = RecordItems.isAllowed(rItem1.id)

        then:
        findEveryAllowedRecordIdForStaffId.callCount == 1
        res.status == ResultStatus.OK
        res.payload == rItem1.id

        cleanup:
        tryGetAuthId.restore()
        findEveryAllowedRecordIdForStaffId.restore()
    }

    void "test finding for id"() {
        given:
        RecordItem rItem1 = TestUtils.buildRecordItem()

        when:
        Result res = RecordItems.mustFindForId(null)

        then:
        res.status == ResultStatus.NOT_FOUND

        when:
        res = RecordItems.mustFindForId(rItem1.id)

        then:
        res.status == ResultStatus.OK
        res.payload == rItem1
    }

    void "test finding modifiable given id"() {
        given:
        Record rec1 = TestUtils.buildRecord()
        RecordNote rNote1 = new RecordNote(record: rec1)
        RecordNote rNote2 = new RecordNote(record: rec1, isReadOnly: true)
        [rNote1, rNote2]*.save(flush: true, failOnError: true)

        when:
        Result res = RecordItems.mustFindModifiableForId(null)

        then:
        res.status == ResultStatus.NOT_FOUND

        when:
        res = RecordItems.mustFindModifiableForId(rNote2.id)

        then:
        res.status == ResultStatus.FORBIDDEN

        when:
        res = RecordItems.mustFindModifiableForId(rNote1.id)

        then:
        res.status == ResultStatus.OK
        res.payload == rNote1
    }

    void "test finding for api id"() {
        given:
        RecordItem rItem1 = TestUtils.buildRecordItem()
        RecordItem rItem2 = TestUtils.buildRecordItem()
        RecordItem rItem3 = TestUtils.buildRecordItem()

        TempRecordReceipt tempRpt1 = TestUtils.buildTempReceipt()
        rItem1.addReceipt(tempRpt1)
        rItem2.addReceipt(tempRpt1)

        RecordItem.withSession { it.flush() }

        when:
        Collection rItems = RecordItems.findEveryForApiId(null)

        then:
        rItems.isEmpty()

        when:
        rItems = RecordItems.findEveryForApiId(tempRpt1.apiId)

        then:
        rItems == [rItem1, rItem2]
    }

    void "test criteria for phone id and options"() {
        given:
        DateTime dt = DateTime.now()
        Phone tp1 = TestUtils.buildActiveTeamPhone()
        GroupPhoneRecord gpr1 = TestUtils.buildGroupPhoneRecord(tp1)
        gpr1.status = PhoneRecordStatus.BLOCKED
        RecordText rText1 = new RecordText(record: gpr1.record, whenCreated: dt.minusDays(1))
        RecordCall rCall1 = new RecordCall(record: gpr1.record)
        RecordNote rNote1 = new RecordNote(record: gpr1.record, whenCreated: dt.plusDays(1))
        [rText1, rCall1, rNote1]*.save(flush: true, failOnError: true)

        when:
        DetachedCriteria criteria = RecordItems.buildForPhoneIdWithOptions(null)

        then:
        criteria.count() == 0

        when:
        criteria = RecordItems.buildForPhoneIdWithOptions(tp1.id)

        then:
        criteria.list() == [rText1, rCall1, rNote1]

        when:
        criteria = RecordItems.buildForPhoneIdWithOptions(tp1.id, dt.minusHours(1), dt.plusHours(1))

        then:
        criteria.list() == [rCall1]

        when:
        criteria = RecordItems.buildForPhoneIdWithOptions(tp1.id, dt.minusHours(1),
            dt.plusHours(1), [RecordNote])

        then:
        criteria.count() == 0

        when:
        criteria = RecordItems.buildForPhoneIdWithOptions(tp1.id, null, null, [RecordNote])

        then:
        criteria.list() == [rNote1]
    }

    void "test finding for record id with options"() {
        given:
        DateTime dt = DateTime.now()
        GroupPhoneRecord gpr1 = TestUtils.buildGroupPhoneRecord()
        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord()
        RecordText rText1 = new RecordText(record: gpr1.record, whenCreated: dt.minusDays(1))
        RecordText rText2 = new RecordText(record: ipr1.record)
        RecordCall rCall1 = new RecordCall(record: gpr1.record)
        RecordNote rNote1 = new RecordNote(record: gpr1.record, whenCreated: dt.plusDays(1))
        RecordNote rNote2 = new RecordNote(record: ipr1.record)
        [rText1, rText2, rCall1, rNote1, rNote2]*.save(flush: true, failOnError: true)

        when:
        DetachedCriteria criteria = RecordItems.buildForRecordIdsWithOptions(null)

        then:
        criteria.count() == 0

        when:
        criteria = RecordItems.buildForRecordIdsWithOptions([gpr1, ipr1]*.record*.id)
        Collection rItems = criteria.list()

        then:
        [rText1, rText2, rCall1, rNote1, rNote2].every { it in rItems }

        when:
        criteria = RecordItems.buildForRecordIdsWithOptions([gpr1.record.id], dt.minusHours(1), dt.plusHours(1))

        then:
        criteria.list() == [rCall1]

        when:
        criteria = RecordItems.buildForRecordIdsWithOptions([gpr1.record.id], null, dt.plusHours(1))
        rItems = criteria.list()

        then:
        [rText1, rCall1].every { it in rItems }
        !(rNote1 in rItems)

        when:
        criteria = RecordItems.buildForRecordIdsWithOptions([gpr1.record.id], dt.minusHours(1), null)
        rItems = criteria.list()

        then:
        [rCall1, rNote1].every { it in rItems }
        !(rText1 in rItems)

        when:
        criteria = RecordItems.buildForRecordIdsWithOptions([gpr1, ipr1]*.record*.id, dt.minusHours(1),
            dt.plusHours(1), [RecordNote])

        then:
        criteria.list() == [rNote2]

        when:
        criteria = RecordItems.buildForRecordIdsWithOptions([gpr1.record.id], null, null, [RecordNote])

        then:
        criteria.list() == [rNote1]
    }

    void "test sorting"() {
        given:
        DateTime dt = DateTime.now()
        Record rec1 = TestUtils.buildRecord()
        RecordItem rItem1 = new RecordItem(record: rec1, whenCreated: dt.minusDays(2))
        RecordItem rItem2 = new RecordItem(record: rec1, whenCreated: dt.minusDays(1))
        RecordItem rItem3 = new RecordItem(record: rec1, whenCreated: dt)
        RecordItem rItem4 = new RecordItem(record: rec1, whenCreated: dt.plusDays(1))

        [rItem1, rItem2, rItem3, rItem4]*.save(flush: true, failOnError: true)

        when:
        DetachedCriteria criteria = new DetachedCriteria(RecordItem).build { eq("record", rec1) }

        then:
        criteria.build(RecordItems.forSort()).list() == [rItem4, rItem3, rItem2, rItem1]
        criteria.build(RecordItems.forSort(true)).list() == [rItem4, rItem3, rItem2, rItem1]
        criteria.build(RecordItems.forSort(false)).list() == [rItem1, rItem2, rItem3, rItem4]
    }

    void "test building for incoming"() {
        given:
        Record rec1 = TestUtils.buildRecord()
        RecordItem rItem1 = TestUtils.buildRecordItem(rec1)
        rItem1.outgoing = true
        RecordItem rItem2 = TestUtils.buildRecordItem(rec1)
        rItem2.outgoing = false
        RecordItem.withSession { it.flush() }

        when:
        Collection found = new DetachedCriteria(RecordItem)
            .build { eq("record", rec1) }
            .build(RecordItems.forIncoming())
            .list()

        then:
        found.size() == 1
        rItem2 in found
    }

    void "test building and finding for non group owners only"() {
        given:
        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord()
        GroupPhoneRecord gpr1 = TestUtils.buildGroupPhoneRecord()
        PhoneRecord spr1 = TestUtils.buildSharedPhoneRecord()

        RecordItem rItem1 = TestUtils.buildRecordItem(ipr1.record)
        RecordItem rItem2 = TestUtils.buildRecordItem(gpr1.record)
        RecordItem rItem3 = TestUtils.buildRecordItem(spr1.record)

        DetachedCriteria criteria = new DetachedCriteria(RecordItem)
            .build { "in"("record", [ipr1, gpr1, spr1]*.record) }

        when:
        Collection found1 = criteria.list()
        Collection found2 = criteria.build(RecordItems.forNonGroupOwnerOnly()).list()

        then:
        found1.size() == 3
        [rItem1, rItem2, rItem3].every { it in found1 }
        found2.size() == 2
        [rItem1, rItem3].every { it in found2 }

        when:
        Collection found3 = RecordItems.findAllByNonGroupOwner([rItem1, rItem2, rItem3])

        then:
        found3.size() == 2
        [rItem1, rItem3].every { it in found3 }

        expect:
        RecordItems.findAllByNonGroupOwner(null) == []
    }

    void "test criteria for outgoing scheduled and incoming messages (not notes) after a certain time"() {
        given:
        DateTime dt = DateTime.now().minusDays(1)
        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord()
        GroupPhoneRecord gpr1 = TestUtils.buildGroupPhoneRecord()

        Collection willFindItems = [
            new RecordCall(record: ipr1.record, outgoing: false),
            new RecordCall(record: ipr1.record, outgoing: true, wasScheduled: true),
            new RecordText(record: ipr1.record, outgoing: false),
            new RecordText(record: ipr1.record, outgoing: true, wasScheduled: true)
        ]
        Collection notFindItems = [
            new RecordCall(record: ipr1.record, outgoing: true),
            new RecordText(record: ipr1.record, outgoing: false, whenCreated: dt.minusDays(1)),
            new RecordText(record: ipr1.record, outgoing: true, whenCreated: dt.minusDays(1)),
            new RecordNote(record: ipr1.record, outgoing: false),
            new RecordNote(record: ipr1.record, outgoing: true),
            new RecordCall(record: gpr1.record, outgoing: false),
            new RecordCall(record: gpr1.record, outgoing: true, wasScheduled: true),
            new RecordText(record: gpr1.record, outgoing: false),
            new RecordText(record: gpr1.record, outgoing: true, wasScheduled: true)
        ]
        willFindItems*.save(flush: true, failOnError: true)
        notFindItems*.save(flush: true, failOnError: true)

        when:
        DetachedCriteria criteria = RecordItems.buildForOutgoingScheduledOrIncomingMessagesAfter(null)

        then:
        criteria.count() == 0

        when:
        criteria = RecordItems.buildForOutgoingScheduledOrIncomingMessagesAfter(dt)
            .build { "in"("record", [ipr1, gpr1]*.record) }
        Collection rItems = criteria.list()

        then:
        rItems.size() == willFindItems.size()
        willFindItems.every { it in rItems }
        notFindItems.every { !(it in rItems) }
    }
}
