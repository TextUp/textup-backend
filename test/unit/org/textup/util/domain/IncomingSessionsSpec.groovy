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
class IncomingSessionsSpec extends Specification {

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
        Phone p1 = TestUtils.buildStaffPhone(s1)

        IncomingSession is1 = TestUtils.buildSession(p1)
        IncomingSession is2 = TestUtils.buildSession(tp1)

        MockedMethod tryGetAuthId = TestUtils.mock(AuthUtils, "tryGetAuthId") { Result.createSuccess(s1.id) }

        when:
        Result res = IncomingSessions.isAllowed(null)

        then:
        res.status == ResultStatus.FORBIDDEN

        when:
        res = IncomingSessions.isAllowed(is1.id)

        then:
        res.status == ResultStatus.FORBIDDEN

        when:
        res = IncomingSessions.isAllowed(is2.id)

        then:
        res.status == ResultStatus.OK
        res.payload == is2.id

        cleanup:
        tryGetAuthId.restore()
    }

    void "test finding for id"() {
        given:
        IncomingSession is1 = TestUtils.buildSession()

        when:
        Result res = IncomingSessions.mustFindForId(null)

        then:
        res.status == ResultStatus.NOT_FOUND

        when:
        res = IncomingSessions.mustFindForId(is1.id)

        then:
        res.status == ResultStatus.OK
        res.payload == is1
    }

    void "test finding for phone and number"() {
        given:
        Phone p1 = TestUtils.buildTeamPhone()
        PhoneNumber pNum1 = TestUtils.randPhoneNumber()
        int isBaseline = IncomingSession.count()

        when:
        Result res = IncomingSessions.mustFindForPhoneAndNumber(null, null, true)

        then:
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        IncomingSession.count() == isBaseline

        when:
        res = IncomingSessions.mustFindForPhoneAndNumber(p1, pNum1, false)

        then:
        res.status == ResultStatus.NOT_FOUND
        IncomingSession.count() == isBaseline

        when:
        res = IncomingSessions.mustFindForPhoneAndNumber(p1, pNum1, true)

        then:
        res.status == ResultStatus.CREATED
        res.payload.phone == p1
        res.payload.number == pNum1
        IncomingSession.count() == isBaseline + 1

        when:
        Result res2 = IncomingSessions.mustFindForPhoneAndNumber(p1, pNum1, true)

        then:
        res2.status == ResultStatus.OK
        res2.payload == res.payload
        IncomingSession.count() == isBaseline + 1
    }

    void "test criteria for phone id and options"() {
        given:
        Phone p1 = TestUtils.buildTeamPhone()
        IncomingSession is1 = TestUtils.buildSession(p1)
        is1.isSubscribedToCall = false
        is1.isSubscribedToText = true
        IncomingSession is2 = TestUtils.buildSession(p1)
        is2.isSubscribedToCall = true
        is2.isSubscribedToText = false
        IncomingSession is3 = TestUtils.buildSession(p1)
        is3.isSubscribedToCall = true
        is3.isSubscribedToText = true

        IncomingSession.withSession { it.flush() }

        when:
        DetachedCriteria criteria = IncomingSessions.buildForPhoneIdWithOptions(null)

        then:
        criteria.count() == 0

        when:
        criteria = IncomingSessions.buildForPhoneIdWithOptions(p1.id)

        then:
        criteria.list() == [is1, is2, is3]

        when:
        criteria = IncomingSessions.buildForPhoneIdWithOptions(p1.id, true)

        then:
        criteria.list() == [is2, is3]

        when:
        criteria = IncomingSessions.buildForPhoneIdWithOptions(p1.id, true, false)

        then:
        criteria.list() == [is2]

        when:
        criteria = IncomingSessions.buildForPhoneIdWithOptions(p1.id, false, true)

        then:
        criteria.list() == [is1]

        when:
        criteria = IncomingSessions.buildForPhoneIdWithOptions(p1.id, false, false)

        then:
        criteria.count() ==  0
    }
}
