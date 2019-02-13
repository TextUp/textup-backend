package org.textup.validator

import grails.test.mixin.*
import grails.test.mixin.gorm.*
import grails.test.mixin.hibernate.*
import grails.test.mixin.support.*
import grails.test.runtime.*
import grails.validation.*
import org.joda.time.*
import org.textup.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import spock.lang.*

@Domain([AnnouncementReceipt, ContactNumber, CustomAccountDetails, FeaturedAnnouncement,
    FutureMessage, GroupPhoneRecord, IncomingSession, IndividualPhoneRecord, Location, MediaElement,
    MediaElementVersion, MediaInfo, Organization, OwnerPolicy, Phone, PhoneNumberHistory,
    PhoneOwnership, PhoneRecord, PhoneRecordMembers, Record, RecordCall, RecordItem,
    RecordItemReceipt, RecordNote, RecordNoteRevision, RecordText, Role, Schedule,
    SimpleFutureMessage, Staff, StaffRole, Team, Token])
@TestMixin(HibernateTestMixin)
class RecordItemRequestSpec extends CustomSpec {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test creation + constraints"() {
        given:
        Phone p1 = TestUtils.buildActiveStaffPhone()
        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord(p1)
        IndividualPhoneRecord foreign = TestUtils.buildIndPhoneRecord()
        PhoneRecord spr1 = TestUtils.buildSharedPhoneRecord(null, p1)
        spr1.permission = SharePermission.VIEW
        PhoneRecord spr2 = TestUtils.buildSharedPhoneRecord(null, p1)
        spr2.permission = SharePermission.NONE

        when:
        Result res = RecordItemRequest.tryCreate(null, null, false)

        then:
        res.status == ResultStatus.UNPROCESSABLE_ENTITY

        when:
        res = RecordItemRequest.tryCreate(p1, [ipr1, spr1, spr2]*.toWrapper(), false)

        then:
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages == ["someNoPermissions"]

        when:
        res = RecordItemRequest.tryCreate(p1, [ipr1, spr1, foreign]*.toWrapper(), false)

        then:
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages == ["foreign"]

        when:
        res = RecordItemRequest.tryCreate(p1, [ipr1, spr1]*.toWrapper(), false)

        then:
        res.status == ResultStatus.CREATED
        ipr1.toWrapper() in res.payload.wrappers
        spr1.toWrapper() in res.payload.wrappers
        res.payload.mutablePhone == p1
    }

    void "test formatting start and end times"() {
        given:
        Phone p1 = TestUtils.buildActiveStaffPhone()
        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord(p1)
        DateTime start = DateTime.now()
        DateTime end = DateTime.now()

        when:
        RecordItemRequest iReq1 = RecordItemRequest.tryCreate(p1, [ipr1.toWrapper()], false).payload

        then:
        iReq1.buildFormattedStart(null) == RecordItemRequest.DEFAULT_START
        iReq1.buildFormattedEnd(null) == RecordItemRequest.DEFAULT_END

        when:
        iReq1.start = start
        iReq1.end = end

        then:
        iReq1.buildFormattedStart(null) instanceof String
        iReq1.buildFormattedStart(null) != RecordItemRequest.DEFAULT_START
        iReq1.buildFormattedEnd(null) instanceof String
        iReq1.buildFormattedEnd(null) != RecordItemRequest.DEFAULT_END
    }

    void "test getting criteria"() {
        given:
        Phone p1 = TestUtils.buildActiveStaffPhone()
        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord(p1)
        IndividualPhoneRecord ipr2 = TestUtils.buildIndPhoneRecord(p1)
        RecordItem rItem1 = TestUtils.buildRecordItem(ipr1.record)
        RecordItem rItem2 = TestUtils.buildRecordItem(ipr2.record)

        when: "no wrappers"
        RecordItemRequest iReq1 = RecordItemRequest.tryCreate(p1, null, false).payload
        Collection rItems = iReq1.criteria.list()

        then:
        rItems.size() == 2
        rItem1.id in rItems*.id
        rItem2.id in rItems*.id

        when: "has wrappers"
        iReq1 = RecordItemRequest.tryCreate(p1, [ipr1.toWrapper()], false).payload
        rItems = iReq1.criteria.list()

        then:
        rItems*.id == [rItem1.id]
    }

    void "test building sections"() {
        given:
        Phone p1 = TestUtils.buildStaffPhone()
        PhoneRecordWrapper w1 = TestUtils.buildIndPhoneRecord(p1).toWrapper()
        Map params = [(TestUtils.randString()): TestUtils.randString()]
        RecordItemRequestSection mockSection = GroovyStub() { asBoolean() >> true }
        MockedMethod buildPagination = TestUtils.mock(ControllerUtils, "buildPagination") {
            [:]
        }
        MockedMethod buildSectionsByEntity = TestUtils.mock(RecordUtils, "buildSectionsByEntity") {
            [mockSection]
        }
        MockedMethod buildSingleSection = TestUtils.mock(RecordUtils, "buildSingleSection") {
            mockSection
        }

        when: "no wrappers + not group by entity"
        List sections = RecordItemRequest.tryCreate(p1, null, false).payload.buildSections(params)

        then:
        sections == [mockSection]
        buildPagination.callCount == 1
        buildSectionsByEntity.callCount == 0
        buildSingleSection.callCount == 1

        when: "no wrappers + group by entity"
        sections = RecordItemRequest.tryCreate(p1, null, true).payload.buildSections(params)

        then:
        sections == [mockSection]
        buildPagination.callCount == 2
        buildSectionsByEntity.callCount == 0
        buildSingleSection.callCount == 2

        when: "has wrappers + not group by entity"
        sections = RecordItemRequest.tryCreate(p1, [w1], false).payload.buildSections(params)

        then:
        sections == [mockSection]
        buildPagination.callCount == 3
        buildSectionsByEntity.callCount == 0
        buildSingleSection.callCount == 3

        when: "has wrappers + group by entity"
        sections = RecordItemRequest.tryCreate(p1, [w1], true).payload.buildSections(params)

        then:
        sections == [mockSection]
        buildPagination.callCount == 4
        buildPagination.callArguments.every { it == [params, 0] }
        buildSectionsByEntity.callCount == 1
        buildSingleSection.callCount == 3

        cleanup:
        buildPagination.restore()
        buildSectionsByEntity.restore()
        buildSingleSection.restore()
    }

}
