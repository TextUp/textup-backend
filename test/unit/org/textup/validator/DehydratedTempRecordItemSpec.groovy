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
class DehydratedTempRecordItemSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test creation + rehydration"() {
        given:
        String text = TestUtils.randString()
        Location loc1 = TestUtils.buildLocation()
        MediaInfo mInfo1 = TestUtils.buildMediaInfo()
        TempRecordItem tempItem1 = TempRecordItem.tryCreate(text, mInfo1, loc1).payload

        when:
        Result res = DehydratedTempRecordItem.tryCreate(null)

        then:
        res.status == ResultStatus.INTERNAL_SERVER_ERROR

        when:
        res = DehydratedTempRecordItem.tryCreate(tempItem1)

        then:
        res.status == ResultStatus.CREATED
        res.payload.text == text
        res.payload.mediaId == mInfo1.id
        res.payload.locationId == loc1.id

        when:
        res = res.payload.tryRehydrate()

        then:
        res.status == ResultStatus.CREATED
        res.payload == tempItem1
    }
}
