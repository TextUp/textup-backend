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
class IndividualPhoneRecordsSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test finding active for id"() {
        given:
        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord()
        ipr1.isDeleted = true
        IndividualPhoneRecord ipr2 = TestUtils.buildIndPhoneRecord()

        when:
        Result res = IndividualPhoneRecords.mustFindActiveForId(null)

        then:
        res.status == ResultStatus.NOT_FOUND

        when:
        res = IndividualPhoneRecords.mustFindActiveForId(ipr1.id)

        then:
        res.status == ResultStatus.NOT_FOUND

        when:
        res = IndividualPhoneRecords.mustFindActiveForId(ipr2.id)

        then:
        res.status == ResultStatus.OK
        res.payload == ipr2
    }

    void "test criteria for ids"() {
        given:
        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord()
        IndividualPhoneRecord ipr2 = TestUtils.buildIndPhoneRecord()

        when:
        DetachedCriteria criteria = IndividualPhoneRecords.buildForIds(null)

        then:
        criteria.count() == 0

        when:
        criteria = IndividualPhoneRecords.buildForIds([ipr1, ipr2]*.id)

        then:
        criteria.list() == [ipr1, ipr2]
    }

    void "test finding every for ids and phone id"() {
        given:
        Phone p1 = TestUtils.buildStaffPhone()
        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord(p1)
        IndividualPhoneRecord ipr2 = TestUtils.buildIndPhoneRecord(p1)
        IndividualPhoneRecord ipr3 = TestUtils.buildIndPhoneRecord()

        when:
        Collection iprs = IndividualPhoneRecords.findEveryByIdsAndPhoneId(null, null)

        then:
        iprs.isEmpty() == true

        when:
        iprs = IndividualPhoneRecords.findEveryByIdsAndPhoneId([ipr1, ipr2, ipr3]*.id, p1.id)

        then:
        ipr1 in iprs
        ipr2 in iprs
        !(ipr3 in iprs)
    }

    void "test finding num to id map given phone id and options"() {
        given:
        Phone tp1 = TestUtils.buildActiveTeamPhone()

        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord(tp1)
        IndividualPhoneRecord ipr2 = TestUtils.buildIndPhoneRecord(tp1)
        IndividualPhoneRecord ipr3 = TestUtils.buildIndPhoneRecord(tp1)
        IndividualPhoneRecord ipr4 = TestUtils.buildIndPhoneRecord(tp1)
        ipr4.isDeleted = true

        PhoneNumber pNum1 = TestUtils.randPhoneNumber()
        ipr1.mergeNumber(pNum1, 1)
        ipr2.mergeNumber(pNum1, 1)

        IndividualPhoneRecord.withSession { it.flush() }

        when:
        Map numToIds = IndividualPhoneRecords.findNumToIdByPhoneIdAndOptions(null)

        then:
        numToIds == [:]

        when:
        numToIds = IndividualPhoneRecords.findNumToIdByPhoneIdAndOptions(tp1.id)

        then:
        numToIds.size() == 4

        numToIds[pNum1].size() == 2
        ipr1.id in numToIds[pNum1]
        ipr2.id in numToIds[pNum1]
        !(ipr3.id in numToIds[pNum1])

        numToIds[PhoneNumber.copy(ipr1.sortedNumbers[0])] == new HashSet([ipr1.id])
        numToIds[PhoneNumber.copy(ipr2.sortedNumbers[0])] == new HashSet([ipr2.id])
        numToIds[PhoneNumber.copy(ipr3.sortedNumbers[0])] == new HashSet([ipr3.id])
        numToIds[PhoneNumber.copy(ipr4.sortedNumbers[0])].isEmpty()
    }

    void "test removing blocked phone records"() {
        given:
        PhoneNumber pNum1 = TestUtils.randPhoneNumber()
        PhoneNumber pNum2 = TestUtils.randPhoneNumber()
        PhoneNumber pNum3 = TestUtils.randPhoneNumber()

        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord()
        ipr1.status = PhoneRecordStatus.ACTIVE
        IndividualPhoneRecord ipr2 = TestUtils.buildIndPhoneRecord()
        ipr2.status = PhoneRecordStatus.BLOCKED
        IndividualPhoneRecord.withSession { it.flush() }

        Map numberToPhoneRecords = [(pNum1): [ipr1, ipr2], (pNum2): [ipr2], (pNum3): []]

        when:
        Map filtered = IndividualPhoneRecords.removeBlockedPhoneRecords(numberToPhoneRecords)

        then:
        filtered[pNum1] == [ipr1]
        filtered[pNum2] == []
        filtered[pNum3] == []

        expect:
        IndividualPhoneRecords.removeBlockedPhoneRecords(null) == [:]
    }

    void "test determining if we should create a new phone record"() {
        given:
        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord()
        ipr1.status = PhoneRecordStatus.ACTIVE
        IndividualPhoneRecord ipr2 = TestUtils.buildIndPhoneRecord()
        ipr2.status = PhoneRecordStatus.BLOCKED
        IndividualPhoneRecord.withSession { it.flush() }

        expect:
        IndividualPhoneRecords.shouldCreateNewPhoneRecord(null, false) == false
        IndividualPhoneRecords.shouldCreateNewPhoneRecord([ipr1], true) == false
        IndividualPhoneRecords.shouldCreateNewPhoneRecord([ipr1, ipr2], true) == false

        and: "should create if none in list"
        IndividualPhoneRecords.shouldCreateNewPhoneRecord([], false) == true
        IndividualPhoneRecords.shouldCreateNewPhoneRecord([], true) == true

        and: "should create if everything in list is blocked and flag is set to true"
        IndividualPhoneRecords.shouldCreateNewPhoneRecord([ipr2], true) == true
    }

    void "test trying to create new phone records if missing in collection"() {
        given:
        PhoneNumber pNum1 = TestUtils.randPhoneNumber()
        PhoneNumber pNum2 = TestUtils.randPhoneNumber()

        Phone p1 = TestUtils.buildStaffPhone()
        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord()
        ipr1.status = PhoneRecordStatus.BLOCKED
        IndividualPhoneRecord.withSession { it.flush() }

        Map numberToPhoneRecords1 = [(pNum1): [ipr1], (pNum2): []]
        Map numberToPhoneRecords2 = [(pNum1): [ipr1], (pNum2): []]
        int iprBaseline = IndividualPhoneRecord.count()

        when: "create if absent but if has some non-visible, then do not create"
        Result res = IndividualPhoneRecords.tryCreateIfNone(p1, numberToPhoneRecords1, false)

        then:
        res.status == ResultStatus.OK
        res.payload.is(numberToPhoneRecords1) == false
        res.payload[pNum1].size() == 0 // blocked is removed
        res.payload[pNum2].size() == 1
        res.payload[pNum2][0].phone == p1
        res.payload[pNum2][0].numbers.size() == 1
        res.payload[pNum2][0].numbers.find { it.number == pNum2.number }
        IndividualPhoneRecord.count() == iprBaseline + 1

        when: "create even if there already exists some not visible"
        res = IndividualPhoneRecords.tryCreateIfNone(p1, numberToPhoneRecords2, true)

        then:
        res.status == ResultStatus.OK
        res.payload.is(numberToPhoneRecords2) == false
        res.payload[pNum1].size() == 1 // blocked is removed
        res.payload[pNum1][0] != ipr1
        res.payload[pNum1][0].phone == p1
        res.payload[pNum1][0].numbers.size() == 1
        res.payload[pNum1][0].numbers.find { it.number == pNum1.number }
        res.payload[pNum2].size() == 1
        res.payload[pNum2][0].phone == p1
        res.payload[pNum2][0].numbers.size() == 1
        res.payload[pNum2][0].numbers.find { it.number == pNum2.number }
        IndividualPhoneRecord.count() == iprBaseline + 3
    }

    void "test finding or creating map for phone and numbers"() {
        given:
        PhoneNumber pNum1 = TestUtils.randPhoneNumber()

        Phone p1 = TestUtils.buildStaffPhone()
        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord(p1)
        ipr1.status = PhoneRecordStatus.ACTIVE
        IndividualPhoneRecord ipr2 = TestUtils.buildIndPhoneRecord(p1)
        ipr2.status = PhoneRecordStatus.BLOCKED
        IndividualPhoneRecord.withSession { it.flush() }

        MockedMethod tryCreateIfNone = MockedMethod.create(IndividualPhoneRecords, "tryCreateIfNone")

        when:
        Result res = IndividualPhoneRecords.tryFindOrCreateNumToObjByPhoneAndNumbers(null, null, false, false)

        then:
        tryCreateIfNone.notCalled
        res.status == ResultStatus.OK
        res.payload == [:]

        when:
        res = IndividualPhoneRecords.tryFindOrCreateNumToObjByPhoneAndNumbers(p1,
            [ipr1.sortedNumbers[0], pNum1], false, true)

        then:
        tryCreateIfNone.notCalled
        res.status == ResultStatus.OK
        res.payload[PhoneNumber.copy(ipr1.sortedNumbers[0])] == [ipr1]
        res.payload[pNum1] == []

        when:
        res = IndividualPhoneRecords.tryFindOrCreateNumToObjByPhoneAndNumbers(p1,
            [ipr1.sortedNumbers[0], ipr2.sortedNumbers[0], pNum1], true, true)

        then:
        res == null // mock returns null
        tryCreateIfNone.callCount == 1
        tryCreateIfNone.latestArgs[0] == p1
        tryCreateIfNone.latestArgs[1][PhoneNumber.copy(ipr1.sortedNumbers[0])] == [ipr1]
        tryCreateIfNone.latestArgs[1][PhoneNumber.copy(ipr2.sortedNumbers[0])] == [ipr2] // even blocked passed-on
        tryCreateIfNone.latestArgs[1][pNum1] == []
        tryCreateIfNone.latestArgs[2] == true

        cleanup:
        tryCreateIfNone?.restore()
    }
}
