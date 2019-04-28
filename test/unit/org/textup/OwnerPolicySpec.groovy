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
class OwnerPolicySpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test creation and constraints"() {
        given:
        Phone p1 = TestUtils.buildStaffPhone()
        Staff s1 = TestUtils.buildStaff()

        when:
        Result res = OwnerPolicy.tryCreate(null, null)

        then:
        res.status == ResultStatus.NOT_FOUND

        when:
        res = OwnerPolicy.tryCreate(p1.owner, s1.id)

        then:
        res.status == ResultStatus.CREATED
        res.payload.validate()
        res.payload.staff == s1
        res.payload.schedule != null

        when:
        res.payload.with {
            frequency = NotificationFrequency.IMMEDIATELY
            method = NotificationMethod.EMAIL
        }

        then:
        res.payload.validate() == false
        res.payload.errors.getFieldErrorCount("frequency") == 1
    }
}
