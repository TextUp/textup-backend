package org.textup.rest

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestFor
import grails.test.mixin.TestMixin
import grails.validation.ValidationErrors
import groovy.json.JsonBuilder
import groovy.xml.MarkupBuilder
import javax.servlet.http.HttpServletRequest
import org.joda.time.DateTime
import org.springframework.context.MessageSource
import org.textup.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.AvailablePhoneNumber
import org.textup.validator.PhoneNumber
import spock.lang.Shared
import static javax.servlet.http.HttpServletResponse.*

@TestFor(NumberController)
@Domain([Contact, Phone, ContactTag, ContactNumber, Record, RecordItem, RecordText,
    RecordCall, RecordItemReceipt, SharedContact, Staff, Team, Organization,
    Schedule, Location, WeeklySchedule, PhoneOwnership, Role, StaffRole, NotificationPolicy,
    MediaInfo, MediaElement, MediaElementVersion])
@TestMixin(HibernateTestMixin)
class NumberControllerSpec extends CustomSpec {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        setupData()
        controller.resultFactory = TestUtils.getResultFactory(grailsApplication)
    }

    def cleanup() {
        cleanupData()
    }

    // Listing available phone numbers
    // -------------------------------

    void "test listing available phone numbers"() {
        given:
        controller.authService = Mock(AuthService)
        controller.numberService = Mock(NumberService)
        AvailablePhoneNumber aNum = Mock()

        when: "not logged in and active"
        request.method = "GET"
        controller.index()

        then:
        1 * controller.authService.loggedInAndActive
        0 * controller.numberService._
        response.status == SC_FORBIDDEN

        when:
        response.reset()
        request.method = "GET"
        controller.index()

        then:
        1 * controller.authService.loggedInAndActive >> s1
        1 * controller.numberService.listExistingNumbers(*_) >> new Result(payload: [aNum])
        1 * controller.numberService.listNewNumbers(*_) >> new Result(payload: [aNum])
        response.status == SC_OK
        response.json.size() == 2
    }

    // Validate single number
    // ----------------------

    void "test validating numbers"() {
        given:
        controller.numberService = Mock(NumberService)
        AvailablePhoneNumber aNum = Mock()

        when:
        request.method = "GET"
        params.id = "i am not a number 123"
        controller.show()

        then:
        0 * controller.numberService._
        response.status == 422
        response.json.errors?.size() > 0

        when:
        response.reset()
        request.method = "GET"
        params.id = "111 222 i am a valid number 3333"
        controller.show()

        then:
        1 * controller.numberService.validateNumber(*_) >> new Result(payload: aNum)
        response.status == SC_OK
    }

    // Request verify
    // -------------

    void "test request verify without phoneNumber"() {
        given:
        controller.numberService = Mock(NumberService)

        when:
        request.method = "POST"
        request.json = "{}"
        controller.save()

        then:
        0 * controller.numberService._
        response.status == 422 // unprocessable entity
    }

    void "test request verify success"() {
        given:
        controller.numberService = Mock(NumberService)
        String num = TestUtils.randPhoneNumber()

        when:
        request.method = "POST"
        request.json = DataFormatUtils.toJsonString(phoneNumber: num)
        controller.save()

        then:
        1 * controller.numberService.startVerifyOwnership({ it.number == num }) >>
            new Result(status: ResultStatus.NO_CONTENT)
        response.status == SC_NO_CONTENT
        response.text == ""
    }

    // Complete verify
    // ----------------


    void "test complete verify success"() {
        given:
        controller.numberService = Mock(NumberService)
        String tok = TestUtils.randString()
        String num = TestUtils.randPhoneNumber()

        when:
        request.method = "POST"
        request.json = DataFormatUtils.toJsonString(token: tok, phoneNumber: num)
        controller.save()

        then:
        1 * controller.numberService.finishVerifyOwnership(tok, { it.number == num }) >>
            new Result(status: ResultStatus.NO_CONTENT)
        response.status == SC_NO_CONTENT
        response.text == ""
    }
}
