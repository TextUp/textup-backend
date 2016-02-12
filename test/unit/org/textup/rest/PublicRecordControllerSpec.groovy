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
import org.textup.types.ReceiptStatus
import org.textup.types.ResultType
import org.textup.util.CustomSpec
import spock.lang.Shared
import spock.lang.Specification
import static javax.servlet.http.HttpServletResponse.*

@TestFor(PublicRecordController)
@Domain([Contact, Phone, ContactTag, ContactNumber, Record, RecordItem, RecordText,
    RecordCall, RecordItemReceipt, SharedContact, Staff, Team, Organization,
    Schedule, Location, WeeklySchedule, PhoneOwnership, Role, StaffRole])
@TestMixin(HibernateTestMixin)
class PublicRecordControllerSpec extends CustomSpec {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }
    def setup() {
        super.setupData()
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

    protected mockForProcess() {
        controller.callbackService = [validate:{ HttpServletRequest request,
            GrailsParameterMap params ->
            new Result(type:ResultType.SUCCESS, success:true, payload:null)
        }, process:{ GrailsParameterMap params ->
            new Result(type:ResultType.SUCCESS, success:true, payload:_closure)
        }] as CallbackService
        controller.recordService = [updateStatus:{ ReceiptStatus status, String apiId,
            Integer duration ->
            _receiptStatus = status
            _apiId = apiId
            _duration = duration
            new Result(type:ResultType.SUCCESS, success:true, payload:_closure)
        }] as RecordService
    }
    protected String buildXml(Closure data) {
        StringWriter writer = new StringWriter()
        MarkupBuilder xmlBuilder = new MarkupBuilder(writer)
        xmlBuilder(data)
        writer.toString().replaceAll(/<call>|<\/call>|(\s+)/, "")
    }

    void "test update invalid status"() {
        when:
        mockForProcess()
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
        mockForProcess()
        request.method = "POST"
        params.handle = Constants.CALLBACK_STATUS
        params.CallSid = "sid"
        params.CallStatus = "undelivered"
        params.CallDuration = 123
        controller.save()

        then:
        response.status == SC_OK
        response.contentAsString == buildXml(_closure)
        _receiptStatus == ReceiptStatus.FAILED
        _apiId == params.CallSid
        _duration == params.CallDuration
    }

    void "test process xml"() {
        when: "handle is not CALLBACK_STATUS"
        mockForProcess()
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
