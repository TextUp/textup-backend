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
class FutureMessagesSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
        IOCUtils.metaClass."static".getQuartzScheduler = { -> TestUtils.mockScheduler() }
    }

    void "test is allowed"() {
        given:
        Staff s1 = TestUtils.buildStaff()
        FutureMessage fMsg1 = TestUtils.buildFutureMessage()

        MockedMethod tryGetAuthId = MockedMethod.create(AuthUtils, "tryGetAuthId") { Result.createSuccess(s1.id) }
        MockedMethod findEveryAllowedRecordIdForStaffId = MockedMethod.create(PhoneRecords, "findEveryAllowedRecordIdForStaffId") {
            []
        }

        when:
        Result res = FutureMessages.isAllowed(null)

        then:
        res.status == ResultStatus.FORBIDDEN

        when:
        res = FutureMessages.isAllowed(fMsg1.id)

        then:
        findEveryAllowedRecordIdForStaffId.callCount > 0
        res.status == ResultStatus.FORBIDDEN

        when:
        findEveryAllowedRecordIdForStaffId.restore()
        findEveryAllowedRecordIdForStaffId = MockedMethod.create(PhoneRecords, "findEveryAllowedRecordIdForStaffId") {
            [fMsg1.record.id]
        }
        res = FutureMessages.isAllowed(fMsg1.id)

        then:
        findEveryAllowedRecordIdForStaffId.callCount == 1
        res.status == ResultStatus.OK
        res.payload == fMsg1.id

        cleanup:
        tryGetAuthId.restore()
        findEveryAllowedRecordIdForStaffId.restore()
    }

    void "test finding for id"() {
        given:
        FutureMessage fMsg1 = TestUtils.buildFutureMessage()

        when:
        Result res = FutureMessages.mustFindForId(null)

        then:
        res.status == ResultStatus.NOT_FOUND

        when:
        res = FutureMessages.mustFindForId(fMsg1.id)

        then:
        res.status == ResultStatus.OK
        res.payload == fMsg1
    }

    void "test finding for key"() {
        given:
        FutureMessage fMsg1 = TestUtils.buildFutureMessage()

        when:
        Result res = FutureMessages.mustFindForKey(null)

        then:
        res.status == ResultStatus.NOT_FOUND

        when:
        res = FutureMessages.mustFindForKey(fMsg1.keyName)

        then:
        res.status == ResultStatus.OK
        res.payload == fMsg1
    }

    void "test criteria for record ids"() {
        given:
        FutureMessage fMsg1 = TestUtils.buildFutureMessage()

        when:
        DetachedCriteria criteria = FutureMessages.buildForRecordIds(null)

        then:
        criteria.count() == 0

        when:
        criteria = FutureMessages.buildForRecordIds([fMsg1.record.id])

        then:
        criteria.list() == [fMsg1]
    }
}
