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
@Unroll
class ContactNumberSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test creation + constraints"() {
        given:
        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord()
        PhoneNumber invalidNum = PhoneNumber.create("invalid")
        PhoneNumber pNum1 = TestUtils.randPhoneNumber()
        Integer pref = TestUtils.randIntegerUpTo(88)

        when:
        Result res = ContactNumber.tryCreate(null, null, null)

        then:
        res.status == ResultStatus.UNPROCESSABLE_ENTITY

        when:
        res = ContactNumber.tryCreate(ipr1, invalidNum, pref)

        then:
        res.status == ResultStatus.UNPROCESSABLE_ENTITY

        when:
        res = ContactNumber.tryCreate(ipr1, pNum1, pref)

        then:
        res.status == ResultStatus.CREATED
        res.payload.owner == ipr1
        res.payload.preference == pref
        res.payload.number == pNum1.number
    }

    void "test sorting"() {
        given:
        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord()
        PhoneNumber pNum1 = TestUtils.randPhoneNumber()
        PhoneNumber pNum2 = TestUtils.randPhoneNumber()
        Integer pref1 = -8
        Integer pref2 = 0

        when:
        ContactNumber cNum1 = ContactNumber.tryCreate(ipr1, pNum1, pref1).payload
        ContactNumber cNum2 = ContactNumber.tryCreate(ipr1, pNum2, pref2).payload

        then:
        [cNum2, cNum1].sort() == [cNum1, cNum2]
    }

    void "test equals and hash code"() {
        given:
        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord()
        PhoneNumber pNum1 = TestUtils.randPhoneNumber()
        PhoneNumber pNum2 = TestUtils.randPhoneNumber()
        Integer pref1 = -8
        Integer pref2 = 0

        ContactNumber cNum1 = ContactNumber.tryCreate(ipr1, pNum1, pref1).payload
        ContactNumber cNum2 = ContactNumber.tryCreate(ipr1, pNum1, pref1).payload
        ContactNumber cNum3 = ContactNumber.tryCreate(ipr1, pNum2, pref2).payload

        expect:
        cNum1 == cNum1
        cNum1.hashCode() == cNum1.hashCode()

        cNum1 == cNum2
        cNum1.hashCode() == cNum2.hashCode()

        cNum1 != cNum3
        cNum1.hashCode() != cNum3.hashCode()

        cNum2 != cNum3
        cNum2.hashCode() != cNum3.hashCode()
    }
}
