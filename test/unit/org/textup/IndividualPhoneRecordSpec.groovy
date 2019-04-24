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
class IndividualPhoneRecordSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test creation + constraints"() {
        given:
        Phone p1 = TestUtils.buildStaffPhone()
        p1.language = VoiceLanguage.JAPANESE
        int rBaseline = Record.count()

        when:
        Result res = IndividualPhoneRecord.tryCreate(null)
        IndividualPhoneRecord.withSession { it.flush() }

        then: "no stray `Record` is created"
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        Record.count() == rBaseline

        when:
        res = IndividualPhoneRecord.tryCreate(p1)

        then:
        res.status == ResultStatus.CREATED
        res.payload.record != null
        res.payload.phone == p1
        res.payload.numbers == null
        res.payload.record.language == p1.language
        Record.count() == rBaseline + 1
    }

    void "getting names"() {
        given:
        Phone p1 = TestUtils.buildStaffPhone()
        String name = "Hello there"
        String initials = "H.T."

        when:
        IndividualPhoneRecord ipr1 = IndividualPhoneRecord.tryCreate(p1).payload

        then:
        ipr1.secureName == ""
        ipr1.publicName == ""

        when:
        ipr1.name = name

        then:
        ipr1.secureName == name
        ipr1.publicName == initials
    }

    void "test determining if active"() {
        given:
        Phone p1 = TestUtils.buildStaffPhone()

        when:
        IndividualPhoneRecord ipr1 = IndividualPhoneRecord.tryCreate(p1).payload

        then:
        ipr1.isActive()
        ipr1.isNotExpired()

        when:
        ipr1.status = PhoneRecordStatus.ACTIVE
        ipr1.isDeleted = true

        then:
        ipr1.isActive() == false
        ipr1.isNotExpired() == false
    }

    void "test merging + deleting errors"() {
        given:
        Phone p1 = TestUtils.buildStaffPhone()
        PhoneNumber invalidNum = PhoneNumber.create("invalid")
        IndividualPhoneRecord ipr1 = IndividualPhoneRecord.tryCreate(p1).payload

        when:
        Result res = ipr1.mergeNumber(null, -8)

        then:
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        ipr1.numbers.isEmpty()

        when:
        res = ipr1.mergeNumber(invalidNum, -8)

        then:
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        ipr1.numbers.isEmpty()

        when:
        res = ipr1.deleteNumber(null)

        then:
        res.status == ResultStatus.NO_CONTENT
        ipr1.numbers.isEmpty()
    }

    void "test merging + deleting + normalizing preferences for numbers"() {
        given:
        Phone p1 = TestUtils.buildStaffPhone()
        PhoneNumber pNum1 = TestUtils.randPhoneNumber()
        PhoneNumber pNum2 = TestUtils.randPhoneNumber()
        PhoneNumber pNum3 = TestUtils.randPhoneNumber()
        IndividualPhoneRecord ipr1 = IndividualPhoneRecord.tryCreate(p1).payload
        Integer pref1 = -8
        Integer pref2 = 3
        Integer pref3 = -12

        when:
        Result res = ipr1.mergeNumber(pNum1, pref1)

        then:
        res.status == ResultStatus.CREATED
        res.payload.number == pNum1.number
        ipr1.numbers.size() == 1
        ipr1.numbers[0].number == pNum1.number
        ipr1.numbers[0].preference == pref1 // not normalized yet

        when:
        res = ipr1.mergeNumber(pNum2, pref2)

        then:
        res.status == ResultStatus.CREATED
        res.payload.number == pNum2.number
        ipr1.numbers.size() == 2
        ipr1.numbers[1].number == pNum2.number
        ipr1.numbers[1].preference == pref2 // not normalized yet

        when:
        res = ipr1.deleteNumber(pNum1)

        then:
        res.status == ResultStatus.NO_CONTENT
        ipr1.numbers.size() == 1
        ipr1.numbers[0].number == pNum2.number
        ipr1.numbers[0].preference == pref2 // not normalized yet

        when:
        res = ipr1.mergeNumber(pNum3, pref3)

        then:
        res.status == ResultStatus.CREATED
        res.payload.number == pNum3.number
        ipr1.numbers.size() == 2
        ipr1.numbers[1].number == pNum3.number
        ipr1.numbers[1].preference == pref3 // not normalized yet

        when: "actually saving"
        IndividualPhoneRecord.withSession { it.flush() }

        then: "preferences are normalized"
        ipr1.numbers.size() == 2
        ipr1.sortedNumbers.size() == 2
        ipr1.sortedNumbers[0].number == pNum3.number
        ipr1.sortedNumbers[0].preference == 0
        ipr1.sortedNumbers[1].number == pNum2.number
        ipr1.sortedNumbers[1].preference == 1
    }

    void "test getting wrapper"() {
        given:
        Phone p1 = TestUtils.buildStaffPhone()
        PhoneRecord pr1 = new PhoneRecord(phone: p1,
            record: TestUtils.buildRecord(),
            permission: SharePermission.VIEW)

        when:
        IndividualPhoneRecord ipr1 = IndividualPhoneRecord.tryCreate(p1).payload
        def w1 = ipr1.toWrapper()

        then:
        w1 instanceof IndividualPhoneRecordWrapper
        w1.permissions.isOwner()

        when:
        w1 = ipr1.toWrapper(pr1)

        then:
        w1 instanceof IndividualPhoneRecordWrapper
        w1.permissions.isOwner() == false
        w1.permissions.canModify() == false
        w1.permissions.canView() == true
    }
}
