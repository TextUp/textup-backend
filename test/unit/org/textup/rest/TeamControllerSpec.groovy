package org.textup.rest

import grails.gorm.DetachedCriteria
import grails.test.mixin.*
import grails.test.mixin.gorm.*
import grails.test.mixin.hibernate.*
import org.textup.*
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
@TestFor(TeamController)
@TestMixin(HibernateTestMixin)
class TeamControllerSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test index"() {
        given:
        String tzId = TestUtils.randString()
        Long orgId = TestUtils.randIntegerUpTo(88)
        Long sId1 = TestUtils.randIntegerUpTo(88)
        Long sId2 = TestUtils.randIntegerUpTo(88)

        DetachedCriteria crit1 = GroovyMock()
        DetachedCriteria crit2 = GroovyMock()
        MockedMethod tryGetAuthId = MockedMethod.create(AuthUtils, "tryGetAuthId") {
            Result.createSuccess(sId1)
        }
        MockedMethod buildActiveForOrgIds = MockedMethod.create(Teams, "buildActiveForOrgIds") {
            crit1
        }
        MockedMethod buildActiveForStaffIds = MockedMethod.create(Teams, "buildActiveForStaffIds") {
            crit2
        }
        MockedMethod respondWithCriteria = MockedMethod.create(controller, "respondWithCriteria")

        when:
        params.timezone = tzId
        controller.index()

        then:
        buildActiveForStaffIds.latestArgs == [[sId1]]
        respondWithCriteria.latestArgs == [crit2, params, null, MarshallerUtils.KEY_TEAM]
        RequestUtils.tryGet(RequestUtils.TIMEZONE).payload == tzId

        when:
        params.clear()
        response.reset()

        params.staffId = sId2
        controller.index()

        then:
        buildActiveForStaffIds.latestArgs == [[sId2]]
        respondWithCriteria.latestArgs == [crit2, params, null, MarshallerUtils.KEY_TEAM]
        RequestUtils.tryGet(RequestUtils.TIMEZONE).payload == null

        when:
        params.clear()
        response.reset()

        params.organizationId = orgId
        controller.index()

        then:
        buildActiveForOrgIds.latestArgs == [[orgId]]
        respondWithCriteria.latestArgs == [crit1, params, null, MarshallerUtils.KEY_TEAM]
        RequestUtils.tryGet(RequestUtils.TIMEZONE).payload == null

        cleanup:
        tryGetAuthId?.restore()
        buildActiveForOrgIds?.restore()
        buildActiveForStaffIds?.restore()
        respondWithCriteria?.restore()
    }

    void "test show"() {
        given:
        String tzId = TestUtils.randString()
        Long id = TestUtils.randIntegerUpTo(88)

        MockedMethod doShow = MockedMethod.create(controller, "doShow")
        MockedMethod isAllowed = MockedMethod.create(Teams, "isAllowed")
        MockedMethod mustFindForId = MockedMethod.create(Teams, "mustFindForId")

        when:
        params.id = id
        params.timezone = tzId
        controller.show()

        then:
        doShow.latestArgs[0] instanceof Closure
        doShow.latestArgs[1] instanceof Closure
        RequestUtils.tryGet(RequestUtils.TIMEZONE).payload == tzId

        when:
        doShow.latestArgs[0].call()
        doShow.latestArgs[1].call()

        then:
        isAllowed.latestArgs == [id]
        mustFindForId.latestArgs == [id]

        cleanup:
        doShow?.restore()
        isAllowed?.restore()
        mustFindForId?.restore()
    }

    void "test save"() {
        given:
        String tzId = TestUtils.randString()
        Long orgId = TestUtils.randIntegerUpTo(88)
        TypeMap body = TypeMap.create(org: orgId)

        controller.teamService = GroovyMock(TeamService)
        MockedMethod doSave = MockedMethod.create(controller, "doSave")
        MockedMethod isAllowed = MockedMethod.create(Organizations, "isAllowed")

        when:
        params.timezone = tzId
        controller.save()

        then:
        doSave.latestArgs[0] == MarshallerUtils.KEY_TEAM
        doSave.latestArgs[1] == request
        doSave.latestArgs[2] == controller.teamService
        doSave.latestArgs[3] instanceof Closure
        RequestUtils.tryGet(RequestUtils.TIMEZONE).payload == tzId

        when:
        doSave.latestArgs[3].call(body)

        then:
        isAllowed.latestArgs == [orgId]

        cleanup:
        doSave?.restore()
        isAllowed?.restore()
    }

    void "test update"() {
        given:
        String tzId = TestUtils.randString()
        Long id = TestUtils.randIntegerUpTo(88)

        controller.teamService = GroovyMock(TeamService)
        MockedMethod doUpdate = MockedMethod.create(controller, "doUpdate")
        MockedMethod isAllowed = MockedMethod.create(Teams, "isAllowed")

        when:
        params.id = id
        params.timezone = tzId
        controller.update()

        then:
        doUpdate.latestArgs[0] == MarshallerUtils.KEY_TEAM
        doUpdate.latestArgs[1] == request
        doUpdate.latestArgs[2] == controller.teamService
        doUpdate.latestArgs[3] instanceof Closure
        RequestUtils.tryGet(RequestUtils.TIMEZONE).payload == tzId

        when:
        doUpdate.latestArgs[3].call()

        then:
        isAllowed.latestArgs == [id]

        cleanup:
        doUpdate?.restore()
        isAllowed?.restore()
    }

    void "test delete"() {
        given:
        Long id = TestUtils.randIntegerUpTo(88)

        controller.teamService = GroovyMock(TeamService)
        MockedMethod doDelete = MockedMethod.create(controller, "doDelete")
        MockedMethod isAllowed = MockedMethod.create(Teams, "isAllowed")

        when:
        params.id = id
        controller.delete()

        then:
        doDelete.latestArgs[0] == controller.teamService
        doDelete.latestArgs[1] instanceof Closure

        when:
        doDelete.latestArgs[1].call()

        then:
        isAllowed.latestArgs == [id]

        cleanup:
        doDelete?.restore()
        isAllowed?.restore()
    }
}
