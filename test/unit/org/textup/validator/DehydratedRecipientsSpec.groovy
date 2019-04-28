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
class DehydratedRecipientsSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test creation + rehydration"() {
        given:
        int maxNum = 10
        Phone p1 = TestUtils.buildActiveStaffPhone()
        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord(p1)
        VoiceLanguage lang1 = VoiceLanguage.values()[0]
        Recipients recips1 = Recipients.tryCreate([ipr1], lang1, maxNum).payload

        when:
        Result res = DehydratedRecipients.tryCreate(null)

        then:
        res.status == ResultStatus.INTERNAL_SERVER_ERROR

        when:
        res = DehydratedRecipients.tryCreate(recips1)

        then:
        res.status == ResultStatus.CREATED
        res.payload.phoneId == p1.id
        res.payload.allIds == [ipr1.id]
        res.payload.maxNum == maxNum
        res.payload.language == lang1

        when:
        res = res.payload.tryRehydrate()

        then:
        res.status == ResultStatus.CREATED
        res.payload.phone == p1
        res.payload.all.size() ==1
        res.payload.all[0] == ipr1
        res.payload.maxNum == maxNum
        res.payload.language == lang1
    }
}
