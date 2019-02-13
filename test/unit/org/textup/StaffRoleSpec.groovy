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
class StaffRoleSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test creation"() {
        given:
        Staff s1 = TestUtils.buildStaff()
        Role role1 = TestUtils.buildRole()

        when:
        Result res = StaffRole.tryCreate(null, null)

        then:
        res.status == ResultStatus.UNPROCESSABLE_ENTITY

        when:
        res = StaffRole.tryCreate(s1, role1)
        StaffRole.withSession { it.flush() }

        then:
        res.status == ResultStatus.CREATED
        res.payload.staff == s1
        res.payload.role == role1

        when:
        res = StaffRole.tryCreate(s1, role1)
        StaffRole.withSession { it.flush() }

        then: "cannot save duplicate"
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
    }
}
