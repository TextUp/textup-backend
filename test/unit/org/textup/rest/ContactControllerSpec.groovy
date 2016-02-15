package org.textup.rest

import grails.plugin.jodatime.converters.JodaConverters
import grails.plugin.springsecurity.SpringSecurityService
import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestFor
import grails.test.mixin.TestMixin
import grails.validation.ValidationErrors
import org.springframework.context.MessageSource
import org.textup.*
import org.textup.util.CustomSpec
import spock.lang.Shared
import spock.lang.Specification
import static javax.servlet.http.HttpServletResponse.*
import org.textup.types.ContactStatus

@TestFor(ContactController)
@Domain([Contact, Phone, ContactTag, ContactNumber, Record, RecordItem, RecordText,
    RecordCall, RecordItemReceipt, SharedContact, Staff, Team, Organization,
    Schedule, Location, WeeklySchedule, PhoneOwnership, Role, StaffRole])
@TestMixin(HibernateTestMixin)
class ContactControllerSpec extends CustomSpec {

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

    protected void mockForList() {
        controller.authService = [
            getLoggedInAndActive: { Staff.findByUsername(loggedInUsername) },
            hasPermissionsForTeam:{ Long id -> true },
            hasPermissionsForTag:{ Long id -> true }
        ] as AuthService
    }

    void "test list with no ids"() {
        when:
        mockForList()
        request.method = "GET"
        controller.index()
        Staff loggedIn = Staff.findByUsername(loggedInUsername)
        List<Long> ids = Helpers.allToLong(loggedIn.phone.getContacts()*.id)

        then: "return contacts for currently logged in staff"
        response.status == SC_OK
        response.json.size() == ids.size()
        response.json*.id.every { ids.contains(it as Long) }
    }

    void "test list with more than 1 id"() {
        when:
        mockForList()
        request.method = "GET"
        params.tagId = tag1.id
        params.teamId = t1.id
        controller.index()

        then:
        response.status == SC_BAD_REQUEST
    }

    void "test list with tag id"() {
        when:
        mockForList()
        request.method = "GET"
        params.tagId = tag1.id
        controller.index()
        List<Long> ids = Helpers.allToLong(tag1.members*.id)

        then:
        response.status == SC_OK
        response.json.size() == ids.size()
        response.json*.id.every { ids.contains(it as Long) }
    }

    void "test list for logged in staff with invalid staff status"() {
        when:
        mockForList()
        request.method = "GET"
        params.staffId = s1.id
        params.shareStatus = "invalid"
        controller.index()
        Staff loggedIn = Staff.findByUsername(loggedInUsername)
        List<Long> ids = Helpers.allToLong(loggedIn.phone.getContacts()*.id)

        then: "return contacts for currently logged in staff"
        response.status == SC_OK
        response.json.size() == ids.size()
        response.json*.id.every { ids.contains(it as Long) }
    }

    void "test list for logged in staff with staff status of sharedByMe"() {
        when:
        mockForList()
        request.method = "GET"
        params.shareStatus = "sharedByMe"
        params["status[]"] = [ContactStatus.UNREAD, ContactStatus.ACTIVE]
        controller.index()
        Staff loggedIn = Staff.findByUsername(loggedInUsername)
        List<Long> ids = Helpers.allToLong(loggedIn.phone.getSharedByMe()*.id)

        then: "status[] param is overshadowed"
        response.status == SC_OK
        response.json.size() == ids.size()
        response.json*.id.every { ids.contains(it as Long) }
    }

    void "test list for logged in staff with staff status of sharedWithMe"() {
        when:
        mockForList()
        request.method = "GET"
        params.shareStatus = "sharedWithMe"
        controller.index()
        Staff loggedIn = Staff.findByUsername(loggedInUsername)
        List<Long> ids = Helpers.allToLong(loggedIn.phone.getSharedWithMe()*.id)

        then:
        response.status == SC_OK
        response.json.size() == ids.size()
        // real marshaller return contact id but the default here is shared contact id
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

    void "test list search"() {
        when:
        mockForList()
        request.method = "GET"
        params.teamId = t1.id
        params.search = "1222333"
        controller.index()
        List<Long> ids = Helpers.allToLong(t1.phone.getContacts(params.search)*.id)

        then:
        response.status == SC_OK
        response.json.contacts.size() == ids.size()
        response.json.contacts*.id.every { ids.contains(it as Long) }
    }

    // Show
    // ----

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
        ] as AuthService

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
            getSharedContactIdForContact:{ Long id -> sc1.id }
        ] as AuthService

        when:
        request.method = "GET"
        params.id = sc1.contact.id
        controller.show()

        then:
        response.status == SC_OK
        //note that this returned is the default val and is
        //different in our custom marshalled SharedContact
        response.json.id == sc1.id
    }

    // Save
    // ----

    protected void mockForSave() {
        controller.contactService = [createForStaff:{ Map body ->
            new Result(payload:c1)
        }, createForTeam:{ Long tId, Map body ->
            new Result(payload:c1)
        }] as ContactService
        controller.authService = [
            exists:{ Class clazz, Long id -> true },
            isLoggedIn:{ Long id -> true },
            hasPermissionsForTeam:{ Long id -> true }
        ] as AuthService
    }

    void "test save for no ids"() {
        when:
        mockForSave()
        request.json = "{'contact':{}}"
        params.staffId = s1.id
        request.method = "POST"
        controller.save()

        then: "implicit create for staff"
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

    // Update
    // ------

    void "test update a nonexistent contact"() {
        given:
        controller.authService = [
            exists:{ Class clazz, Long id -> false }
        ] as AuthService

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
            hasPermissionsForContact:{ Long id -> false },
            getSharedContactIdForContact: { Long id -> null }
        ] as AuthService

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
        }] as ContactService
        controller.authService = [
            exists:{ Class clazz, Long id -> true },
            hasPermissionsForContact:{ Long id -> true }
        ] as AuthService

        when:
        request.json = "{'contact':{}}"
        params.id = c1.id
        request.method = "PUT"
        controller.update()

        then:
        response.status == SC_OK
        response.json.id == c1.id
    }

    void "test update contact that is shard with this phone"() {
        given:
        controller.contactService = [update:{ Long cId, Map body ->
            new Result(payload:c1)
        }] as ContactService
        controller.authService = [
            exists:{ Class clazz, Long id -> true },
            hasPermissionsForContact:{ Long id -> false },
            getSharedContactIdForContact:{ Long id -> sc2.id }
        ] as AuthService

        when:
        request.json = "{'contact':{}}"
        params.id = c1.id
        request.method = "PUT"
        controller.update()

        then:
        response.status == SC_OK
        response.json.id == c1.id
    }

    // Delete
    // ------

    void "test delete a nonexistent contact"() {
        when:
        params.id = c1.id
        request.method = "DELETE"
        controller.delete()

        then:
        response.status == SC_METHOD_NOT_ALLOWED
    }
}
