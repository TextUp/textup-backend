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
}
