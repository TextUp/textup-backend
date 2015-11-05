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

@TestFor(TeamController)
@Domain([TagMembership, Contact, Phone, ContactTag, 
    ContactNumber, Record, RecordItem, RecordNote, RecordText, 
    RecordCall, RecordItemReceipt, PhoneNumber, SharedContact, 
    TeamMembership, StaffPhone, Staff, Team, Organization, 
    Schedule, Location, TeamPhone, WeeklySchedule, TeamContactTag])
@TestMixin(HibernateTestMixin)
class TeamControllerSpec extends CustomSpec {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }
    def setup() {
        super.setupData()
    }
    def cleanup() { 
        super.cleanupData()
    }

    //////////
    // List //
    //////////

    protected mockForList() {
        controller.authService = [
            isLoggedIn:{ Long id -> true },
            isAdminAt:{ Long id -> true }
        ]
    }

    void "test list with no ids"() {
        when: 
        request.method = "GET"
        controller.index()

        then: 
        response.status == SC_BAD_REQUEST
    }

    void "test list with both ids"() {
        when: 
        request.method = "GET"
        params.organizationId = org.id
        params.staffId = s1.id
        controller.index()

        then: 
        response.status == SC_BAD_REQUEST
    }

    void "test list with org id"() {
        when: 
        mockForList()
        request.method = "GET"
        params.organizationId = org.id
        controller.index()
        List<Long> ids = Helpers.allToLong(org.teams*.id)

        then: 
        response.status == SC_OK
        response.json.size() == ids.size() 
        response.json*.id.every { ids.contains(it as Long) }
    }

    void "test list with staff id"() {
        when: 
        mockForList()
        request.method = "GET"
        params.staffId = s1.id
        controller.index()
        List<Long> ids = Helpers.allToLong(s1.teams*.id)

        then: 
        response.status == SC_OK
        response.json.size() == ids.size() 
        response.json*.id.every { ids.contains(it as Long) }
    }

    //////////
    // Show //
    //////////

    void "test show nonexistent team"() {
        when:
        request.method = "GET"
        params.id = -88L
        controller.show()

        then: 
        response.status == SC_NOT_FOUND
    }

    void "test show forbidden team"() {
        given:
        controller.authService = [
            hasPermissionsForTeam:{ Long id -> false }
        ]

        when:
        request.method = "GET"
        params.id = t1.id
        controller.show()

        then:
        response.status == SC_FORBIDDEN
    }

    void "test show team"() {
        given:
        controller.authService = [
            hasPermissionsForTeam:{ Long id -> true },
        ]
        
        when:
        request.method = "GET"
        params.id = t1.id
        controller.show()

        then:
        response.status == SC_OK
        response.json.id == t1.id 
    }

    //////////
    // Save //
    //////////

    void "test save nonexistent team"() {
        given: 
        controller.authService = [
            exists:{ Class clazz, Long id -> false }
        ]

        when:
        request.json = "{'team':{}}"
        params.id = -88L
        request.method = "POST"
        controller.save()

        then:
        response.status == SC_NOT_FOUND
    }

    void "test save forbidden team"() {
        given: 
        controller.authService = [
            exists:{ Class clazz, Long id -> true },
            isAdminAt:{ Long id -> false }
        ]

        when:
        request.json = "{'team':{}}"
        params.id = t1.id
        request.method = "POST"
        controller.save()

        then:
        response.status == SC_FORBIDDEN
    }

    void "test save team"() {
        given: 
        controller.teamService = [save:{ Map body ->
            new Result(payload:t1)
        }]
        controller.authService = [
            exists:{ Class clazz, Long id -> true },
            isAdminAt:{ Long id -> true }
        ]

        when:
        request.json = "{'team':{}}"
        params.id = t1.id
        request.method = "POST"
        controller.save()

        then:
        response.status == SC_CREATED
        response.json.id == t1.id

    }

    ////////////
    // Update //
    ////////////

    void "test update nonexistent team"() {
        given: 
        controller.authService = [
            exists:{ Class clazz, Long id -> false }
        ]

        when:
        request.json = "{'team':{}}"
        params.id = -88L
        request.method = "PUT"
        controller.update()

        then:
        response.status == SC_NOT_FOUND
    }

    void "test update forbidden team"() {
        given: 
        controller.authService = [
            exists:{ Class clazz, Long id -> true },
            isAdminForTeam:{ Long id -> false }
        ]

        when:
        request.json = "{'team':{}}"
        params.id = t1.id
        request.method = "PUT"
        controller.update()

        then:
        response.status == SC_FORBIDDEN
    }

    void "test update team"() {
        given: 
        controller.teamService = [update:{ Long cId, Map body ->
            new Result(payload:t1)
        }]
        controller.authService = [
            exists:{ Class clazz, Long id -> true },
            isAdminForTeam:{ Long id -> true }
        ]

        when:
        request.json = "{'team':{}}"
        params.id = t1.id
        request.method = "PUT"
        controller.update()

        then:
        response.status == SC_OK
        response.json.id == t1.id
    }

    ////////////
    // Delete //
    ////////////

    void "test delete nonexistent team"() {
        given: 
        controller.authService = [
            exists:{ Class clazz, Long id -> false }
        ]

        when:
        params.id = -88L
        request.method = "DELETE"
        controller.delete()

        then:
        response.status == SC_NOT_FOUND
    }

    void "test delete forbidden team"() {
        given: 
        controller.authService = [
            exists:{ Class clazz, Long id -> true },
            isAdminForTeam:{ Long id -> false }
        ]

        when:
        params.id = t1.id
        request.method = "DELETE"
        controller.delete()

        then:
        response.status == SC_FORBIDDEN
    }

    void "test delete team"() {
        given: 
        controller.teamService = [delete:{ Long cId ->
            new Result(payload:t1)
        }]
        controller.authService = [
            exists:{ Class clazz, Long id -> true },
            isAdminForTeam:{ Long id -> true }
        ]

        when:
        params.id = t1.id
        request.method = "DELETE"
        controller.delete()

        then:
        response.status == SC_NO_CONTENT
    }
}
