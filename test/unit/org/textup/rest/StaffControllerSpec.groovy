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
@TestFor(StaffController)
@TestMixin(HibernateTestMixin)
class StaffControllerSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test show"() {
        given:
        String tzId = TestUtils.randString()
        Long id = TestUtils.randIntegerUpTo(88)

        MockedMethod doShow = MockedMethod.create(controller, "doShow")
        MockedMethod isAllowed = MockedMethod.create(Staffs, "isAllowed")
        MockedMethod mustFindForId = MockedMethod.create(Staffs, "mustFindForId")

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
        String err1 = TestUtils.randString()
        TypeMap body = TestUtils.randTypeMap()

        controller.staffService = GroovyMock(StaffService)
        MockedMethod tryGetJsonBody = MockedMethod.create(RequestUtils, "tryGetJsonBody") {
            Result.createError([err1], ResultStatus.FORBIDDEN)
        }

        when:
        params.timezone = tzId
        controller.save()

        then:
        tryGetJsonBody.latestArgs == [request, MarshallerUtils.KEY_STAFF]
        0 * controller.staffService._
        response.status == ResultStatus.FORBIDDEN.intStatus
        response.text.contains(err1)
        RequestUtils.tryGet(RequestUtils.TIMEZONE).payload == tzId

        when:
        tryGetJsonBody = MockedMethod.create(tryGetJsonBody) { Result.createSuccess(body) }
        response.reset()
        controller.save()

        then:
        tryGetJsonBody.latestArgs == [request, MarshallerUtils.KEY_STAFF]
        1 * controller.staffService.tryCreate(body) >> Result.void()
        response.status == ResultStatus.NO_CONTENT.intStatus
        RequestUtils.tryGet(RequestUtils.TIMEZONE).payload == tzId

        cleanup:
        tryGetJsonBody?.restore()
    }

    void "test update"() {
        given:
        String tzId = TestUtils.randString()
        Long id = TestUtils.randIntegerUpTo(88)

        controller.staffService = GroovyMock(StaffService)
        MockedMethod doUpdate = MockedMethod.create(controller, "doUpdate")
        MockedMethod isAllowed = MockedMethod.create(Staffs, "isAllowed")

        when:
        params.id = id
        params.timezone = tzId
        controller.update()

        then:
        doUpdate.latestArgs[0] == MarshallerUtils.KEY_STAFF
        doUpdate.latestArgs[1] == request
        doUpdate.latestArgs[2] == controller.staffService
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

    void "test listing for org"() {
        given:
        Long orgId = TestUtils.randIntegerUpTo(88)
        StaffStatus stat1 = StaffStatus.values()[0]
        TypeMap qParams = TypeMap.create(organizationId: TestUtils.randIntegerUpTo(88),
            search: TestUtils.randString())

        DetachedCriteria crit1 = GroovyMock()
        MockedMethod isAllowed = MockedMethod.create(Organizations, "isAllowed") {
            Result.createSuccess(orgId)
        }
        MockedMethod buildForOrgIdAndOptions = MockedMethod.create(Staffs, "buildForOrgIdAndOptions") {
            crit1
        }
        MockedMethod respondWithCriteria = MockedMethod.create(controller, "respondWithCriteria")

        when:
        controller.listForOrg([stat1], qParams)

        then:
        isAllowed.latestArgs == [qParams.organizationId]
        buildForOrgIdAndOptions.latestArgs == [orgId, qParams.search, [stat1]]
        respondWithCriteria.latestArgs == [crit1, params, null, MarshallerUtils.KEY_STAFF]

        cleanup:
        isAllowed?.restore()
        respondWithCriteria?.restore()
    }

    void "test listing for team"() {
        given:
        StaffStatus stat1 = StaffStatus.values()[0]

        Team t1 = TestUtils.buildTeam()
        Staff s1 = TestUtils.buildStaff()
        s1.status = stat1
        t1.addToMembers(s1)
        Staff.withSession { it.flush() }

        TypeMap qParams = TypeMap.create(teamId: t1.id)

        MockedMethod isAllowed = MockedMethod.create(Teams, "isAllowed") {
            Result.createSuccess(t1.id)
        }
        MockedMethod respondWithClosures = MockedMethod.create(controller, "respondWithClosures")

        when:
        controller.listForTeam([stat1], qParams)

        then:
        isAllowed.latestArgs == [t1.id]
        respondWithClosures.latestArgs[2] == qParams
        respondWithClosures.latestArgs[3] == MarshallerUtils.KEY_STAFF

        and:
        respondWithClosures.latestArgs[0].call() == 1
        respondWithClosures.latestArgs[1].call().size() == 1
        respondWithClosures.latestArgs[1].call()[0] == s1

        cleanup:
        isAllowed?.restore()
        respondWithClosures?.restore()
    }

    void "test listing for staff that user can share with"() {
        given:
        Long sId = TestUtils.randIntegerUpTo(88)
        TypeMap qParams = TypeMap.create(shareStaffId: sId)

        Staff s1 = GroovyMock()
        MockedMethod isAllowed = MockedMethod.create(Staffs, "isAllowed") {
            Result.createSuccess(sId)
        }
        MockedMethod findEveryForSharingId = MockedMethod.create(Staffs, "findEveryForSharingId") {
            [s1]
        }
        MockedMethod respondWithClosures = MockedMethod.create(controller, "respondWithClosures")

        when:
        controller.listForShareStaff(qParams)

        then:
        isAllowed.latestArgs == [sId]
        findEveryForSharingId.latestArgs == [sId]
        respondWithClosures.latestArgs[2] == qParams
        respondWithClosures.latestArgs[3] == MarshallerUtils.KEY_STAFF

        and:
        respondWithClosures.latestArgs[0].call() == 1
        respondWithClosures.latestArgs[1].call() == [s1]

        cleanup:
        isAllowed?.restore()
        findEveryForSharingId?.restore()
        respondWithClosures?.restore()
    }

    void "test listing overall"() {
        given:
        String tzId = TestUtils.randString()
        StaffStatus stat1 = StaffStatus.values()[0]
        Long orgId = TestUtils.randIntegerUpTo(88)
        Long tId = TestUtils.randIntegerUpTo(88)

        MockedMethod listForOrg = MockedMethod.create(controller, "listForOrg")
        MockedMethod listForTeam = MockedMethod.create(controller, "listForTeam")
        MockedMethod listForShareStaff = MockedMethod.create(controller, "listForShareStaff")

        when:
        params.timezone = tzId
        controller.index()

        then:
        listForShareStaff.latestArgs == [TypeMap.create(params)]
        RequestUtils.tryGet(RequestUtils.TIMEZONE).payload == tzId

        when:
        params.clear()
        response.reset()

        params.teamId = tId
        params."status[]" = [stat1]
        controller.index()

        then:
        listForTeam.latestArgs == [[stat1], TypeMap.create(params)]
        RequestUtils.tryGet(RequestUtils.TIMEZONE).payload == null

        when:
        params.clear()
        response.reset()

        params.organizationId = orgId
        controller.index()

        then:
        listForOrg.latestArgs == [StaffStatus.ACTIVE_STATUSES, TypeMap.create(params)]
        RequestUtils.tryGet(RequestUtils.TIMEZONE).payload == null

        cleanup:
        listForOrg?.restore()
        listForTeam?.restore()
        listForShareStaff?.restore()
    }
}
