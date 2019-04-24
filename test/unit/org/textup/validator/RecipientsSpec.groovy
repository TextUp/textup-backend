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
class RecipientsSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test creation given phone, phone record ids, and phone numbers"() {
        given:
        Phone p1 = TestUtils.buildActiveTeamPhone()
        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord(p1)
        GroupPhoneRecord gpr1 = TestUtils.buildGroupPhoneRecord(p1)
        PhoneRecord spr1 = TestUtils.buildSharedPhoneRecord(null, p1)
        PhoneNumber pNum1 = TestUtils.randPhoneNumber()
        int prBaseline = PhoneRecord.count()

        when:
        Result res = Recipients.tryCreate(null, null, null, 0)

        then:
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        PhoneRecord.count() == prBaseline

        when:
        res = Recipients.tryCreate(p1, [ipr1, gpr1, spr1]*.id, [pNum1], 1)

        then:
        res.status == ResultStatus.UNPROCESSABLE_ENTITY

        when:
        res = Recipients.tryCreate(p1, [ipr1, gpr1, spr1]*.id, [pNum1], 10)

        then:
        res.status == ResultStatus.CREATED
        res.payload.phone == p1
        res.payload.all.size() == 4
        ipr1 in res.payload.all
        gpr1 in res.payload.all
        spr1 in res.payload.all
        res.payload.all.find { it instanceof IndividualPhoneRecord && it.sortedNumbers[0].number == pNum1.number }
        PhoneRecord.count() == prBaseline + 1

        when:
        Result res2 = Recipients.tryCreate(p1, [ipr1, gpr1, spr1]*.id, [pNum1], 10)

        then:
        res2.status == ResultStatus.CREATED
        res2.payload.phone == res.payload.phone
        res2.payload.all.every { it in res.payload.all }
        PhoneRecord.count() == prBaseline + 1
    }

    void "test creation given phone records"() {
        given:
        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord()
        GroupPhoneRecord gpr1 = TestUtils.buildGroupPhoneRecord()
        VoiceLanguage lang1 = VoiceLanguage.values()[0]
        int prBaseline = PhoneRecord.count()

        when:
        Result res = Recipients.tryCreate(null, null, 0)

        then:
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        PhoneRecord.count() == prBaseline

        when:
        res = Recipients.tryCreate([ipr1, gpr1], lang1, 10)

        then:
        res.status == ResultStatus.CREATED
        res.payload.phone == ipr1.phone
        res.payload.all.size() == 2
        ipr1 in res.payload.all
        gpr1 in res.payload.all
        res.payload.language == lang1
        PhoneRecord.count() == prBaseline
    }

    // We don't want the user to be confused about why a request is failing when did in fact put in
    // at least one recipient. If the user puts in a number they previous blocked, the likely mean to
    // unblock that phone number, so we allow a new contact with that number to be created.
    void "test creating new `IndividualPhoneRecord`s for phone numbers still happens even if a blocked one exists"() {
        given:
        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord()
        ipr1.status = PhoneRecordStatus.BLOCKED
        IndividualPhoneRecord.withSession { it.flush() }

        int iprBaseline = IndividualPhoneRecord.count()

        when:
        Result res = Recipients.tryCreate(ipr1.phone, [], [PhoneNumber.copy(ipr1.sortedNumbers[0])], 10)

        then:
        res.status == ResultStatus.CREATED
        res.payload.all.size() == 1
        res.payload.all[0].id == IndividualPhoneRecord.last().id
        res.payload.all[0].numbers.find { it.number == ipr1.sortedNumbers[0].number }
        IndividualPhoneRecord.count()  == iprBaseline + 1
    }

    void "test permission constraint"() {
        given:
        PhoneRecord spr1 = TestUtils.buildSharedPhoneRecord()
        spr1.permission = SharePermission.NONE
        VoiceLanguage lang1 = VoiceLanguage.values()[0]

        when:
        Result res = Recipients.tryCreate([spr1], lang1, 10)

        then:
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages.contains("someNoPermissions")
    }

    void "test number of recipient constraint"() {
        given:
        VoiceLanguage lang1 = VoiceLanguage.values()[0]
        Phone p1 = TestUtils.buildActiveStaffPhone()
        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord(p1)
        IndividualPhoneRecord ipr2 = TestUtils.buildIndPhoneRecord(p1)
        IndividualPhoneRecord ipr3 = TestUtils.buildIndPhoneRecord(p1)
        GroupPhoneRecord gpr1 = TestUtils.buildGroupPhoneRecord(p1)
        gpr1.members.addToPhoneRecords(ipr2)
        gpr1.members.addToPhoneRecords(ipr3)
        PhoneRecord.withSession { it.flush() }

        when:
        Result res = Recipients.tryCreate([ipr1, ipr2, gpr1], lang1, 3, false)

        then:
        res.status == ResultStatus.CREATED
        res.payload.buildCount() == 3

        when:
        res = Recipients.tryCreate([ipr1, ipr2, gpr1], lang1, 10, true)

        then:
        res.status == ResultStatus.CREATED
        res.payload.buildCount() == 4

        when:
        res = Recipients.tryCreate([ipr1, ipr2, gpr1], lang1, 3, true)

        then:
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages.contains("tooManyRecipients")
    }

    void "test getters"() {
        given:
        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord()
        GroupPhoneRecord gpr1 = TestUtils.buildGroupPhoneRecord()
        VoiceLanguage lang1 = VoiceLanguage.values()[0]

        when:
        Recipients recip1 = Recipients.tryCreate([ipr1], lang1, 10).payload

        then:
        recip1.tryGetOne().payload == ipr1.toWrapper()
        recip1.tryGetOneIndividual().payload == ipr1.toWrapper()

        when:
        recip1 = Recipients.tryCreate([gpr1], lang1, 10).payload

        then:
        recip1.tryGetOne().payload == gpr1.toWrapper()
        recip1.tryGetOneIndividual().status == ResultStatus.UNPROCESSABLE_ENTITY

        when:
        recip1 = Recipients.tryCreate([gpr1, ipr1], lang1, 10).payload

        then:
        recip1.tryGetOne().payload == gpr1.toWrapper()
        recip1.tryGetOneIndividual().payload == ipr1.toWrapper()
    }

    void "test building from name"() {
        given:
        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord()
        VoiceLanguage lang1 = VoiceLanguage.values()[0]

        when:
        Recipients recip1 = Recipients.tryCreate([ipr1], lang1, 10).payload

        then:
        recip1.buildFromName() == ipr1.phone.owner.buildName()
    }

    void "test iterating through unique records"() {
        given:
        VoiceLanguage lang1 = VoiceLanguage.values()[0]
        Phone p1 = TestUtils.buildActiveTeamPhone()
        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord(p1)
        IndividualPhoneRecord ipr2 = TestUtils.buildIndPhoneRecord(p1)
        GroupPhoneRecord gpr1 = TestUtils.buildGroupPhoneRecord(p1)
        gpr1.members.addToPhoneRecords(ipr2)

        PhoneRecord.withSession { it.flush() }

        List records = []
        Closure doAction = { rec1 -> records << rec1 }

        when:
        Recipients.tryCreate([ipr1, gpr1], lang1, 10)
            .payload
            .eachRecord(doAction)

        then:
        records.size() == 3
        ipr1.record in records
        ipr2.record in records
        gpr1.record in records
    }

    void "test iterating through owned and shared `IndividualPhoneRecord`s"() {
        given:
        VoiceLanguage lang1 = VoiceLanguage.values()[0]
        Phone p1 = TestUtils.buildActiveTeamPhone()

        GroupPhoneRecord gpr1 = TestUtils.buildGroupPhoneRecord(p1)
        GroupPhoneRecord gpr2 = TestUtils.buildGroupPhoneRecord(p1)

        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord(p1)
        IndividualPhoneRecord ipr2 = TestUtils.buildIndPhoneRecord(p1)
        gpr1.members.addToPhoneRecords(ipr2)
        gpr2.members.addToPhoneRecords(ipr2)
        PhoneRecord spr1 = TestUtils.buildSharedPhoneRecord(null, p1)
        gpr1.members.addToPhoneRecords(spr1)

        PhoneRecord.withSession { it.flush() }

        List args = []
        Closure doAction = { arg1, arg2 -> args << [arg1, arg2] }

        when:
        Recipients.tryCreate([ipr1, gpr1, gpr2], lang1, 10)
            .payload
            .eachIndividualWithRecords(doAction)

        then:
        args.size() == 3
        args.find {
            it[0] == ipr1.toWrapper() && it[1] == [ipr1.record]
        }
        args.find {
            it[0] == ipr2.toWrapper() && it[1].size() == 3 &&
                ipr2.record in it[1] &&
                gpr1.record in it[1] &&
                gpr2.record in it[1]
        }
        args.find {
            it[0] == spr1.toWrapper() && it[1].size() == 2 &&
                spr1.record in it[1] &&
                gpr1.record in it[1]
        }

        when:
        args.clear()
        Recipients.tryCreate([ipr1, gpr2], lang1, 10)
            .payload
            .eachIndividualWithRecords(doAction)

        then:
        args.size() == 2
        args.find {
            it[0] == ipr1.toWrapper() && it[1] == [ipr1.record]
        }
        args.find {
            it[0] == ipr2.toWrapper() && it[1].size() == 2 &&
                ipr2.record in it[1] &&
                gpr2.record in it[1]
        }
    }
}
