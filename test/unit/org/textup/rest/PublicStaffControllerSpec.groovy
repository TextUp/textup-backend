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

@TestFor(PublicStaffController)
@Domain([Contact, Phone, ContactTag, ContactNumber, Record, RecordItem, RecordText,
    RecordCall, RecordItemReceipt, SharedContact, Staff, Team, Organization,
    Schedule, Location, WeeklySchedule, PhoneOwnership, Role, StaffRole, NotificationPolicy])
@TestMixin(HibernateTestMixin)
class PublicStaffControllerSpec extends CustomSpec {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }
    def setup() {
        super.setupData()
    }
    def cleanup() {
        super.cleanupData()
    }

    void "test list"() {
        when:
        request.method = "GET"
        controller.index()

        then:
        response.status == SC_METHOD_NOT_ALLOWED
    }

    void "test show"() {
        when:
        request.method = "GET"
        controller.show()

        then:
        response.status == SC_METHOD_NOT_ALLOWED
    }

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

    void "test update"() {
        when:
        request.method = "PUT"
        controller.update()

        then:
        response.status == SC_METHOD_NOT_ALLOWED
    }

    void "test delete"() {
        when:
        params.id = s1.id
        request.method = "DELETE"
        controller.delete()

        then:
        response.status == SC_METHOD_NOT_ALLOWED
    }
}
