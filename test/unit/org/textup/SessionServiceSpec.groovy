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
@TestFor(SessionService)
class SessionServiceSpec extends Specification {

   static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test creating"() {
        given:
        Phone p1 = TestUtils.buildActiveTeamPhone()
        TypeMap body1 = TypeMap.create(number: TestUtils.randString())
        TypeMap body2 = TypeMap.create(number: TestUtils.randPhoneNumberString(),
            isSubscribedToText: true,
            isSubscribedToCall: true)

        int isBaseline = IncomingSession.count()

        when:
        Result res = service.tryCreate(null, null)

        then:
        res.status == ResultStatus.NOT_FOUND
        IncomingSession.count() == isBaseline

        when:
        res = service.tryCreate(p1.id, body1)

        then:
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        IncomingSession.count() == isBaseline

        when:
        res = service.tryCreate(p1.id, body2)

        then:
        res.status == ResultStatus.CREATED
        res.payload.phone == p1
        res.payload.number == PhoneNumber.create(body2.number)
        res.payload.isSubscribedToText == true
        res.payload.isSubscribedToCall == true
        IncomingSession.count() == isBaseline + 1
    }

    void "test updating"() {
        given:
        IncomingSession is1 = TestUtils.buildSession()
        TypeMap body1 = TypeMap.create(isSubscribedToText: true, isSubscribedToCall: true)

        when:
        Result res = service.tryUpdate(null, null)

        then:
        res.status == ResultStatus.NOT_FOUND

        when:
        res = service.tryUpdate(is1.id, body1)

        then:
        res.status == ResultStatus.OK
        res.payload == is1
        res.payload.isSubscribedToText == true
        res.payload.isSubscribedToCall == true
    }
}
