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
    Schedule, Location, WeeklySchedule, PhoneOwnership, Role, StaffRole])
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

    void "test list with contact id and no dates"() {
        given:
        controller.authService = [
            hasPermissionsForContact:{ Long id -> true },
        ] as AuthService

        when:
        request.method = "GET"
        params.contactId = c1.id
        controller.index()
        List<Long> ids = Helpers.allToLong(c1.items*.id)

        then:
        response.status == SC_OK
        response.json.size() == ids.size()
        response.json*.id.every { ids.contains(it as Long) }
    }

    void "test list with for shared contact id and no dates"() {
        given:
        controller.authService = [
            hasPermissionsForContact:{ Long id -> false },
            getSharedContactIdForContact:{ Long cId -> sc1.id }
        ] as AuthService

        when:
        request.method = "GET"
        params.contactId = c2.id
        controller.index()
        List<Long> ids = Helpers.allToLong(sc1.items*.id)

        then:
        response.status == SC_OK
        response.json.size() == ids.size()
        response.json*.id.every { ids.contains(it as Long) }
    }

    void "test list with contact id for date since"() {
        given:
        controller.authService = [
            hasPermissionsForContact:{ Long id -> true },
        ] as AuthService

        when:
        DateTime since = DateTime.now().minusDays(1)
        request.method = "GET"
        params.contactId = c1.id
        params.since = since.toDate()
        controller.index()
        List<Long> ids = Helpers.allToLong(c1.getSince(since)*.id)

        then:
        response.status == SC_OK
        response.json.size() == ids.size()
        response.json*.id.every { ids.contains(it as Long) }
    }

    void "test list with contact id for date between"() {
        given:
        controller.authService = [
            hasPermissionsForContact:{ Long id -> true },
        ] as AuthService

        when:
        DateTime since = DateTime.now().minusDays(1)
        DateTime before = DateTime.now().minusHours(1)
        request.method = "GET"
        params.contactId = c1.id
        params.since = since.toDate()
        params.before = before.toDate()
        controller.index()
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

    void "test update"() {
        when:
        params.id = rText1.id
        request.method = "PUT"
        controller.update()

        then:
        response.status == SC_METHOD_NOT_ALLOWED
    }

    // Delete
    // ------

    void "test delete"() {
        when:
        params.id = rText1.id
        request.method = "DELETE"
        controller.delete()

        then:
        response.status == SC_METHOD_NOT_ALLOWED
    }
}
