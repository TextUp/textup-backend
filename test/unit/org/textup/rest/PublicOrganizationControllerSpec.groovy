package org.textup.rest

import org.textup.*
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

@TestFor(PublicOrganizationController)
@Domain([Contact, Phone, ContactTag, ContactNumber, Record, RecordItem, RecordText,
    RecordCall, RecordItemReceipt, SharedContact, Staff, Team, Organization,
    Schedule, Location, WeeklySchedule, PhoneOwnership])
@TestMixin(HibernateTestMixin)
class PublicOrganizationControllerSpec extends CustomSpec {

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

    void "test list"() {
        when:
        request.method = "GET"
        controller.index()

        then:
        response.status == SC_OK
        response.json.size() == Organization.count()
    }

    // Show
    // ----

    void "test show nonexistent"() {
        when:
        request.method = "GET"
        params.id = -88L
        controller.show()

        then:
        response.status == SC_NOT_FOUND
    }

    void "test show"() {
        when:
        request.method = "GET"
        params.id = org.id
        controller.show()

        then:
        response.status == SC_OK
        response.json.id == org.id
    }

    // Save
    // ----

    void "test save"() {
        when:
        request.json = "{'organization':{}}"
        request.method = "POST"
        controller.save()

        then:
        response.status == SC_METHOD_NOT_ALLOWED
    }

    // Update
    // ------

    void "test update"() {
        when:
        request.json = "{'organization':{}}"
        request.method = "PUT"
        controller.update()

        then:
        response.status == SC_METHOD_NOT_ALLOWED
    }

    // Delete
    // ------

    void "test delete"() {
        when:
        params.id = org.id
        request.method = "DELETE"
        controller.delete()

        then:
        response.status == SC_METHOD_NOT_ALLOWED
    }
}
