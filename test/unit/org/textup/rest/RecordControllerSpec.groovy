package org.textup.rest

import grails.plugin.springsecurity.SpringSecurityService
import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestFor
import grails.test.mixin.TestMixin
import grails.validation.ValidationErrors
import org.joda.time.DateTime
import org.springframework.context.MessageSource
import org.textup.*
import org.textup.util.CustomSpec
import spock.lang.Shared
import spock.lang.Specification
import static javax.servlet.http.HttpServletResponse.*
import grails.plugin.jodatime.converters.JodaConverters
import org.textup.types.ResultType

@TestFor(RecordController)
@Domain([Contact, Phone, ContactTag, ContactNumber, Record, RecordItem, RecordText,
    RecordCall, RecordItemReceipt, SharedContact, Staff, Team, Organization,
    Schedule, Location, WeeklySchedule, PhoneOwnership, Role, StaffRole,
    RecordNote, RecordNoteRevision])
@TestMixin(HibernateTestMixin)
class RecordControllerSpec extends CustomSpec {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }
    def setup() {
        super.setupData()
        JodaConverters.registerJsonAndXmlMarshallers()
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
        controller.listForClass(c1, Contact, params)
        List<Long> ids = Helpers.allToLong(c1.items*.id)

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
        controller.listForClass(c1, Contact, params)
        List<Long> ids = Helpers.allToLong(c1.getSince(since)*.id)

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
        controller.listForClass(c1, Contact, params)
        List<Long> ids = Helpers.allToLong(c1.getBetween(since, before)*.id)

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

    protected void mockForSave() {
        controller.recordService = [createForStaff:{ Map body ->
            ResultList resList = new ResultList()
            resList << new Result(type:ResultType.SUCCESS, success:true, payload:rText1)
            resList << new Result(type:ResultType.SUCCESS, success:true, payload:rText2)
            resList
        }, createForTeam:{ Long tId, Map body ->
            ResultList resList = new ResultList()
            resList << new Result(type:ResultType.SUCCESS, success:true, payload:teTag1)
            resList << new Result(type:ResultType.SUCCESS, success:true, payload:teTag2)
            resList
        }] as RecordService
    }

    void "test save for no ids"() {
        when:
        mockForSave()
        request.json = "{'record':{}}"
        request.method = "POST"
        controller.save()

        then:
        response.status == SC_CREATED
        response.json.size() == 2
        response.json*.id.every { (it as Long) in [rText1, rText2]*.id }
    }

    void "test save for team id"() {
        when:
        mockForSave()
        request.json = "{'record':{}}"
        params.teamId = t1.id
        request.method = "POST"
        controller.save()

        then:
        response.status == SC_CREATED
        response.json.size() == 2
        response.json*.id.every { (it as Long) in [teTag1, teTag2]*.id }
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
            new Result(success:true)
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
