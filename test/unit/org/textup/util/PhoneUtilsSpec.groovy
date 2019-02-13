package org.textup.util

import grails.test.mixin.*
import grails.test.mixin.gorm.*
import grails.test.mixin.hibernate.*
import grails.test.mixin.support.*
import org.joda.time.DateTime
import org.textup.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.domain.*
import org.textup.validator.*
import spock.lang.*

@Domain([AnnouncementReceipt, ContactNumber, CustomAccountDetails, FeaturedAnnouncement,
    FutureMessage, GroupPhoneRecord, IncomingSession, IndividualPhoneRecord, Location, MediaElement,
    MediaElementVersion, MediaInfo, Organization, OwnerPolicy, Phone, PhoneNumberHistory,
    PhoneOwnership, PhoneRecord, PhoneRecordMembers, Record, RecordCall, RecordItem,
    RecordItemReceipt, RecordNote, RecordNoteRevision, RecordText, Role, Schedule,
    SimpleFutureMessage, Staff, StaffRole, Team, Token])
@TestMixin(HibernateTestMixin)
class PhoneUtilsSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test adding change to number history"() {
        given:
        Phone p1 = TestUtils.buildStaffPhone()
        PhoneNumber invalidNum = PhoneNumber.create("invalid")
        PhoneNumber pNum1 = TestUtils.randPhoneNumber()

        when:
        Result res = PhoneUtils.tryAddChangeToHistory(null, null)

        then:
        res.status == ResultStatus.INTERNAL_SERVER_ERROR
        p1.numberHistoryEntries == null

        when:
        res = PhoneUtils.tryAddChangeToHistory(p1, invalidNum)

        then: "invalid nums treated as no number"
        res.status == ResultStatus.OK
        p1.numberHistoryEntries.size() == 1
        p1.numberHistoryEntries[0].numberAsString == null
        p1.numberHistoryEntries[0].endTime == null

        when:
        res = PhoneUtils.tryAddChangeToHistory(p1, pNum1)

        then:
        res.status == ResultStatus.OK
        p1.numberHistoryEntries.size() == 2
        p1.numberHistoryEntries[0].endTime.monthOfYear == p1.numberHistoryEntries[1].whenCreated.monthOfYear
        p1.numberHistoryEntries[0].endTime.year == p1.numberHistoryEntries[1].whenCreated.year
        p1.numberHistoryEntries[1].numberAsString == pNum1.number
        p1.numberHistoryEntries[1].endTime == null
    }
}
