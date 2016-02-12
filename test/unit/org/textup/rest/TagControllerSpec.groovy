package org.textup.rest

import grails.plugin.springsecurity.SpringSecurityService
import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestFor
import grails.test.mixin.TestMixin
import grails.validation.ValidationErrors
import org.springframework.context.MessageSource
import org.textup.util.CustomSpec
import spock.lang.Shared
import spock.lang.Specification
import org.textup.*
import static javax.servlet.http.HttpServletResponse.*

@TestFor(TagController)
@Domain([Contact, Phone, ContactTag, ContactNumber, Record, RecordItem, RecordText,
    RecordCall, RecordItemReceipt, SharedContact, Staff, Team, Organization,
    Schedule, Location, WeeklySchedule, PhoneOwnership, Role, StaffRole])
@TestMixin(HibernateTestMixin)
class TagControllerSpec extends CustomSpec {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }
    def setup() {
        super.setupData()
    }
    def cleanup() {
        super.cleanupData()
    }

    // List
    // ----

    protected void mockForList() {
        controller.authService = [
            getLoggedInAndActive:{ Staff.findByUsername(loggedInUsername) },
            hasPermissionsForTeam:{ Long id -> true }
        ] as AuthService
    }

    void "test list with no ids"() {
        when:
        mockForList()
        request.method = "GET"
        controller.index()
        Staff loggedIn = Staff.findByUsername(loggedInUsername)
        List<Long> ids = Helpers.allToLong(loggedIn.phone.tags*.id)

        then:
        response.status == SC_OK
        response.json.size() == ids.size()
        response.json*.id.every { ids.contains(it as Long) }
    }

    void "test list with team id"() {
        when:
        mockForList()
        params.teamId = t1.id
        request.method = "GET"
        controller.index()
        List<Long> ids = Helpers.allToLong(t1.phone.tags*.id)

        then:
        response.status == SC_OK
        response.json.size() == ids.size()
        response.json*.id.every { ids.contains(it as Long) }
    }

    // Show
    // ----

    void "test show nonexistent tag"() {
        when:
        request.method = "GET"
        params.id = -88L
        controller.show()

        then:
        response.status == SC_NOT_FOUND
    }

    void "test show forbidden tag"() {
        given:
        controller.authService = [
            hasPermissionsForTag:{ Long id -> false },
        ] as AuthService

        when:
        request.method = "GET"
        params.id = tag1.id
        controller.show()

        then:
        response.status == SC_FORBIDDEN
    }

    void "test show tag"() {
        given:
        controller.authService = [
            hasPermissionsForTag:{ Long id -> true },
        ] as AuthService

        when:
        request.method = "GET"
        params.id = tag1.id
        controller.show()

        then:
        response.status == SC_OK
        response.json.id == tag1.id
    }

    // Save
    // ----

    protected void mockForSave() {
        controller.tagService = [createForStaff:{ Map body ->
            new Result(payload:tag1)
        }, createForTeam:{ Long tId, Map body ->
            new Result(payload:teTag1)
        }] as TagService
        controller.authService = [
            exists:{ Class clazz, Long id -> true },
            getIsActive:{ true },
            hasPermissionsForTeam:{ Long id -> true }
        ] as AuthService
    }

    void "test save with no id"() {
        when:
        mockForSave()
        request.json = "{'tag':{}}"
        request.method = "POST"
        controller.save()

        then:
        response.status == SC_CREATED
        response.json.id == tag1.id
    }

    void "test save with team id"() {
        when:
        mockForSave()
        request.json = "{'tag':{}}"
        params.teamId = t1.id
        request.method = "POST"
        controller.save()

        then:
        response.status == SC_CREATED
        response.json.id == teTag1.id
    }

    // Update
    // ------

    void "test update nonexistent tag"() {
        given:
        controller.authService = [
            exists:{ Class clazz, Long id -> false }
        ] as AuthService

        when:
        request.json = "{'tag':{}}"
        params.id = -88L
        request.method = "PUT"
        controller.update()

        then:
        response.status == SC_NOT_FOUND
    }

    void "test update forbidden tag"() {
        given:
        controller.authService = [
            exists:{ Class clazz, Long id -> true },
            hasPermissionsForTag:{ Long id -> false }
        ] as AuthService

        when:
        request.json = "{'tag':{}}"
        params.id = tag1.id
        request.method = "PUT"
        controller.update()

        then:
        response.status == SC_FORBIDDEN
    }

    void "test update tag"() {
        given:
        controller.tagService = [update:{ Long cId, Map body ->
            new Result(payload:tag1)
        }] as TagService
        controller.authService = [
            exists:{ Class clazz, Long id -> true },
            hasPermissionsForTag:{ Long id -> true }
        ] as AuthService

        when:
        request.json = "{'tag':{}}"
        params.id = tag1.id
        request.method = "PUT"
        controller.update()

        then:
        response.status == SC_OK
        response.json.id == tag1.id
    }

    // Delete
    // ------

    void "test delete nonexistent tag"() {
        given:
        controller.authService = [
            exists:{ Class clazz, Long id -> false }
        ] as AuthService

        when:
        params.id = -88L
        request.method = "DELETE"
        controller.delete()

        then:
        response.status == SC_NOT_FOUND
    }

    void "test delete forbidden tag"() {
        given:
        controller.authService = [
            exists:{ Class clazz, Long id -> true },
            hasPermissionsForTag:{ Long id -> false }
        ] as AuthService

        when:
        params.id = tag1.id
        request.method = "DELETE"
        controller.delete()

        then:
        response.status == SC_FORBIDDEN
    }

    void "test delete tag"() {
        given:
        controller.tagService = [delete:{ Long cId ->
            new Result(payload:tag1)
        }] as TagService
        controller.authService = [
            exists:{ Class clazz, Long id -> true },
            hasPermissionsForTag:{ Long id -> true }
        ] as AuthService

        when:
        params.id = tag1.id
        request.method = "DELETE"
        controller.delete()

        then:
        response.status == SC_NO_CONTENT
    }
}
