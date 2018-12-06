package org.textup

import com.twilio.Twilio
import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestFor
import grails.test.mixin.TestMixin
import grails.test.runtime.FreshRuntime
import org.codehaus.groovy.grails.web.mapping.LinkGenerator
import org.springframework.context.MessageSource
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*
import spock.lang.Shared
import spock.lang.Specification
import static org.springframework.http.HttpStatus.*

// This test class checks success and error states and does not test
// how callback maps are handled. For tests on callback handling behavior
// see `CallServiceSpec.groovy`

@Domain([Record, RecordItem, RecordText, RecordCall, RecordItemReceipt,
    MediaInfo, MediaElement, MediaElementVersion])
@TestMixin(HibernateTestMixin)
@TestFor(CallService)
class CallServiceNoMockSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        def twilioTestConfig = grailsApplication.config.textup.apiKeys.twilio
        Twilio.init(twilioTestConfig.sid, twilioTestConfig.authToken)
        IOCUtils.metaClass."static".getWebhookLink = { Map args = [:] -> "http://www.example.com" }
        service.resultFactory = TestUtils.getResultFactory(grailsApplication)
    }

    void "test starting a call to one or more numbers"() {
        given:
        PhoneNumber invalidFrom1 = new PhoneNumber(number:TestConstants.TEST_CALL_FROM_NOT_VALID)
        PhoneNumber fromNum1 = new PhoneNumber(number:TestConstants.TEST_CALL_FROM_VALID),
            toNum1 = new PhoneNumber(number:"+16262223334"),
            toNum2 = new PhoneNumber(number:"+16262223335"),
            invalidNum1 = new PhoneNumber(number:TestConstants.TEST_CALL_TO_NOT_VALID)
        assert [invalidFrom1, fromNum1, toNum1, toNum2, invalidNum1].each { it.validate() }

        when: "we try to call with an invalid 'from' number"
        Result<TempRecordReceipt> res = service.start(invalidFrom1, toNum1, [:])

        then:
        res.success == false
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages.size() == 1

        when: "we start the call with an invalid 'to' number"
        res = service.start(fromNum1, invalidNum1, [:])

        then:
        res.success == false
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages.size() == 1

        when: "we start a call with one 'to' number"
        res = service.start(fromNum1, toNum1, [:])

        then:
        res.success == true
        res.status == ResultStatus.OK
        res.payload instanceof TempRecordReceipt
        res.payload.apiId != null
        res.payload.contactNumberAsString == toNum1.number

        when: "we start a call with multiple 'to' numbers where first is invalid"
        res = service.start(fromNum1, [invalidNum1, toNum1, toNum2], [:])

        then: "first invalid number is ignored"
        res.success == true
        res.status == ResultStatus.OK
        res.payload instanceof TempRecordReceipt
        res.payload.apiId != null
        res.payload.contactNumberAsString == toNum1.number
    }
}
