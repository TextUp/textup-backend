package org.textup.rest

import grails.plugin.springsecurity.SpringSecurityService
import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestFor
import grails.test.mixin.TestMixin
import grails.validation.ValidationErrors
import org.springframework.context.MessageSource
import org.textup.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import spock.lang.Shared
import spock.lang.Specification
import static javax.servlet.http.HttpServletResponse.*

@TestFor(StaffController)
@Domain([CustomAccountDetails, Contact, Phone, ContactTag, ContactNumber, Record, RecordItem, RecordText,
    RecordCall, RecordItemReceipt, SharedContact, Staff, Team, Organization,
    Schedule, Location, WeeklySchedule, PhoneOwnership, Role, StaffRole, NotificationPolicy,
    MediaInfo, MediaElement, MediaElementVersion])
@TestMixin(HibernateTestMixin)
class StaffControllerSpec extends CustomSpec {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }
    def setup() {
        super.setupData()
        // enables resolving of resource names from class
        controller.grailsApplication.flatConfig = config.flatten()
    }
    def cleanup() {
        super.cleanupData()
    }

    // List
    // ----

    protected void mockForList() {
        controller.authService = [
            isAdminAt:{ Long id -> true },
            isAdminAtSameOrgAs:{ Long id -> true },
            isLoggedInAndActive:{ Long id -> true },
            hasPermissionsForTeam:{ Long id -> true },
            getIsActive: { -> true },
            getLoggedInAndActive: { -> s1 }
        ] as AuthService
    }

    void "test listing with no ids"() {
        when:
        request.method = "GET"
        controller.index()

        then:
        response.status == SC_BAD_REQUEST
    }

    void "test listing with both ids"() {
        when:
        request.method = "GET"
        params.organizationId = org.id
        params.teamId = t1.id
        params.canShareStaffId = s1.id
        controller.index()

        then:
        response.status == SC_BAD_REQUEST
    }

    void "test listing with org id"() {
        when:
        mockForList()
        request.method = "GET"
        params.organizationId = org.id
        controller.index()
        List<Long> ids = TypeConversionUtils.allTo(Long, org.people*.id)

        then:
        response.status == SC_OK
        response.json.size() == ids.size()
        response.json*.id.every { ids.contains(it as Long) }
    }

    void "test list with team id"() {
        when:
        mockForList()
        request.method = "GET"
        params.teamId = t1.id
        controller.index()
        List<Long> ids = TypeConversionUtils.allTo(Long, t1.members*.id)

        then:
        response.status == SC_OK
        response.json.size() == ids.size()
        response.json*.id.every { ids.contains(it as Long) }
    }

    void "test list staff that can share with provided staff id"() {
        when:
        mockForList()
        request.method = "GET"
        params.canShareStaffId = s1.id
        controller.index()
        List<Long> ids = TypeConversionUtils.allTo(Long, s1.teams.members*.id.flatten())
        ids.remove(s1.id)

        then:
        response.status == SC_OK
        response.json.size() == ids.size()
        response.json*.id.every { ids.contains(it as Long) }
    }

    void "test list search"() {
        when:
        mockForList()
        request.method = "GET"
        params.search = "demo"
        controller.index()
        List<Long> ids = TypeConversionUtils.allTo(Long, org.getStaff(params.search)*.id)

        then:
        response.status == SC_OK
        response.json.staff.size() == ids.size()
        response.json.staff*.id.every { ids.contains(it as Long) }
    }

    // Show
    // ----

    void "test show nonexistent staff"() {
        when:
        request.method = "GET"
        params.id = -88L
        controller.show()

        then:
        response.status == SC_NOT_FOUND
    }

    void "test show a forbidden staff"() {
        given:
        controller.authService = [
            hasPermissionsForStaff:{ Long id -> false },
        ] as AuthService

        when:
        request.method = "GET"
        params.id = s1.id
        controller.show()

        then:
        response.status == SC_FORBIDDEN
    }

    void "test show a staff"() {
        given:
        controller.authService = [
            hasPermissionsForStaff:{ Long id -> true },
        ] as AuthService

        when:
        request.method = "GET"
        params.id = s1.id
        controller.show()

        then:
        response.status == SC_OK
        response.json.id == s1.id
    }

    // Save
    // ----

    void "test save"() {
        given:
        controller.staffService = [create:{ Map body, String timezone ->
            new Result(payload:s1, status:ResultStatus.CREATED)
        }, addRoleToStaff: { Long sId ->
            new Result(payload:s1, status:ResultStatus.CREATED)
        }] as StaffService

        when:
        request.json = "{'staff':{}}"
        request.method = "POST"
        controller.save()

        then:
        response.status == SC_CREATED
        response.json.id == s1.id
    }

    // Update
    // ------

    void "test update a nonexistent staff"() {
        given:
        controller.authService = [
            exists:{ Class clazz, Long id -> false }
        ] as AuthService

        when:
        request.json = "{'staff':{}}"
        params.id = -88L
        request.method = "PUT"
        controller.update()

        then:
        response.status == SC_NOT_FOUND
    }

    void "test update a forbidden staff"() {
        given:
        controller.authService = [
            exists:{ Class clazz, Long id -> true },
            isLoggedIn:{ Long id -> false },
            isAdminAtSameOrgAs:{ Long id -> false }
        ] as AuthService

        when:
        request.json = "{'staff':{}}"
        params.id = s1.id
        request.method = "PUT"
        controller.update()

        then:
        response.status == SC_FORBIDDEN
    }

    void "test update a staff"() {
        given:
        controller.staffService = [update:{ Long cId, Map body, String tz ->
            new Result(payload:s1, status:ResultStatus.OK)
        }] as StaffService
        controller.authService = [
            exists:{ Class clazz, Long id -> true },
            isLoggedIn:{ Long id -> true },
            isAdminAtSameOrgAs:{ Long id -> true }
        ] as AuthService

        when:
        request.json = "{'staff':{}}"
        params.id = s1.id
        request.method = "PUT"
        controller.update()

        then:
        response.status == SC_OK
        response.json.id == s1.id
    }

    // Delete
    // ------

    void "test delete"() {
        when:
        params.id = s1.id
        request.method = "DELETE"
        controller.delete()

        then:
        response.status == SC_METHOD_NOT_ALLOWED
    }
}
