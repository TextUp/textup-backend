package org.textup

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
import org.textup.validator.*
import spock.lang.*

@Domain([AnnouncementReceipt, ContactNumber, CustomAccountDetails, FeaturedAnnouncement,
    FutureMessage, GroupPhoneRecord, IncomingSession, IndividualPhoneRecord, Location, MediaElement,
    MediaElementVersion, MediaInfo, Organization, OwnerPolicy, Phone, PhoneNumberHistory,
    PhoneOwnership, PhoneRecord, PhoneRecordMembers, Record, RecordCall, RecordItem,
    RecordItemReceipt, RecordNote, RecordNoteRevision, RecordText, Role, Schedule,
    SimpleFutureMessage, Staff, StaffRole, Team, Token])
@TestMixin(HibernateTestMixin)
class PhoneRecordMembersSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test creation + constraints"() {
        when:
        Result res = PhoneRecordMembers.tryCreate()

        then:
        res.status == ResultStatus.CREATED
        res.payload instanceof PhoneRecordMembers
        res.payload.allActive == []
        res.payload.getByStatus(null) == []
        res.payload.getByStatus(PhoneRecordStatus.ACTIVE_STATUSES) == []
    }

    void "test constraints on phone record members"() {
        given:
        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord()
        PhoneRecord spr1 = TestUtils.buildSharedPhoneRecord(ipr1)
        GroupPhoneRecord gpr1 = TestUtils.buildGroupPhoneRecord()

        PhoneRecordMembers prm1 = PhoneRecordMembers.tryCreate().payload

        when:
        prm1.addToPhoneRecords(ipr1)

        then:
        prm1.validate()
        prm1.allActive*.id == [ipr1]*.id
        prm1.getByStatus(null)*.id == [ipr1]*.id
        prm1.getByStatus(PhoneRecordStatus.ACTIVE_STATUSES)*.id == [ipr1]*.id

        when:
        prm1.addToPhoneRecords(spr1)

        then:
        prm1.validate()
        prm1.allActive.size() == 2
        spr1 in prm1.allActive
        spr1 in prm1.getByStatus(PhoneRecordStatus.ACTIVE_STATUSES)

        when:
        prm1.addToPhoneRecords(gpr1)

        then:
        prm1.validate() == false
        prm1.errors.getFieldErrorCount("phoneRecords") == 1
    }

    void "test getting all not expired vs getting all active"() {
        given:
        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord()
        PhoneRecord spr1 = TestUtils.buildSharedPhoneRecord(ipr1)
        spr1.dateExpired = DateTime.now().minusDays(1)
        PhoneRecord spr2 = TestUtils.buildSharedPhoneRecord()
        GroupPhoneRecord gpr1 = TestUtils.buildGroupPhoneRecord()
        gpr1.status = PhoneRecordStatus.BLOCKED

        PhoneRecordMembers prm1 = PhoneRecordMembers.tryCreate().payload

        when:
        prm1.addToPhoneRecords(ipr1)
        prm1.addToPhoneRecords(spr1)
        prm1.addToPhoneRecords(spr2)
        prm1.addToPhoneRecords(gpr1)

        then:
        prm1.phoneRecords.size() == 4
        prm1.allActive.size() == 2
        [ipr1, spr2].every { it in prm1.allActive }
        prm1.allNotExpired.size() == 3
        [ipr1, spr2, gpr1].every { it in prm1.allNotExpired }
    }

    void "test getting by status can get non-expired `PhoneRecord`s of ANY status"() {
        given:
        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord()
        ipr1.status = PhoneRecordStatus.UNREAD
        IndividualPhoneRecord ipr2 = TestUtils.buildIndPhoneRecord()
        ipr2.status = PhoneRecordStatus.ARCHIVED
        PhoneRecord spr1 = TestUtils.buildSharedPhoneRecord(ipr1)
        spr1.dateExpired = DateTime.now().minusDays(1)
        spr1.status = PhoneRecordStatus.UNREAD
        PhoneRecord spr2 = TestUtils.buildSharedPhoneRecord()
        spr2.status = PhoneRecordStatus.ACTIVE
        GroupPhoneRecord gpr1 = TestUtils.buildGroupPhoneRecord()
        gpr1.status = PhoneRecordStatus.BLOCKED

        PhoneRecordMembers prm1 = PhoneRecordMembers.tryCreate().payload

        when:
        prm1.addToPhoneRecords(ipr1)
        prm1.addToPhoneRecords(ipr2)
        prm1.addToPhoneRecords(spr1)
        prm1.addToPhoneRecords(spr2)
        prm1.addToPhoneRecords(gpr1)

        then:
        prm1.phoneRecords.size() == 5
        prm1.getByStatus([PhoneRecordStatus.UNREAD]).size() == 1 // spr1 is expired so not included
        prm1.getByStatus([PhoneRecordStatus.UNREAD])[0] == ipr1
        prm1.getByStatus([PhoneRecordStatus.ACTIVE]).size() == 1
        prm1.getByStatus([PhoneRecordStatus.ACTIVE])[0] == spr2
        prm1.getByStatus([PhoneRecordStatus.ARCHIVED]).size() == 1
        prm1.getByStatus([PhoneRecordStatus.ARCHIVED])[0] == ipr2
        prm1.getByStatus([PhoneRecordStatus.BLOCKED]).size() == 1
        prm1.getByStatus([PhoneRecordStatus.BLOCKED])[0] == gpr1
    }
}
