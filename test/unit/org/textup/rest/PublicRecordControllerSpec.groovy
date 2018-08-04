package org.textup.rest

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestFor
import grails.test.mixin.TestMixin
import grails.validation.ValidationErrors
import groovy.xml.MarkupBuilder
import javax.servlet.http.HttpServletRequest
import org.codehaus.groovy.grails.web.servlet.mvc.GrailsParameterMap
import org.joda.time.DateTime
import org.springframework.context.MessageSource
import org.textup.*
import org.textup.type.ReceiptStatus
import org.textup.util.*
import org.textup.validator.BasePhoneNumber
import org.textup.validator.PhoneNumber
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
    }
    def setup() {
        super.setupData()
        controller.twimlBuilder = [noResponse: { ->
            new Result(status:ResultStatus.OK, payload:{ Response {} })
        }] as TwimlBuilder
    }
    def cleanup() {
        super.cleanupData()
    }

    // Process
    // -------

    private String _apiId
    private ReceiptStatus _receiptStatus
    private Integer _duration
    private Closure _closure = { Test() }

    protected mockValidate() {
        controller.callbackService = [validate:{ HttpServletRequest request,
            GrailsParameterMap params ->
            new Result(payload:null, status:ResultStatus.OK)
        }, process:{ GrailsParameterMap params ->
            new Result(payload:_closure, status:ResultStatus.OK)
        }] as CallbackService
    }
    protected mockUpdateStatus() {
        controller.recordService = [updateStatus:{ ReceiptStatus status, String apiId,
            Integer duration ->
            _receiptStatus = status
            _apiId = apiId
            _duration = duration
            new Result(payload:_closure, status:ResultStatus.OK)
        }] as RecordService
    }
    protected String buildXml(Closure data) {
        StringWriter writer = new StringWriter()
        MarkupBuilder xmlBuilder = new MarkupBuilder(writer)
        xmlBuilder(data)
        writer.toString().replaceAll(/<call>|<\/call>|(\s+)/, "")
    }

    void "test updating status for not found receipt"() {
        when:
        mockValidate()
        request.method = "POST"
        controller.recordService = [updateStatus:{ ReceiptStatus status, String apiId,
            Integer duration ->
            TestHelpers.getResultFactory(grailsApplication).
                failWithCodeAndStatus("recordService.updateStatus.receiptsNotFound",
                    ResultStatus.NOT_FOUND, [apiId])
        }] as RecordService
        controller.twimlBuilder = [noResponse: { ->
            new Result(status:ResultStatus.OK, payload:_closure)
        }] as TwimlBuilder
        params.handle = Constants.CALLBACK_STATUS
        params.CallSid = "sid"
        params.CallStatus = "validStatus"
        params.CallDuration = 123
        controller.save()

        then: "return ok when not found and still return no response closure"
        response.status == SC_OK
        response.contentAsString == buildXml(_closure)
    }

    void "test retry call on status fail"() {
        given:
        mockValidate()
        request.method = "POST"

        boolean _didRetry = false
        controller.recordService = [updateStatus:{ ReceiptStatus status, String apiId,
            Integer duration ->
            new Result(status:ResultStatus.NOT_FOUND,
                payload:[statusCode:ResultStatus.NOT_FOUND.intStatus, message:"error"])
        }] as RecordService
        controller.callService = [retry: { PhoneNumber fromNum,
            List<? extends BasePhoneNumber> toNums, String apiId, Map afterPickup ->
            _didRetry = true
            new Result(status:ResultStatus.OK, payload:null)
        }] as CallService

        when: "updating status failed"
        params.handle = Constants.CALLBACK_STATUS
        params.CallSid = "sid"
        params.CallStatus = Constants.FAILED_STATUSES[0]
        params.CallDuration = 123
        controller.save()

        then: "don't retry"
        _didRetry == false
    }

    void "test retry call on status succeed"() {
        given:
        mockValidate()
        request.method = "POST"

        boolean _didRetry = false
        controller.recordService = [updateStatus:{ ReceiptStatus status, String apiId,
            Integer duration ->
            new Result(status:ResultStatus.OK)
        }] as RecordService
        controller.callService = [retry: { PhoneNumber fromNum,
            List<? extends BasePhoneNumber> toNums, String apiId, Map afterPickup ->
            _didRetry = true
            new Result(status:ResultStatus.OK, payload:null)
        }] as CallService

        when: "updating status succeeds but the status is a failed status"
        params.handle = Constants.CALLBACK_STATUS
        params.CallSid = "sid"
        params.CallStatus = Constants.FAILED_STATUSES[0]
        params.CallDuration = 123
        controller.save()

        then: "do retry"
        _didRetry == true
    }

    void "test update invalid status"() {
        when:
        mockValidate()
        mockUpdateStatus()
        request.method = "POST"
        params.handle = Constants.CALLBACK_STATUS
        params.CallSid = "sid"
        params.CallStatus = "invalid"
        params.CallDuration = 123
        controller.save()

        then:
        response.status == SC_OK
        response.contentAsString == buildXml(_closure)
        _receiptStatus == ReceiptStatus.SUCCESS
        _apiId == params.CallSid
        _duration == params.CallDuration
    }

    void "test update valid status"() {
        when:
        mockValidate()
        mockUpdateStatus()
        request.method = "POST"
        params.handle = Constants.CALLBACK_STATUS
        params.CallSid = "sid"
        params.CallStatus = Constants.PENDING_STATUSES[0]
        params.CallDuration = 123
        controller.save()

        then:
        response.status == SC_OK
        response.contentAsString == buildXml(_closure)
        _receiptStatus == ReceiptStatus.PENDING
        _apiId == params.CallSid
        _duration == params.CallDuration
    }

    void "test process xml"() {
        when: "handle is not CALLBACK_STATUS"
        mockValidate()
        request.method = "POST"
        params.handle = "NOT CALLBACK STATUS!!!"
        controller.save()

        then:
        response.status == SC_OK
        response.contentAsString == buildXml(_closure)
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
