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
import org.textup.type.ReceiptStatus
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

    private String _token
    private PhoneNumber _reqPNum, _verifyPNum

    def setup() {
        super.setupData()
        controller.tokenService = [requestVerify:{ PhoneNumber pNum ->
            _reqPNum = pNum
            new Result(status:ResultStatus.NO_CONTENT, payload:null)
        }, verifyNumber: { String token, PhoneNumber pNum ->
            _token = token
            _verifyPNum = pNum
            new Result(status:ResultStatus.NO_CONTENT, payload:null)
        }] as TokenService
        controller.grailsApplication.flatConfig = config.flatten()
    }
    def cleanup() {
        super.cleanupData()
    }

    protected String toJson(Closure data) {
        JsonBuilder builder = new JsonBuilder()
        builder(data)
        builder.toString()
    }

    // Listing available phone numbers
    // -------------------------------

    void "test listing available phone numbers"() {
        when: "not logged in and active"
        controller.authService = [getLoggedInAndActive:{ -> null }] as AuthService
        request.method = "GET"
        controller.index()

        then:
        response.status == SC_FORBIDDEN

        when:
        response.reset()
        controller.authService = [getLoggedInAndActive:{ -> s1 }] as AuthService
        controller.numberService = [
            listExistingNumbers:{ ->
                AvailablePhoneNumber aNum = new AvailablePhoneNumber(phoneNumber:"111@222 3333",
                    infoType:"region", info:"i am a valid region here")
                assert aNum.validate() == true
                new Result(payload:[aNum], status:ResultStatus.OK)
            },
            listNewNumbers: { String search, Location loc1 ->
                AvailablePhoneNumber aNum = new AvailablePhoneNumber(phoneNumber:"111@222 3333",
                    infoType:"region", info:"i am a valid region here")
                assert aNum.validate() == true
                new Result(payload:[aNum], status:ResultStatus.OK)
            }
        ] as NumberService
        controller.resultFactory = TestHelpers.getResultFactory(grailsApplication)
        request.method = "GET"
        controller.index()

        then:
        response.status == SC_OK
        response.json.size() == 2
    }

    // Validate single number
    // ----------------------

    void "test validating numbers"() {
        given:
        controller.numberService = [validateNumber:{ PhoneNumber pNum ->
            new Result(status:ResultStatus.OK, payload:[valid:true])
        }] as NumberService

        when:
        request.method = "GET"
        params.id = "i am not a number 123"
        controller.show()

        then:
        response.status == 422
        response.json.errors?.size() > 0

        when:
        response.reset()
        request.method = "GET"
        params.id = "111 222 i am a valid number 3333"
        controller.show()

        then:
        response.status == SC_OK
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
        response.text == ""
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
        response.text == ""
        _verifyPNum.number == new PhoneNumber(number:num).number
         _token == tok
    }
}
