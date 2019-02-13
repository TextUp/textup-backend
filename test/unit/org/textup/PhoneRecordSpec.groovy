package org.textup

import grails.gorm.DetachedCriteria
import grails.test.mixin.*
import grails.test.mixin.gorm.*
import grails.test.mixin.hibernate.*
import grails.test.mixin.support.*
import grails.test.runtime.*
import grails.validation.*
import org.joda.time.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*
import spock.lang.*

@Domain([AnnouncementReceipt, ContactNumber, CustomAccountDetails, FeaturedAnnouncement,
    FutureMessage, GroupPhoneRecord, IncomingSession, IndividualPhoneRecord, Location, MediaElement,
    MediaElementVersion, MediaInfo, Organization, OwnerPolicy, Phone, PhoneNumberHistory,
    PhoneOwnership, PhoneRecord, PhoneRecordMembers, Record, RecordCall, RecordItem,
    RecordItemReceipt, RecordNote, RecordNoteRevision, RecordText, Role, Schedule,
    SimpleFutureMessage, Staff, StaffRole, Team, Token])
@TestMixin(HibernateTestMixin)
class PhoneRecordSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test basic constraints"() {
        given:
        Record rec1 = TestUtils.buildRecord()
        Phone p1 = TestUtils.buildStaffPhone()

        when:
        PhoneRecord pr1 = new PhoneRecord()

        then:
        pr1.validate() == false

        when:
        pr1 = new PhoneRecord(phone: p1, record: rec1)

        then:
        pr1.validate()
    }

    void "test creating for share + constraints"() {
        given:
        Record rec1 = TestUtils.buildRecord()
        Record rec2 = TestUtils.buildRecord()
        Phone p1 = TestUtils.buildStaffPhone()
        Phone p2 = TestUtils.buildStaffPhone()
        SharePermission perm1 = SharePermission.values()[0]
        PhoneRecord pr1 = new PhoneRecord(phone: p1, record: rec1)
        assert pr1.validate()

        when:
        Result res = PhoneRecord.tryCreate(null, null, null)

        then:
        res.status == ResultStatus.UNPROCESSABLE_ENTITY

        when:
        res = PhoneRecord.tryCreate(perm1, pr1, p1)

        then:
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages.contains("shareWithMyself")

        when:
        res = PhoneRecord.tryCreate(null, pr1, p2)

        then:
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages.contains("mustSpecifySharingPermission")

        when:
        res = PhoneRecord.tryCreate(perm1, pr1, p2)

        then:
        res.status == ResultStatus.CREATED
        res.payload.shareSource == pr1
        res.payload.record == pr1.record
        res.payload.phone == p2
        res.payload.permission == perm1

        when:
        res.payload.record = rec2

        then: "mismatchedRecord error"
        res.payload.validate() == false
    }

    void "test cancelling future messages related to this record"() {
        given:
        List fMsgs = [GroovyMock(FutureMessage)]
        MockedMethod buildForRecordIds = TestUtils.mock(FutureMessages, "buildForRecordIds") {
            GroovyStub(DetachedCriteria) { list() >> fMsgs }
        }

        PhoneRecord pr1 = new PhoneRecord(phone: TestUtils.buildStaffPhone(),
            record: TestUtils.buildRecord())
        assert pr1.validate()
        pr1.futureMessageJobService = GroovyMock(FutureMessageJobService)

        when:
        Result res = pr1.tryCancelFutureMessages()

        then:
        1 * pr1.futureMessageJobService.cancelAll(fMsgs) >> new ResultGroup()
        buildForRecordIds.callCount == 1
        buildForRecordIds.callArguments[0] == [[pr1.record.id]]
        res.payload == []

        cleanup:
        buildForRecordIds.restore()
    }

    void "test converting to wrapper"() {
        given:
        Record rec1 = TestUtils.buildRecord()
        Phone p1 = TestUtils.buildStaffPhone()
        Phone p2 = TestUtils.buildStaffPhone()
        SharePermission perm1 = SharePermission.values()[0]
        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord()

        when: "wrapper for not shared"
        PhoneRecord pr1 = new PhoneRecord(phone: p1, record: rec1)
        def w1 = pr1.toWrapper()

        then:
        w1 instanceof PhoneRecordWrapper
        w1.wrappedClass == PhoneRecord
        w1.id == pr1.id
        w1.isOverridden() == false

        when: "wrapped for shared"
        PhoneRecord pr2 = PhoneRecord.tryCreate(perm1, ipr1, p2).payload
        w1 = pr2.toWrapper()

        then: "actually use share source's wrapper"
        w1 instanceof IndividualPhoneRecordWrapper
        w1.wrappedClass == IndividualPhoneRecord
        w1.id != ipr1.id
        w1.id == pr2.id
        w1.isOverridden() == true
    }
}
