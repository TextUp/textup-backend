package org.textup

import grails.test.mixin.*
import grails.test.mixin.gorm.*
import grails.test.mixin.hibernate.*
import grails.test.mixin.support.*
import grails.test.runtime.*
import grails.validation.*
import org.joda.time.*
import org.textup.action.*
import org.textup.cache.*
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
@TestFor(TeamService)
@TestMixin(HibernateTestMixin)
class TeamServiceSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
        phoneCache(PhoneCache)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test trying to update phone"() {
        given:
        TypeMap pInfo = TestUtils.randTypeMap()
        String tz = TestUtils.randString()

        Organization org1 = TestUtils.buildOrg()
        Team t1 = TestUtils.buildTeam(org1)
        Staff s1 = TestUtils.buildStaff(org1)
        s1.status = StaffStatus.ADMIN
        Team.withSession { it.flush() }

        int pBaseline = Phone.count()

        service.phoneService = GroovyMock(PhoneService)
        MockedMethod tryGetAuthId = MockedMethod.create(AuthUtils, "tryGetAuthId") {
            Result.createSuccess(s1.id)
        }

        when:
        Result res = service.tryUpdatePhone(null, null, null)

        then:
        tryGetAuthId.notCalled
        res.status == ResultStatus.NO_CONTENT
        Phone.count() == pBaseline

        when:
        res = service.tryUpdatePhone(t1, pInfo, tz)

        then:
        tryGetAuthId.hasBeenCalled
        1 * service.phoneService.tryUpdate(_, pInfo, tz) >> Result.void()
        res.status == ResultStatus.NO_CONTENT
        Phone.count() == pBaseline + 1
        IOCUtils.phoneCache.findAnyPhoneIdForOwner(t1.id, PhoneOwnershipType.GROUP) != null

        cleanup:
        tryGetAuthId.restore()
    }

    void "test trying to update fields"() {
        given:
        TypeMap body = TypeMap.create(name: TestUtils.randString(),
            hexColor: "#889900")
        Team t1 = TestUtils.buildTeam()

        when:
        Result res = service.trySetFields(t1, body)

        then:
        res.status == ResultStatus.OK
        res.payload == t1
        t1.name == body.name
        t1.hexColor == body.hexColor
    }

    void "test deleting"() {
        given:
        Team t1 = TestUtils.buildTeam()

        when:
        Result res = service.tryDelete(t1.id)

        then:
        res.status == ResultStatus.OK
        res.payload == t1
        t1.isDeleted
    }

    void "test updating overall"() {
        given:
        TypeMap body = TypeMap.create(location: TestUtils.randTypeMap(),
            timezone: TestUtils.randString(),
            phone: TestUtils.randTypeMap())
        Team t1 = TestUtils.buildTeam()

        service.locationService = GroovyMock(LocationService)
        service.teamActionService = GroovyMock(TeamActionService)
        MockedMethod trySetFields = MockedMethod.create(service, "trySetFields") {
            Result.createSuccess(t1)
        }
        MockedMethod tryUpdatePhone = MockedMethod.create(service, "tryUpdatePhone") {
            Result.createSuccess(t1)
        }

        when:
        Result res = service.tryUpdate(t1.id, body)

        then:
        trySetFields.latestArgs == [t1, body]
        1 * service.locationService.tryUpdate(t1.location, body.location) >> Result.void()
        1 * service.teamActionService.tryHandleActions(t1, body) >> Result.createSuccess(t1)
        tryUpdatePhone.latestArgs == [t1, body.phone, body.timezone]
        res.status == ResultStatus.OK
        res.payload == t1

        cleanup:
        trySetFields?.restore()
        tryUpdatePhone?.restore()
    }

    void "test creating overall"() {
        given:
        TypeMap body = TypeMap.create(location: TestUtils.randTypeMap(),
            timezone: TestUtils.randString(),
            phone: TestUtils.randTypeMap(),
            name: TestUtils.randString())

        Organization org1 = TestUtils.buildOrg()
        Location loc1 = TestUtils.buildLocation()

        service.locationService = GroovyMock(LocationService)
        service.teamActionService = GroovyMock(TeamActionService)
        MockedMethod trySetFields = MockedMethod.create(service, "trySetFields") { Team t1 ->
            Result.createSuccess(t1)
        }
        MockedMethod tryUpdatePhone = MockedMethod.create(service, "tryUpdatePhone") { Team t1 ->
            Result.createSuccess(t1)
        }

        when:
        Result res = service.tryCreate(org1.id, body)

        then:
        1 * service.locationService.tryCreate(body.location) >> Result.createSuccess(loc1)
        trySetFields.latestArgs[0] instanceof Team
        trySetFields.latestArgs[1] == body
        1 * service.teamActionService.tryHandleActions(_ as Team, body) >> { args ->
            Result.createSuccess(args[0])
        }
        tryUpdatePhone.latestArgs[0] instanceof Team
        tryUpdatePhone.latestArgs[1] == body.phone
        tryUpdatePhone.latestArgs[2] == body.timezone
        res.status == ResultStatus.CREATED
        res.payload instanceof Team
        res.payload.name == body.name
        res.payload.location == loc1
        res.payload.org == org1

        cleanup:
        trySetFields?.restore()
        tryUpdatePhone?.restore()
    }
}
