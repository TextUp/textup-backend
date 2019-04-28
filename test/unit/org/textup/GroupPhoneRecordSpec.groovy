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
class GroupPhoneRecordSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test creation + constraints"() {
        given:
        Phone p1 = TestUtils.buildStaffPhone()
        p1.language = VoiceLanguage.CHINESE
        String name = TestUtils.randString()
        int rBaseline = Record.count()
        int prmBaseline = PhoneRecordMembers.count()

        when:
        Result res = GroupPhoneRecord.tryCreate(null, null)
        GroupPhoneRecord.withSession { it.flush() }

        then:
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        Record.count() == rBaseline
        PhoneRecordMembers.count() == prmBaseline

        when:
        res = GroupPhoneRecord.tryCreate(p1, name)

        then:
        res.status == ResultStatus.CREATED
        res.payload.validate()
        res.payload.phone == p1
        res.payload.members != null
        res.payload.record != null
        res.payload.name == name
        res.payload.record.language == p1.language
        Record.count() == rBaseline + 1
        PhoneRecordMembers.count() == prmBaseline + 1

        when:
        res.payload.hexColor = TestUtils.randString()

        then:
        res.payload.validate() == false
        res.payload.errors.getFieldErrorCount("hexColor") == 1
    }

    void "test getting names"() {
        given:
        Phone p1 = TestUtils.buildStaffPhone()
        String name = "You are awesome!"
        String initials = "Y.A.A."

        GroupPhoneRecord gpr1 = GroupPhoneRecord.tryCreate(p1, name).payload

        expect:
        gpr1.secureName == name
        gpr1.publicName == initials
    }

    void "test getting records"() {
        given:
        Phone p1 = TestUtils.buildStaffPhone()
        String name = TestUtils.randString()

        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord()

        when:
        GroupPhoneRecord gpr1 = GroupPhoneRecord.tryCreate(p1, name).payload

        then:
        gpr1.members.phoneRecords == null
        gpr1.buildRecords() == [gpr1.record]

        when:
        gpr1.members.addToPhoneRecords(ipr1)

        then:
        gpr1.members.phoneRecords.size() == 1
        gpr1.record in gpr1.buildRecords()
        ipr1.record in gpr1.buildRecords()
    }

    void "test determining if active"() {
        given:
        Phone p1 = TestUtils.buildStaffPhone()
        String name = TestUtils.randString()

        when:
        GroupPhoneRecord gpr1 = GroupPhoneRecord.tryCreate(p1, name).payload

        then:
        gpr1.isActive()
        gpr1.isNotExpired()

        when:
        gpr1.isDeleted = true

        then:
        gpr1.isActive() == false
        gpr1.isNotExpired() == false
    }
}
