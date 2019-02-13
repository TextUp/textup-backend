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

        MockedMethod tryGetAuthId = TestUtils.mock(AuthUtils, "tryGetAuthId") { Result.createSuccess(s1.id) }
        MockedMethod findEveryAllowedRecordIdForStaffId = TestUtils.mock(PhoneRecords, "findEveryAllowedRecordIdForStaffId") {
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
        findEveryAllowedRecordIdForStaffId = TestUtils.mock(PhoneRecords, "findEveryAllowedRecordIdForStaffId") {
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

    void "test criteria for incoming messages (not notes) after a certain time"() {
        given:
        DateTime dt = DateTime.now().minusDays(1)
        Record rec1 = TestUtils.buildRecord()
        RecordCall rCall1 = new RecordCall(record: rec1, outgoing: false)
        RecordCall rCall2 = new RecordCall(record: rec1, outgoing: true)
        RecordText rText1 = new RecordText(record: rec1, outgoing: false, whenCreated: dt.minusDays(1))
        RecordText rText2 = new RecordText(record: rec1, outgoing: true, whenCreated: dt.minusDays(1))
        RecordNote rNote1 = new RecordNote(record: rec1, outgoing: false)
        RecordNote rNote2 = new RecordNote(record: rec1, outgoing: true)
        [rCall1, rCall2, rText1, rText2, rNote1, rNote2]*.save(flush: true, failOnError: true)

        when:
        DetachedCriteria criteria = RecordItems.buildForIncomingMessagesAfter(null)

        then:
        criteria.count() == 0

        when:
        criteria = RecordItems.buildForIncomingMessagesAfter(dt)
        Collection rItems = criteria.list()

        then:
        rCall1 in rItems
        !(rCall2 in rItems)
        !(rText1 in rItems)
        !(rText2 in rItems)
        !(rNote1 in rItems)
        !(rNote2 in rItems)
    }

    void "test criteria for phone id and options"() {
        given:
        DateTime dt = DateTime.now()
        Phone tp1 = TestUtils.buildActiveTeamPhone()
        GroupPhoneRecord gpr1 = TestUtils.buildGroupPhoneRecord(tp1)
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
}
