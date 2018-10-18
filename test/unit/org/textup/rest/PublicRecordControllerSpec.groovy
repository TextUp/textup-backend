package org.textup.rest

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestFor
import grails.test.mixin.TestMixin
import grails.test.runtime.*
import java.util.concurrent.TimeUnit
import org.textup.*
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*
import spock.lang.*
import static javax.servlet.http.HttpServletResponse.*
import static org.springframework.http.HttpStatus.*

@TestFor(PublicRecordController)
@Domain([Contact, Phone, ContactTag, ContactNumber, Record, RecordItem, RecordText,
    RecordCall, RecordItemReceipt, SharedContact, Staff, Team, Organization,
    Schedule, Location, WeeklySchedule, PhoneOwnership, Role, StaffRole, NotificationPolicy,
    MediaInfo, MediaElement, MediaElementVersion])
@TestMixin(HibernateTestMixin)
class PublicRecordControllerSpec extends CustomSpec {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }
    def setup() {
        setupData()
    }
    def cleanup() {
        cleanupData()
    }

    // Process
    // -------

    @DirtiesRuntime
    void "test invalid request"() {
        given:
        MockedMethod validate = TestHelpers.mock(TwilioUtils, "validate")
            { new Result(status: ResultStatus.BAD_REQUEST) }
        controller.callbackService = Mock(CallbackService)
        controller.threadService = Mock(ThreadService)

        when:
        request.method = "POST"
        controller.save()

        then:
        0 * controller.callbackService.process(*_)
        0 * controller.threadService._
        validate.callCount == 1
        response.status == ResultStatus.BAD_REQUEST.intStatus
    }

    @DirtiesRuntime
    void "test handling status"() {
        given:
        controller.callbackService = Mock(CallbackService)
        controller.callbackStatusService = Mock(CallbackStatusService)
        controller.threadService = Mock(ThreadService)
        MockedMethod validate = TestHelpers.mock(TwilioUtils, "validate") { new Result() }

        when:
        request.method = "POST"
        params.handle = Constants.CALLBACK_STATUS
        controller.save()

        then:
        1 * controller.threadService.delay(_ as Long, _ as TimeUnit, _ as Closure) >> { args ->
            args[2](); return null;
        }
        1 * controller.callbackStatusService.process(*_)
        0 * controller.callbackService.process(*_)
        validate.callCount == 1
        response.status == SC_OK
        response.text.contains("Response")
        response.xml.toString() == "" // empty response
    }

    @DirtiesRuntime
    void "test processing webhook"() {
        given:
        controller.callbackService = Mock(CallbackService)
        controller.threadService = Mock(ThreadService)
        MockedMethod validate = TestHelpers.mock(TwilioUtils, "validate") { new Result() }

        when:
        request.method = "POST"
        controller.save()

        then:
        0 * controller.threadService._
        1 * controller.callbackService.process(*_) >> new Result(payload: { Test() })
        validate.callCount == 1
        response.status == SC_OK
    }

    // Not allowed
    // -----------

    void "test index"() {
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
    void "test update"() {
        when:
        request.method = "PUT"
        controller.update()

        then:
        response.status == SC_METHOD_NOT_ALLOWED
    }
    void "test delete"() {
        when:
        request.method = "DELETE"
        controller.delete()

        then:
        response.status == SC_METHOD_NOT_ALLOWED
    }
}
