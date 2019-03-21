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
import org.textup.util.domain.*
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
        Long staffId = TestUtils.randIntegerUpTo(88)
        Long authId = TestUtils.randIntegerUpTo(88)

        Organization org1 = TestUtils.buildOrg()
        Team t1 = TestUtils.buildTeam(org1)
        Phone p1 = TestUtils.buildStaffPhone()

        service.phoneService = GroovyMock(PhoneService)
        MockedMethod tryGetAuthId = MockedMethod.create(AuthUtils, "tryGetAuthId") {
            Result.createSuccess(authId)
        }
        MockedMethod isAllowed = MockedMethod.create(Staffs, "isAllowed") { Long sId ->
            Result.createSuccess(sId)
        }

        when:
        Result res = service.tryUpdatePhone(null, null, null, null)

        then:
        tryGetAuthId.notCalled
        isAllowed.notCalled
        res.status == ResultStatus.NO_CONTENT

        when:
        res = service.tryUpdatePhone(t1, null, pInfo, tz)

        then:
        tryGetAuthId.hasBeenCalled
        isAllowed.hasBeenCalled
        1 * service.phoneService.tryFindAnyIdOrCreateImmediatelyForOwner(t1.id, PhoneOwnershipType.GROUP) >>
            Result.createSuccess(p1.id)
        1 * service.phoneService.tryUpdate(p1, pInfo, authId, tz) >> Result.void()
        res.status == ResultStatus.NO_CONTENT

        when:
        res = service.tryUpdatePhone(t1, staffId, pInfo, tz)

        then:
        tryGetAuthId.hasBeenCalled
        isAllowed.hasBeenCalled
        1 * service.phoneService.tryFindAnyIdOrCreateImmediatelyForOwner(t1.id, PhoneOwnershipType.GROUP) >>
            Result.createSuccess(p1.id)
        1 * service.phoneService.tryUpdate(p1, pInfo, staffId, tz) >> Result.void()
        res.status == ResultStatus.NO_CONTENT

        cleanup:
        tryGetAuthId?.restore()
        isAllowed?.restore()
    }

    void "test handling team actions"() {
        given:
        TypeMap body = TestUtils.randTypeMap()
        Team t1 = GroovyMock()
        service.teamActionService = GroovyMock(TeamActionService)

        when:
        Result res = service.tryHandleTeamActions(t1, body)

        then:
        1 * service.teamActionService.hasActions(body) >> false
        res.status == ResultStatus.OK
        res.payload == t1

        when:
        res = service.tryHandleTeamActions(t1, body)

        then:
        1 * service.teamActionService.hasActions(body) >> true
        1 * service.teamActionService.tryHandleActions(t1, body) >> Result.void()
        res.status == ResultStatus.NO_CONTENT
    }

    void "test trying to update fields"() {
        given:
        TypeMap body = TypeMap.create(name: TestUtils.randString(), hexColor: "#889900")
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
            staffId: TestUtils.randIntegerUpTo(88),
            phone: TestUtils.randTypeMap())
        Team t1 = TestUtils.buildTeam()

        service.locationService = GroovyMock(LocationService)
        MockedMethod trySetFields = MockedMethod.create(service, "trySetFields") {
            Result.createSuccess(t1)
        }
        MockedMethod tryHandleTeamActions = MockedMethod.create(service, "tryHandleTeamActions") {
            Result.createSuccess(t1)
        }
        MockedMethod tryUpdatePhone = MockedMethod.create(service, "tryUpdatePhone") {
            Result.createSuccess(t1)
        }

        when:
        Result res = service.tryUpdate(t1.id, body)

        then:
        trySetFields.latestArgs == [t1, body]
        tryHandleTeamActions.latestArgs == [t1, body]
        1 * service.locationService.tryUpdate(t1.location, body.location) >> Result.void()
        tryUpdatePhone.latestArgs == [t1, body.staffId, body.phone, body.timezone]
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
            staffId: TestUtils.randIntegerUpTo(88),
            phone: TestUtils.randTypeMap(),
            name: TestUtils.randString())

        Organization org1 = TestUtils.buildOrg()
        Location loc1 = TestUtils.buildLocation()

        service.locationService = GroovyMock(LocationService)
        MockedMethod trySetFields = MockedMethod.create(service, "trySetFields") { Team t1 ->
            Result.createSuccess(t1)
        }
        MockedMethod tryHandleTeamActions = MockedMethod.create(service, "tryHandleTeamActions") { Team t1 ->
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
        tryHandleTeamActions.latestArgs[0] instanceof Team
        tryHandleTeamActions.latestArgs[1] == body
        tryUpdatePhone.latestArgs[0] instanceof Team
        tryUpdatePhone.latestArgs[1] == body.staffId
        tryUpdatePhone.latestArgs[2] == body.phone
        tryUpdatePhone.latestArgs[3] == body.timezone
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
