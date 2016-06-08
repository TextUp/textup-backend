package org.textup.rest

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestFor
import grails.test.mixin.TestMixin
import grails.validation.ValidationErrors
import groovy.json.JsonBuilder
import groovy.xml.MarkupBuilder
import javax.servlet.http.HttpServletRequest
import org.codehaus.groovy.grails.web.servlet.mvc.GrailsParameterMap
import org.joda.time.DateTime
import org.springframework.context.MessageSource
import org.textup.*
import org.textup.types.ReceiptStatus
import org.textup.types.ResultType
import org.textup.util.CustomSpec
import org.textup.validator.PhoneNumber
import spock.lang.Shared
import static javax.servlet.http.HttpServletResponse.*

@TestFor(LookupNumberController)
@Domain([Contact, Phone, ContactTag, ContactNumber, Record, RecordItem, RecordText,
    RecordCall, RecordItemReceipt, SharedContact, Staff, Team, Organization,
    Schedule, Location, WeeklySchedule, PhoneOwnership, Role, StaffRole])
@TestMixin(HibernateTestMixin)
class LookupNumberControllerSpec extends CustomSpec {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    private String _token
    private PhoneNumber _reqPNum, _verifyPNum

    def setup() {
        super.setupData()
        controller.tokenService = [requestVerify:{ PhoneNumber pNum ->
            _reqPNum = pNum
            new Result(type:ResultType.SUCCESS, success:true, payload:null)
        }, verifyNumber: { String token, PhoneNumber pNum ->
            _token = token
            _verifyPNum = pNum
            new Result(type:ResultType.SUCCESS, success:true, payload:null)
        }] as TokenService
    }
    def cleanup() {
        super.cleanupData()
    }

    protected String toJson(Closure data) {
        JsonBuilder builder = new JsonBuilder()
        builder(data)
        builder.toString()
    }

    // Request verify
    // -------------

    void "test request verify without phoneNumber"() {
        when:
        request.method = "POST"
        request.json = "{}"
        controller.save()

        then:
        response.status == 422 // unprocessable entity
    }

    void "test request verify success"() {
        given:
        String num = "1112223333"

        when:
        request.method = "POST"
        request.json = toJson({
            phoneNumber(num)
        })
        controller.save()

        then:
        response.status == SC_NO_CONTENT
        _reqPNum.number == new PhoneNumber(number:num).number
    }

    // Complete verify
    // ----------------


    void "test complete verify success"() {
        given:
        String tok = "iamaspecialsnowflake"
        String num = "1112223333"

        when:
        request.method = "POST"
        request.json = toJson({
            token(tok)
            phoneNumber(num)
        })
        controller.save()

        then:
        response.status == SC_NO_CONTENT
        _verifyPNum.number == new PhoneNumber(number:num).number
         _token == tok
    }
}
