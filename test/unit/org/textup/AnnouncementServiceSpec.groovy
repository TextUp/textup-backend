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
@TestFor(AnnouncementService)
@TestMixin(HibernateTestMixin)
class AnnouncementServiceSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test create"() {
        given:
        Staff s1 = TestUtils.buildStaff()
        Phone p1 = TestUtils.buildActiveStaffPhone()
        TypeMap body1 = TypeMap.create()
        TypeMap body2 = TypeMap.create(expiresAt: DateTime.now().plusDays(1),
            message: TestUtils.randString())

        service.outgoingAnnouncementService = GroovyMock(OutgoingAnnouncementService)
        MockedMethod tryGetActiveAuthUser = MockedMethod.create(AuthUtils, "tryGetActiveAuthUser") {
            Result.createSuccess(s1)
        }

    	when:
    	Result res = service.tryCreate(null, null)

    	then:
        tryGetActiveAuthUser.notCalled
        0 * service.outgoingAnnouncementService._
    	res.status == ResultStatus.NOT_FOUND

        when:
        res = service.tryCreate(p1.id, body1)

        then:
        tryGetActiveAuthUser.notCalled
        0 * service.outgoingAnnouncementService._
        res.status == ResultStatus.UNPROCESSABLE_ENTITY

        when: "success, having mocked method on phone"
        res = service.tryCreate(p1.id, body2)

        then:
        tryGetActiveAuthUser.hasBeenCalled
        1 * service.outgoingAnnouncementService.send(_ as FeaturedAnnouncement, Author.create(s1)) >>
            { args -> Result.createSuccess(args[0]) }
        res.status == ResultStatus.CREATED
        res.payload.expiresAt == body2.expiresAt
        res.payload.message == body2.message

        cleanup:
        tryGetActiveAuthUser?.restore()
    }

    void "test update"() {
    	given: "baselines and existing announcement"
        FeaturedAnnouncement fa1 = TestUtils.buildAnnouncement()
        TypeMap body1 = TypeMap.create(expiresAt: DateTime.now().plusDays(1),
            message: TestUtils.randString())

    	int faBaseline = FeaturedAnnouncement.count()

    	when:
    	Result res = service.tryUpdate(null, null)

    	then:
    	res.status == ResultStatus.NOT_FOUND
        FeaturedAnnouncement.count() == faBaseline

    	when:
    	res = service.tryUpdate(fa1.id, body1)

    	then:
    	res.status == ResultStatus.OK
        res.payload.expiresAt == body1.expiresAt
        res.payload.message != body1.message
        FeaturedAnnouncement.count() == faBaseline
    }
}
