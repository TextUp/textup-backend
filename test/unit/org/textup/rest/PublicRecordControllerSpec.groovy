package org.textup.rest

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestFor
import grails.test.mixin.TestMixin
import java.util.concurrent.TimeUnit
import org.textup.*
import org.textup.rest.TwimlBuilder
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*
import spock.lang.Shared
import spock.lang.Specification
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
        twimlBuilder(TwimlBuilder)
    }
    def setup() {
        setupData()
    }
    def cleanup() {
        cleanupData()
    }

    // Process
    // -------

    void "test invalid request"() {
        given:
        controller.callbackService = Mock(CallbackService)
        controller.threadService = Mock(ThreadService)

        when:
        request.method = "POST"
        controller.save()

        then:
        1 * controller.callbackService.validate(*_) >> new Result(status: ResultStatus.BAD_REQUEST)
        0 * controller.callbackService.process(*_)
        0 * controller.threadService._
        response.status == ResultStatus.BAD_REQUEST.intStatus
    }

    void "test handling status"() {
        given:
        controller.callbackService = Mock(CallbackService)
        controller.callbackStatusService = Mock(CallbackStatusService)
        controller.threadService = Mock(ThreadService)
        controller.twimlBuilder = TestHelpers.getTwimlBuilder(grailsApplication)

        when:
        request.method = "POST"
        params.handle = Constants.CALLBACK_STATUS
        controller.save()

        then:
        1 * controller.callbackService.validate(*_) >> new Result()
        1 * controller.threadService.submit(_ as Long, _ as TimeUnit, _ as Closure) >> { args ->
            args[2](); return null;
        }
        1 * controller.callbackStatusService.process(*_)
        0 * controller.callbackService.process(*_)
        response.status == SC_OK
        response.text.contains("Response")
        response.xml.toString() == "" // empty response
    }

    void "test processing webhook"() {
        given:
        controller.callbackService = Mock(CallbackService)
        controller.threadService = Mock(ThreadService)

        when:
        request.method = "POST"
        controller.save()

        then:
        0 * controller.threadService._
        1 * controller.callbackService.validate(*_) >> new Result()
        1 * controller.callbackService.process(*_) >> new Result(payload: { Test() })
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
