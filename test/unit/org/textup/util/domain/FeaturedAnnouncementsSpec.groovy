package org.textup.util.domain

import grails.gorm.DetachedCriteria
import grails.test.mixin.*
import grails.test.mixin.gorm.*
import grails.test.mixin.hibernate.*
import org.joda.time.*
import org.textup.*
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

class FeaturedAnnouncementsSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test is allowed"() {
        given:
        Team t1 = TestUtils.buildTeam()
        Staff s1 = TestUtils.buildStaff()
        t1.addToMembers(s1)

        Phone tp1 = TestUtils.buildActiveTeamPhone(t1)

        FeaturedAnnouncement fa1 = TestUtils.buildAnnouncement()
        FeaturedAnnouncement fa2 = TestUtils.buildAnnouncement(tp1)

        MockedMethod tryGetAuthId = MockedMethod.create(AuthUtils, "tryGetAuthId") { Result.createSuccess(s1.id) }

        when:
        Result res = FeaturedAnnouncements.isAllowed(null)

        then:
        res.status == ResultStatus.FORBIDDEN

        when:
        res = FeaturedAnnouncements.isAllowed(fa1.id)

        then:
        res.status == ResultStatus.FORBIDDEN

        when:
        res = FeaturedAnnouncements.isAllowed(fa2.id)

        then:
        res.status == ResultStatus.OK
        res.payload == fa2.id

        cleanup:
        tryGetAuthId.restore()
    }

    void "test finding given id"() {
        given:
        FeaturedAnnouncement fa1 = TestUtils.buildAnnouncement()

        when:
        Result res = FeaturedAnnouncements.mustFindForId(null)

        then:
        res.status == ResultStatus.NOT_FOUND

        when:
        res = FeaturedAnnouncements.mustFindForId(-88L)

        then:
        res.status == ResultStatus.NOT_FOUND

        when:
        res = FeaturedAnnouncements.mustFindForId(fa1.id)

        then:
        res.status == ResultStatus.OK
        res.payload == fa1
    }

    void "test criteria"() {
        given:
        Phone p1 = TestUtils.buildStaffPhone()
        FeaturedAnnouncement fa1 = TestUtils.buildAnnouncement(p1)
        FeaturedAnnouncement fa2 = TestUtils.buildAnnouncement(p1)
        fa2.whenCreated = DateTime.now().minusDays(2)
        fa2.expiresAt = DateTime.now().plusDays(2)

        when:
        DetachedCriteria criteria = FeaturedAnnouncements.buildActiveForPhoneId(null)

        then:
        criteria.count() == 0
        criteria.list() == []

        when:
        criteria = FeaturedAnnouncements.buildActiveForPhoneId(p1.id)

        then:
        criteria.count() == 2
        criteria.build(FeaturedAnnouncements.forSort()).list() == [fa2, fa1]
    }

    void "test checking to see if any active announcements for phone id"() {
        given:
        Phone p1 = TestUtils.buildStaffPhone()
        Phone p2 = TestUtils.buildStaffPhone()
        FeaturedAnnouncement fa1 = TestUtils.buildAnnouncement(p1)

        expect:
        FeaturedAnnouncements.anyForPhoneId(null) == false
        FeaturedAnnouncements.anyForPhoneId(p2.id) == false
        FeaturedAnnouncements.anyForPhoneId(p1.id)
    }
}
