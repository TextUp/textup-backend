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
class FeaturedAnnouncementSpec extends Specification {

	static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test creation + constraints"() {
        given:
        Phone p1 = TestUtils.buildStaffPhone()
        String msg = TestUtils.randString()
        DateTime dt = DateTime.now().plusDays(2)

    	when: "is empty"
        Result res = FeaturedAnnouncement.tryCreate(null, null, null)

    	then:
        res.status == ResultStatus.UNPROCESSABLE_ENTITY

    	when: "expires in past"
        res = FeaturedAnnouncement.tryCreate(p1, DateTime.now().minusDays(2), msg)

    	then:
    	res.status == ResultStatus.UNPROCESSABLE_ENTITY

        when:
        res = FeaturedAnnouncement.tryCreate(p1, dt, msg)

        then:
        res.status == ResultStatus.CREATED
        res.payload.message == msg
        res.payload.phone == p1
        res.payload.expiresAt == dt
        res.payload.isExpired == false

        when: "expire"
        res.payload.with {
            whenCreated = DateTime.now().minusHours(1)
            expiresAt = DateTime.now().minusMinutes(8)
        }

        then:
        res.payload.isExpired == true
    }
}
