package org.textup.rest

import grails.plugin.jodatime.converters.JodaConverters
import grails.plugin.springsecurity.SpringSecurityService
import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestFor
import grails.test.mixin.TestMixin
import grails.test.runtime.DirtiesRuntime
import grails.validation.ValidationErrors
import org.codehaus.groovy.grails.web.util.TypeConvertingMap
import org.joda.time.DateTime
import org.springframework.context.MessageSource
import org.textup.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*
import spock.lang.Shared
import spock.lang.Specification
import static javax.servlet.http.HttpServletResponse.*

@TestFor(RecordController)
@Domain([Contact, Phone, ContactTag, ContactNumber, Record, RecordItem, RecordText,
    RecordCall, RecordItemReceipt, SharedContact, Staff, Team, Organization,
    Schedule, Location, WeeklySchedule, PhoneOwnership, Role, StaffRole,
    RecordNote, RecordNoteRevision, NotificationPolicy,
    MediaInfo, MediaElement, MediaElementVersion])
@TestMixin(HibernateTestMixin)
class RecordControllerSpec extends CustomSpec {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        setupData()
        JodaConverters.registerJsonAndXmlMarshallers()
        controller.recordService = [parseTypes:{ Collection<?> rawTypes -> [] }] as RecordService
        controller.resultFactory = TestUtils.getResultFactory(grailsApplication)
    }

    def cleanup() {
        cleanupData()
    }

    // List
    // ----

    @DirtiesRuntime
    void "test listing error conditions"() {
        given:
        controller.authService = Mock(AuthService)
        String errorMsg = TestUtils.randString()
        MockedMethod buildRecordItemRequest = TestUtils.mock(RecordUtils, "buildRecordItemRequest") {
            Result.createError([errorMsg], ResultStatus.BAD_REQUEST)
        }
        Long teamId = TestUtils.randIntegerUpTo(88)

        when: "validation error when building record item request"
        controller.index()

        then:
        1 * controller.authService.loggedInAndActive
        buildRecordItemRequest.callCount == 1
        response.status == ResultStatus.BAD_REQUEST.intStatus

        when: "passed in nonexistent team id"
        params.clear()
        response.reset()

        params.teamId = teamId
        controller.index()

        then:
        1 * controller.authService.loggedInAndActive
        1 * controller.authService.exists(Team, teamId) >> false
        buildRecordItemRequest.callCount == 1
        response.status == ResultStatus.NOT_FOUND.intStatus

        when: "forbidden to access team's phone"
        params.clear()
        response.reset()

        params.teamId = teamId
        controller.index()

        then:
        1 * controller.authService.loggedInAndActive
        1 * controller.authService.exists(Team, teamId) >> true
        1 * controller.authService.hasPermissionsForTeam(teamId) >> false
        buildRecordItemRequest.callCount == 1
        response.status == ResultStatus.FORBIDDEN.intStatus
    }

    @DirtiesRuntime
    void "test passing pagination options and timezone when listing items"() {
        given:
        controller.authService = Stub(AuthService)
        RecordItemRequest stubItemRequest = Stub() {
            countRecordItems() >> 100
            getRecordItems(*_) >> []
        }
        MockedMethod buildRecordItemRequest = TestUtils.mock(RecordUtils, "buildRecordItemRequest") {
            Result.createSuccess(stubItemRequest, ResultStatus.OK)
        }
        String tzId = TestUtils.randString()

        when:
        params.timezone = tzId
        controller.index()

        then:
        request[Constants.REQUEST_PAGINATION_OPTIONS] == params
        request[Constants.REQUEST_TIMEZONE] == tzId
    }

    @DirtiesRuntime
    void "test listing with json response"() {
        given:
        controller.authService = Mock(AuthService)
        Phone mockPhone = Mock()
        Staff staffStub = Stub { getPhone() >> mockPhone }
        RecordItemRequest mockItemRequest = Mock()
        MockedMethod buildRecordItemRequest = TestUtils.mock(RecordUtils, "buildRecordItemRequest") {
            Result.createSuccess(mockItemRequest, ResultStatus.OK)
        }

        when:
        controller.index()

        then:
        (1.._) * controller.authService.loggedInAndActive >> staffStub
        1 * mockItemRequest.countRecordItems() >> 100
        1 * mockItemRequest.getRecordItems(*_) >> []
        buildRecordItemRequest.callCount == 1
        buildRecordItemRequest.callArguments[0][0] == mockPhone
        buildRecordItemRequest.callArguments[0][1] == params
        buildRecordItemRequest.callArguments[0][2] == false
        response.status == ResultStatus.OK.intStatus
        response.getHeaderValue("Content-Type") == "application/json;charset=UTF-8"
        response.json.records.size() == 0 // getRecordItems returns an empty list
        response.json.meta.total == 100
    }

    @DirtiesRuntime
    void "test listing with pdf response"() {
        given:
        controller.authService = Mock(AuthService)
        controller.pdfService = Mock(PdfService)
        Phone mockPhone = Mock()
        Staff staffStub = Stub { getPhone() >> mockPhone }
        RecordItemRequest mockItemRequest = Mock()
        MockedMethod buildRecordItemRequest = TestUtils.mock(RecordUtils, "buildRecordItemRequest") {
            Result.createSuccess(mockItemRequest, ResultStatus.OK)
        }

        when:
        params.format = "pdf"
        controller.index()

        then:
        (1.._) * controller.authService.loggedInAndActive >> staffStub
        1 * controller.pdfService.buildRecordItems(mockItemRequest) >>
            Result.createSuccess([] as byte[], ResultStatus.OK)
        buildRecordItemRequest.callCount == 1
        buildRecordItemRequest.callArguments[0][0] == mockPhone
        buildRecordItemRequest.callArguments[0][1] == params
        buildRecordItemRequest.callArguments[0][2] == false
        response.status == ResultStatus.OK.intStatus
        response.getHeaderValue("Content-Type") == "application/pdf;charset=utf-8"
        response.getHeaderValue("Content-Disposition").contains("attachment;filename=")
    }

    // Show
    // ----

    void "test show nonexistent item"() {
        when:
        request.method = "GET"
        params.id = -88L
        controller.show()

        then:
        response.status == SC_NOT_FOUND
    }

    void "test show forbidden item"() {
        given:
        controller.authService = [
            hasPermissionsForItem:{ Long id -> false },
        ] as AuthService

        when:
        request.method = "GET"
        params.id = rText1.id
        controller.show()

        then:
        response.status == SC_FORBIDDEN
    }

    void "test show item"() {
        given:
        controller.authService = [
            hasPermissionsForItem:{ Long id -> true },
        ] as AuthService

        when:
        request.method = "GET"
        params.id = rText1.id
        controller.show()

        then:
        response.status == SC_OK
        response.json.id == rText1.id
    }

    // Save
    // ----

    void "test validating recipients in request body for save"() {
        expect: "no validation to happen for recipients for texts"
        controller.validateCreateBody(RecordText, new TypeConvertingMap([:])) == true

        and: "only one recipient allowed for calls"
        controller.validateCreateBody(RecordCall, new TypeConvertingMap([:])) == false
        controller.validateCreateBody(RecordCall, new TypeConvertingMap([callContact: 1])) == true
        controller.validateCreateBody(RecordCall, new TypeConvertingMap([callSharedContact: 1])) == true
        controller.validateCreateBody(RecordCall, new TypeConvertingMap([callContact: 1, callSharedContact: 1])) == false

        and: "only one recipient allowed for notes"
        controller.validateCreateBody(RecordNote, new TypeConvertingMap([:])) == false
        controller.validateCreateBody(RecordNote, new TypeConvertingMap([forContact: 1])) == true
        controller.validateCreateBody(RecordNote, new TypeConvertingMap([forSharedContact: 1])) == true
        controller.validateCreateBody(RecordNote, new TypeConvertingMap([forTag: 1])) == true
        controller.validateCreateBody(RecordNote, new TypeConvertingMap([forContact: 1, forSharedContact: 1])) == false
        controller.validateCreateBody(RecordNote, new TypeConvertingMap([forContact: 1, forTag: 1])) == false
        controller.validateCreateBody(RecordNote, new TypeConvertingMap([forSharedContact: 1, forTag: 1])) == false
        controller.validateCreateBody(RecordNote, new TypeConvertingMap([forContact: 1, forSharedContact: 1, forTag: 1])) == false
    }

    protected void mockForSave(Class requestClass, boolean doesExist, boolean hasTeamPermissions, Staff authUser) {
        RecordUtils.metaClass."static".determineClass = { Map body ->
            new Result(status: ResultStatus.OK, payload:requestClass)
        }
        controller.recordService = [
            create:{ Long id, Map body ->
                ResultGroup resGroup = new ResultGroup()
                resGroup << new Result(status:ResultStatus.CREATED, payload:rText1)
                resGroup << new Result(status:ResultStatus.CREATED, payload:rText2)
                resGroup
            }
        ] as RecordService
        controller.authService = [
            exists: { Class clazz, Long id -> doesExist },
            hasPermissionsForTeam: { Long id -> hasTeamPermissions },
            getLoggedInAndActive: { -> authUser }
        ] as AuthService
    }

    void "test save for team"() {
        given:
        mockForSave(RecordCall, true, true, s1)

        when:
        request.json = "{'record':{'callContact': 1}}"
        params.teamId = t1.id
        request.method = "POST"
        controller.save()

        then: "see mock"
        response.status == SC_CREATED
        response.json.size() == 2
        response.json*.id.every { (it as Long) in [rText1, rText2]*.id }
    }

    void "test save for staff"() {
        given:
        mockForSave(RecordText, false, false, s1)

        when:
        request.json = "{'record':{}}"
        request.method = "POST"
        controller.save()

        then:
        response.status == SC_CREATED
        response.json.size() == 2
        response.json*.id.every { (it as Long) in [rText1, rText2]*.id }
    }

    // Update
    // ------

    void "test update a nonexistent note"() {
        given:
        controller.authService = [
            exists:{ Class clazz, Long id -> false }
        ] as AuthService

        when:
        params.id = "nonexistent"
        request.json = "{'record':{}}"
        request.method = "PUT"
        controller.update()

        then:
        response.status == SC_METHOD_NOT_ALLOWED
    }
    void "test update a note"() {
        given: "a persisted note"
        RecordNote note1 = new RecordNote(record:c1.record)
        note1.save(flush:true, failOnError:true)

        controller.recordService = [update:{ Long id, Map body ->
            new Result(success:true, payload:note1)
        }] as RecordService
        controller.authService = [
            exists:{ Class clazz, Long id -> true },
            hasPermissionsForItem:{ Long cId -> true }
        ] as AuthService

        when:
        params.id = note1.id
        request.json = "{'record':{}}"
        request.method = "PUT"
        controller.update()

        then:
        response.status == SC_OK
        response.json.id == note1.id
    }

    // Delete
    // ------

    void "test delete for a non-note"() {
        given:
        controller.authService = [
            exists:{ Class clazz, Long id -> false }
        ] as AuthService

        when:
        params.id = rText1.id
        request.method = "DELETE"
        controller.delete()

        then:
        response.status == SC_METHOD_NOT_ALLOWED
    }
    void "test delete for a note"() {
        given: "a persisted note"
        RecordNote note1 = new RecordNote(record:c1.record)
        note1.save(flush:true, failOnError:true)

        controller.recordService = [delete:{ Long id ->
            new Result(status:ResultStatus.NO_CONTENT)
        }] as RecordService
        controller.authService = [
            exists:{ Class clazz, Long id -> true },
            hasPermissionsForItem:{ Long cId -> true }
        ] as AuthService

        when:
        params.id = note1.id
        request.method = "DELETE"
        controller.delete()

        then:
        response.status == SC_NO_CONTENT
    }
}
