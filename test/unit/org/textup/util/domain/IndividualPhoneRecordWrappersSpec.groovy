package org.textup.util.domain

import grails.gorm.DetachedCriteria
import grails.test.mixin.*
import grails.test.mixin.gorm.*
import grails.test.mixin.hibernate.*
import org.joda.time.*
import org.textup.*
import org.textup.override.*
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
class IndividualPhoneRecordWrappersSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test creation"() {
        given:
        Phone p1 = TestUtils.buildStaffPhone()
        int rBaseline = Record.count()
        int iprBaseline = IndividualPhoneRecord.count()

        when:
        Result res = IndividualPhoneRecordWrappers.tryCreate(null)
        IndividualPhoneRecord.withSession { it.flush() }

        then:
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        Record.count() == rBaseline
        IndividualPhoneRecord.count() == iprBaseline

        when:
        res = IndividualPhoneRecordWrappers.tryCreate(p1)

        then:
        res.status == ResultStatus.CREATED
        res.payload instanceof IndividualPhoneRecordWrapper
        res.payload.wrappedClass == IndividualPhoneRecord
        res.payload.isOverridden() == false
        res.payload.tryUnwrap().payload.phone == p1
        Record.count() == rBaseline  + 1
        IndividualPhoneRecord.count() == iprBaseline + 1
    }

    void "test finding for id"() {
        given:
        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord()
        GroupPhoneRecord gpr1 = TestUtils.buildGroupPhoneRecord()
        PhoneRecord spr1 = TestUtils.buildSharedPhoneRecord(ipr1)

        when:
        Result res = IndividualPhoneRecordWrappers.mustFindForId(null)

        then:
        res.status == ResultStatus.NOT_FOUND

        when:
        res = IndividualPhoneRecordWrappers.mustFindForId(gpr1.id)

        then:
        res.status == ResultStatus.NOT_FOUND

        when:
        res = IndividualPhoneRecordWrappers.mustFindForId(spr1.id)

        then:
        res.status == ResultStatus.OK
        res.payload.id == spr1.id

        when:
        res = IndividualPhoneRecordWrappers.mustFindForId(ipr1.id)

        then:
        res.status == ResultStatus.OK
        res.payload.id == ipr1.id
    }

    void "test closure for query"() {
        given:
        Phone p1 = TestUtils.buildActiveStaffPhone()
        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord(p1)
        PhoneRecord spr1 = TestUtils.buildSharedPhoneRecord(null, p1)

        DetachedCriteria criteria = new DetachedCriteria(PhoneRecord).build { eq("phone", p1) }

        when:
        Collection prs = criteria.build(IndividualPhoneRecordWrappers.forQuery(null)).list()

        then:
        prs.size() > 0
        ipr1 in prs
        spr1 in prs

        when:
        prs = criteria.build(IndividualPhoneRecordWrappers.forQuery(spr1.shareSource.name)).list()

        then:
        prs == [spr1]

        when:
        prs = criteria.build(IndividualPhoneRecordWrappers.forQuery(spr1.shareSource.sortedNumbers[0].number)).list()

        then:
        prs == [spr1]

        when:
        prs = criteria.build(IndividualPhoneRecordWrappers.forQuery(ipr1.name)).list()

        then:
        prs == [ipr1]

        when:
        prs = criteria.build(IndividualPhoneRecordWrappers.forQuery(ipr1.sortedNumbers[0].number)).list()

        then:
        prs == [ipr1]

        when:
        prs = criteria.build(IndividualPhoneRecordWrappers.forQuery(TestConstants.TEST_DEFAULT_AREA_CODE)).list()

        then:
        prs.size() > 0
        ipr1 in prs
        spr1 in prs
    }

    void "test criteria base"() {
        given:
        Phone p1 = TestUtils.buildActiveStaffPhone()
        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord(p1)
        ipr1.status = PhoneRecordStatus.ARCHIVED
        IndividualPhoneRecord ipr2 = TestUtils.buildIndPhoneRecord(p1)
        ipr2.status = PhoneRecordStatus.BLOCKED
        PhoneRecord spr1 = TestUtils.buildSharedPhoneRecord(null, p1)
        spr1.status = PhoneRecordStatus.UNREAD
        GroupPhoneRecord gpr1 = TestUtils.buildGroupPhoneRecord(p1)

        when:
        DetachedCriteria criteria = IndividualPhoneRecordWrappers.buildActiveBase(null, null)

        then:
        criteria.count() == 0

        when:
        criteria = IndividualPhoneRecordWrappers
            .buildActiveBase(null, PhoneRecordStatus.VISIBLE_STATUSES)
            .build { eq("phone", p1) }
        Collection prs = criteria.list()

        then: "groups always excluded"
        prs.size() == 2
        [ipr1, spr1].every { it in prs }

        when:
        criteria = IndividualPhoneRecordWrappers
            .buildActiveBase(null, [PhoneRecordStatus.UNREAD, PhoneRecordStatus.BLOCKED])
            .build { eq("phone", p1) }
        prs = criteria.list()

        then:
        prs.size() == 2
        [spr1, ipr2].every { it in prs }

        when:
        criteria = IndividualPhoneRecordWrappers
            .buildActiveBase(null, [PhoneRecordStatus.ARCHIVED])
            .build { eq("phone", p1) }
        prs = criteria.list()

        then:
        prs == [ipr1]

        when:
        criteria = IndividualPhoneRecordWrappers
            .buildActiveBase(spr1.shareSource.name, [PhoneRecordStatus.ARCHIVED])
            .build { eq("phone", p1) }
        prs = criteria.list()

        then:
        prs.isEmpty()

        when:
        criteria = IndividualPhoneRecordWrappers
            .buildActiveBase(spr1.shareSource.name, [PhoneRecordStatus.UNREAD])
            .build { eq("phone", p1) }
        prs = criteria.list()

        then:
        prs == [spr1]
    }

    void "test building closure for list action"() {
        given:
        Phone p1 = TestUtils.buildActiveStaffPhone()
        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord(p1)
        ipr1.status = PhoneRecordStatus.ACTIVE
        ipr1.record.lastRecordActivity = DateTime.now().minusDays(1)
        IndividualPhoneRecord ipr2 = TestUtils.buildIndPhoneRecord(p1)
        ipr2.status = PhoneRecordStatus.ACTIVE
        ipr2.record.lastRecordActivity = DateTime.now().minusDays(3)
        IndividualPhoneRecord ipr3 = TestUtils.buildIndPhoneRecord(p1)
        ipr3.status = PhoneRecordStatus.UNREAD
        ipr3.record.lastRecordActivity = DateTime.now().minusDays(10)
        GroupPhoneRecord gpr1 = TestUtils.buildGroupPhoneRecord(p1)

        DetachedJoinableCriteria criteria = new DetachedJoinableCriteria(PhoneRecord).build { eq("phone", p1) }

        when:
        Closure action = IndividualPhoneRecordWrappers.listAction(null)

        then:
        action.call() == []

        when:
        action = IndividualPhoneRecordWrappers.listAction(criteria)
        def retVal = action.call()

        then:
        retVal instanceof List
        retVal.size() == 3
        retVal.every { it instanceof IndividualPhoneRecordWrapper }
        retVal == [ipr3, ipr1, ipr2]*.toWrapper()
    }

    void "test criteria for phone id and options"() {
        given:
        Phone p1 = TestUtils.buildActiveStaffPhone()
        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord(p1)
        PhoneRecord spr1 = TestUtils.buildSharedPhoneRecord(null, p1)

        when:
        DetachedCriteria criteria = IndividualPhoneRecordWrappers.buildForPhoneIdWithOptions(null)

        then:
        criteria.count() == 0

        when:
        criteria = IndividualPhoneRecordWrappers.buildForPhoneIdWithOptions(p1.id)
        Collection prs = criteria.list()

        then:
        prs.size() == 2
        ipr1 in prs
        spr1 in prs

        when: "still valid if we include the query custom associations"
        criteria = IndividualPhoneRecordWrappers.buildForPhoneIdWithOptions(p1.id,
            spr1.shareSource.sortedNumbers[0].number, PhoneRecordStatus.VISIBLE_STATUSES, true)
        prs = criteria.list()

        then:
        prs == [spr1]
    }

    void "test criteria for shared by phone id and options"() {
        given:
        Phone p1 = TestUtils.buildActiveStaffPhone()
        PhoneRecord spr1 = TestUtils.buildSharedPhoneRecord(null, p1)

        when:
        DetachedCriteria criteria = IndividualPhoneRecordWrappers.buildForSharedByIdWithOptions(null)

        then:
        criteria.count() == 0

        when:
        criteria = IndividualPhoneRecordWrappers.buildForSharedByIdWithOptions(spr1.shareSource.phone.id)

        then:
        criteria.list() == [spr1]

        when: "still valid if we include the query custom associations"
        criteria = IndividualPhoneRecordWrappers.buildForSharedByIdWithOptions(spr1.shareSource.phone.id,
            spr1.shareSource.name)

        then:
        criteria.list() == [spr1]
    }

    void "test finding or creating for phone and numbers"() {
        given:
        PhoneNumber pNum1 = TestUtils.randPhoneNumber()
        Phone tp1 = TestUtils.buildActiveTeamPhone()
        Phone p1 = TestUtils.buildActiveStaffPhone()
        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord(tp1)
        PhoneRecord spr1 = TestUtils.buildSharedPhoneRecord(ipr1, p1)
        int iprBaseline = IndividualPhoneRecord.count()

        when:
        Result res = IndividualPhoneRecordWrappers.tryFindOrCreateEveryByPhoneAndNumbers(tp1,
            [ipr1.sortedNumbers[0], pNum1])

        then:
        res.status == ResultStatus.OK
        res.payload.every { it instanceof IndividualPhoneRecordWrapper }
        res.payload.size() == 3
        ipr1.toWrapper() in res.payload
        spr1.toWrapper() in res.payload
        IndividualPhoneRecord.count() == iprBaseline + 1

        when:
        Result res2 = IndividualPhoneRecordWrappers.tryFindOrCreateEveryByPhoneAndNumbers(tp1,
            [ipr1.sortedNumbers[0], pNum1])

        then:
        res2.status == ResultStatus.OK
        res2.payload == res.payload
        IndividualPhoneRecord.count() == iprBaseline + 1
    }
}
