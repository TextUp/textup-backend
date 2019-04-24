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
class RecordItemRequestSectionSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test creation"() {
        given:
        String name = TestUtils.randString()
        PhoneNumber pNum1 = TestUtils.randPhoneNumber()
        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord()
        GroupPhoneRecord gpr1 = TestUtils.buildGroupPhoneRecord()
        PhoneRecord spr1 = TestUtils.buildSharedPhoneRecord()

        when:
        Result res = RecordItemRequestSection.tryCreate(null, null, null, null)

        then:
        res.status == ResultStatus.UNPROCESSABLE_ENTITY

        when:
        res = RecordItemRequestSection.tryCreate(name, pNum1, null, [ipr1, gpr1,  spr1]*.toWrapper())

        then: "no record items is allowed"
        res.status == ResultStatus.CREATED
        res.payload.phoneName == name
        res.payload.phoneNumber == pNum1.prettyPhoneNumber
        res.payload.recordItems.isEmpty()
        res.payload.contactNames.size() == 1
        res.payload.contactNames[0] == ipr1.secureName
        res.payload.sharedContactNames.size() == 1
        res.payload.sharedContactNames[0] == spr1.shareSource.secureName
        res.payload.tagNames.size() == 1
        res.payload.tagNames[0] == gpr1.secureName
    }

    void "test passed-in record items will be sorted before creation"() {
        given:
        String name = TestUtils.randString()
        PhoneNumber pNum1 = TestUtils.randPhoneNumber()
        RecordItem rItem1 = TestUtils.buildRecordItem()
        rItem1.whenCreated = DateTime.now().minusDays(1)
        RecordItem rItem2 = TestUtils.buildRecordItem()
        rItem2.whenCreated = DateTime.now().minusDays(10)
        RecordItem.withSession { it.flush() }

        when:
        Result res = RecordItemRequestSection.tryCreate(name, pNum1, [rItem1, rItem2], null)

        then:
        res.status == ResultStatus.CREATED
        res.payload.recordItems == [rItem2, rItem1]
    }
}
