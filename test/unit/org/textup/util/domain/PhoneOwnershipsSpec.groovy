package org.textup.util.domain

import grails.gorm.DetachedCriteria
import grails.test.mixin.*
import grails.test.mixin.gorm.*
import grails.test.mixin.hibernate.*
import org.joda.time.*
import org.textup.*
import org.textup.cache.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.validator.*
import org.textup.util.*
import spock.lang.*

@Domain([AnnouncementReceipt, ContactNumber, CustomAccountDetails, FeaturedAnnouncement,
    FutureMessage, GroupPhoneRecord, IncomingSession, IndividualPhoneRecord, Location, MediaElement,
    MediaElementVersion, MediaInfo, Organization, OwnerPolicy, Phone, PhoneNumberHistory,
    PhoneOwnership, PhoneRecord, PhoneRecordMembers, Record, RecordCall, RecordItem,
    RecordItemReceipt, RecordNote, RecordNoteRevision, RecordText, Role, Schedule,
    SimpleFutureMessage, Staff, StaffRole, Team, Token])
@TestMixin(HibernateTestMixin)
class PhoneOwnershipsSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test getting any staff phones for staff id"() {
        given:
        Staff s1 = TestUtils.buildStaff()
        Phone p1 = TestUtils.buildStaffPhone(s1)
        Phone p2 = TestUtils.buildActiveStaffPhone(s1)

        when:
        DetachedCriteria criteria = PhoneOwnerships.buildAnyStaffPhonesForStaffId(null)

        then:
        criteria.count() == 0

        when:
        criteria = PhoneOwnerships.buildAnyStaffPhonesForStaffId(s1.id)

        then:
        criteria.list() == [p1, p2]*.owner
        criteria.build(PhoneOwnerships.returnsPhoneId()).list() == [p1, p2]*.id
    }

    void "test getting any team phones for staff id"() {
        given:
        Team t1 = TestUtils.buildTeam()
        Staff s1 = TestUtils.buildStaff()
        t1.addToMembers(s1)

        Phone p1 = TestUtils.buildTeamPhone(t1)
        Phone p2 = TestUtils.buildActiveTeamPhone(t1)

        when:
        DetachedCriteria criteria = PhoneOwnerships.buildAnyTeamPhonesForStaffId(null)

        then:
        criteria.count() == 0

        when:
        criteria = PhoneOwnerships.buildAnyTeamPhonesForStaffId(s1.id)

        then:
        criteria.list() == [p1, p2]*.owner
    }
}
