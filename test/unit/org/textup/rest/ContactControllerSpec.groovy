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

@TestFor(ContactController)
@Domain([TagMembership, Contact, Phone, ContactTag, 
    ContactNumber, Record, RecordItem, RecordNote, RecordText, 
    RecordCall, RecordItemReceipt, PhoneNumber, SharedContact, 
    TeamMembership, StaffPhone, Staff, Team, Organization, 
    Schedule, Location, TeamPhone, WeeklySchedule, TeamContactTag])
@TestMixin(HibernateTestMixin)
class ContactControllerSpec extends CustomSpec {

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

    protected void mockForList() {
        controller.authService = [
            isLoggedIn:{ Long id -> true },
            belongsToSameTeamAs:{ Long id -> true },
            hasPermissionsForTag:{ Long id -> true }
        ]
    }

    void "test list with no ids"() {
        when: 
        request.method = "GET"
        controller.index()

        then: 
        response.status == SC_BAD_REQUEST
    }

    void "test list with more than 1 id"() {
        when: 
        request.method = "GET"
        params.staffId = s1.id
        params.teamId = t1.id
        controller.index()

        then: 
        response.status == SC_BAD_REQUEST
    }

    void "test list with staff id"() {
        when: 
        mockForList()
        request.method = "GET"
        params.staffId = s1.id
        controller.index()
        List<Long> ids = Helpers.allToLong(s1.phone.getContacts()*.id)

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
        List<Long> ids = Helpers.allToLong(t1.phone.getContacts()*.id)

        then: 
        response.status == SC_OK
        response.json.size() == ids.size() 
        response.json*.id.every { ids.contains(it as Long) }
    }

    void "test list with tag id"() {
        when:
        mockForList()
        request.method = "GET"
        params.tagId = tag1.id
        controller.index()
        List<Long> ids = Helpers.allToLong(tag1.getSubscribers()*.contact*.id)

        then:
        response.status == SC_OK
        response.json.size() == ids.size() 
        response.json*.id.every { ids.contains(it as Long) }
    }

    //////////
    // Show //
    //////////

    void "test show nonexistent contact"() {
        when:
        request.method = "GET"
        params.id = -88L
        controller.show()

        then: 
        response.status == SC_NOT_FOUND
    }

    void "test show a contact"() {
        given:
        controller.authService = [
            hasPermissionsForContact:{ Long id -> true },
        ]

        when:
        request.method = "GET"
        params.id = c1.id
        controller.show()

        then:
        response.status == SC_OK
        response.json.id == c1.id 
    }

    void "test show a shared contact"() {
        given:
        controller.authService = [
            hasPermissionsForContact:{ Long id -> false },
            getSharedContactForContact:{ Long id -> sc1.id }
        ]
        
        when:
        request.method = "GET"
        params.id = c2.id 
        controller.show()

        then:
        response.status == SC_OK
        //note that this returned is the default val and is 
        //different in our custom marshalled SharedContact
        response.json.id == sc1.id
    }

    //////////
    // Save //
    //////////

    protected void mockForSave() {
        controller.contactService = [create:{ Class clazz, Long id, Map body ->
            new Result(payload:c1)
        }]
        controller.authService = [
            exists:{ Class clazz, Long id -> true },
            isLoggedIn:{ Long id -> true },
            belongsToSameTeamAs:{ Long id -> true }
        ]
    }

    void "test save with no ids"() {
        when: 
        request.json = "{'contact':{}}"
        request.method = "POST"
        controller.save()

        then:
        response.status == SC_BAD_REQUEST
    }

    void "test save with both ids"() {
        when: 
        request.json = "{'contact':{}}"
        params.staffId = s1.id
        params.teamId = t1.id
        request.method = "POST"
        controller.save()

        then:
        response.status == SC_BAD_REQUEST
    }

    void "test save for staff"() {
        when: 
        mockForSave()
        request.json = "{'contact':{}}"
        params.staffId = s1.id
        request.method = "POST"
        controller.save()

        then:
        response.status == SC_CREATED
        response.json.id == c1.id
    }

    void "test save for team"() {
        when: 
        mockForSave()
        request.json = "{'contact':{}}"
        params.teamId = t1.id
        request.method = "POST"
        controller.save()

        then:
        response.status == SC_CREATED
        response.json.id == c1.id
    }

    ////////////
    // Update //
    ////////////

    void "test update a nonexistent contact"() {
        given: 
        controller.authService = [
            exists:{ Class clazz, Long id -> false }
        ]

        when:
        request.json = "{'contact':{}}"
        params.id = -88L
        request.method = "PUT"
        controller.update()

        then:
        response.status == SC_NOT_FOUND
    }

    void "test update a forbidden contact"() {
        given: 
        controller.authService = [
            exists:{ Class clazz, Long id -> true },
            hasPermissionsForContact:{ Long id -> false }
        ]

        when:
        request.json = "{'contact':{}}"
        params.id = c1.id
        request.method = "PUT"
        controller.update()

        then:
        response.status == SC_FORBIDDEN
    }

    void "test update a contact"() {
        given: 
        controller.contactService = [update:{ Long cId, Map body ->
            new Result(payload:c1)
        }]
        controller.authService = [
            exists:{ Class clazz, Long id -> true },
            hasPermissionsForContact:{ Long id -> true }
        ]

        when:
        request.json = "{'contact':{}}"
        params.id = c1.id
        request.method = "PUT"
        controller.update()

        then:
        response.status == SC_OK
        response.json.id == c1.id
    }

    ////////////
    // Delete //
    ////////////

    void "test delete a nonexistent contact"() {
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

    void "test delete a forbidden contact"() {
        given: 
        controller.authService = [
            exists:{ Class clazz, Long id -> true },
            hasPermissionsForContact:{ Long id -> false }
        ]

        when:
        params.id = c1.id
        request.method = "DELETE"
        controller.delete()

        then:
        response.status == SC_FORBIDDEN
    }

    void "test delete a contact"() {
        given: 
        controller.contactService = [delete:{ Long cId ->
            new Result(payload:c1)
        }]
        controller.authService = [
            exists:{ Class clazz, Long id -> true },
            hasPermissionsForContact:{ Long id -> true }
        ]

        when:
        params.id = c1.id
        request.method = "DELETE"
        controller.delete()

        then:
        response.status == SC_NO_CONTENT
    }
}
