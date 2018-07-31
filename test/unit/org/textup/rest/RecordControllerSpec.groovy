package org.textup.rest

import grails.plugin.jodatime.converters.JodaConverters
import grails.plugin.springsecurity.SpringSecurityService
import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestFor
import grails.test.mixin.TestMixin
import grails.validation.ValidationErrors
import org.joda.time.DateTime
import org.springframework.context.MessageSource
import org.textup.*
import org.textup.type.SharePermission
import org.textup.util.CustomSpec
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
        super.setupData()
        JodaConverters.registerJsonAndXmlMarshallers()
        controller.recordService = [parseTypes:{ Collection<?> rawTypes -> [] }] as RecordService
        controller.resultFactory = getResultFactory()
    }
    def cleanup() {
        super.cleanupData()
    }

    // List
    // ----

    void "test list with no ids"() {
        when:
        request.method = "GET"
        controller.index()

        then:
        response.status == SC_BAD_REQUEST
    }
    void "test list with both contact and tag id"() {
        when:
        request.method = "GET"
        params.contactId = 2L
        params.tagId = 8L
        controller.index()

        then:
        response.status == SC_BAD_REQUEST
    }
    void "test list with nonexistent contact id"() {
        when:
        request.method = "GET"
        params.contactId = -888L
        controller.index()

        then:
        response.status == SC_NOT_FOUND
    }
    void "test list with forbidden contact id that is shared with me"() {
        given:
        controller.authService = [
            hasPermissionsForContact:{ Long id -> false },
            getSharedContactIdForContact:{ Long cId -> null }
        ] as AuthService

        when:
        request.method = "GET"
        params.contactId = 2L
        controller.index()

        then:
        response.status == SC_FORBIDDEN
    }
    void "test list with forbidden tag id"() {
        controller.authService = [
            hasPermissionsForTag:{ Long id -> false }
        ] as AuthService

        when:
        request.method = "GET"
        params.tagId = tag1.id
        controller.index()

        then:
        response.status == SC_FORBIDDEN
    }

    void "test list with no dates"() {
        when:
        request.method = "GET"
        controller.listForRecord(c1.record, params)
        List<Long> ids = Helpers.allTo(Long, c1.record.items*.id)

        then:
        response.status == SC_OK
        response.json.size() == ids.size()
        response.json*.id.every { ids.contains(it as Long) }
    }

    void "test list with no dates for shared contact with view-only permissions"() {
        given:
        sc1.permission = SharePermission.VIEW
        sc1.save(flush: true, failOnError: true)
        sc1.resultFactory = getResultFactory()

        controller.authService = [
            hasPermissionsForContact:{ Long id -> false },
            getSharedContactIdForContact:{ Long cId -> sc1.id }
        ] as AuthService
        List<Long> ids = Helpers.allTo(Long, sc1.tryGetReadOnlyRecord().payload.items*.id)

        when:
        request.method = "GET"
        params.contactId = 2L
        controller.index()

        then:
        response.status == SC_OK
        response.json.size() == ids.size()
        response.json*.id.every { ids.contains(it as Long) }
    }

    void "test list with date since"() {
        when:
        DateTime since = DateTime.now().minusDays(1)
        request.method = "GET"
        params.since = since.toDate()
        controller.listForRecord(c1.record, params)
        List<Long> ids = Helpers.allTo(Long, c1.record.getSince(since)*.id)

        then:
        response.status == SC_OK
        response.json.size() == ids.size()
        response.json*.id.every { ids.contains(it as Long) }
    }

    void "test list with date between"() {
        when:
        DateTime since = DateTime.now().minusDays(1)
        DateTime before = DateTime.now().minusHours(1)
        request.method = "GET"
        params.since = since.toDate()
        params.before = before.toDate()
        controller.listForRecord(c1.record, params)
        List<Long> ids = Helpers.allTo(Long, c1.record.getBetween(since, before)*.id)

        then:
        response.status == SC_OK
        response.json.records.size() == ids.size()
        //this is a little different because this went to respondHandleEmpty
        //which explicitly puts in the root "records." All the other tests above
        //rely on the renderer to put in the "records" root which is not loaded
        //in these unit tests.
        response.json.records*.id.every { ids.contains(it as Long) }
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
        given:
        addToMessageSource(["recordController.create.tooManyForCall", "recordController.create.tooManyForNote"])

        expect: "no validation to happen for recipients for texts"
        controller.validateCreateBody(RecordText, [:]) == true

        and: "only one recipient allowed for calls"
        controller.validateCreateBody(RecordCall, [:]) == false
        controller.validateCreateBody(RecordCall, [callContact: 1]) == true
        controller.validateCreateBody(RecordCall, [callSharedContact: 1]) == true
        controller.validateCreateBody(RecordCall, [callContact: 1, callSharedContact: 1]) == false

        and: "only one recipient allowed for notes"
        controller.validateCreateBody(RecordNote, [:]) == false
        controller.validateCreateBody(RecordNote, [forContact: 1]) == true
        controller.validateCreateBody(RecordNote, [forSharedContact: 1]) == true
        controller.validateCreateBody(RecordNote, [forTag: 1]) == true
        controller.validateCreateBody(RecordNote, [forContact: 1, forSharedContact: 1]) == false
        controller.validateCreateBody(RecordNote, [forContact: 1, forTag: 1]) == false
        controller.validateCreateBody(RecordNote, [forSharedContact: 1, forTag: 1]) == false
        controller.validateCreateBody(RecordNote, [forContact: 1, forSharedContact: 1, forTag: 1]) == false
    }

    protected void mockForSave(Class requestClass, boolean doesExist, boolean hasTeamPermissions, Staff authUser) {
        controller.recordService = [
            determineClass: { Map body ->
                new Result(status: ResultStatus.OK, payload:requestClass)
            },
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
