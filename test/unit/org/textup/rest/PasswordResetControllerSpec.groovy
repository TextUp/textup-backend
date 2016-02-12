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
import spock.lang.Shared
import static javax.servlet.http.HttpServletResponse.*

@TestFor(PasswordResetController)
@Domain([Contact, Phone, ContactTag, ContactNumber, Record, RecordItem, RecordText,
    RecordCall, RecordItemReceipt, SharedContact, Staff, Team, Organization,
    Schedule, Location, WeeklySchedule, PhoneOwnership, Role, StaffRole])
@TestMixin(HibernateTestMixin)
class PasswordResetControllerSpec extends CustomSpec {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    private String _username, _token

    def setup() {
        super.setupData()
        controller.passwordResetService = [requestReset:{ String un ->
        	_username = un
        	new Result(type:ResultType.SUCCESS, success:true, payload:null)
    	}, resetPassword: { String token, String pwd ->
    		_token = token
    		new Result(type:ResultType.SUCCESS, success:true, payload:null)
		}] as PasswordResetService
    }
    def cleanup() {
        super.cleanupData()
    }

    protected String toJson(Closure data) {
    	JsonBuilder builder = new JsonBuilder()
    	builder(data)
    	builder.toString()
    }

    // Request reset
    // -------------

    void "test request reset without username"() {
    	when:
    	request.method = "POST"
    	request.json = "{}"
    	controller.requestReset()

    	then:
    	response.status == SC_BAD_REQUEST
    }

    void "test request request success"() {
    	when:
    	request.method = "POST"
    	request.json = toJson({
    		username(s1.username)
		})
    	controller.requestReset()

    	then:
    	response.status == SC_OK
    	_username == s1.username
    }

    // Complete request
    // ----------------

    void "test complete request with missing info"() {
    	when:
    	request.method = "PUT"
    	request.json = "{}"
    	controller.resetPassword()

    	then:
    	response.status == SC_BAD_REQUEST
    }

    void "test complete request success"() {
    	given:
    	String tok = "iamaspecialsnowflake"

    	when:
    	request.method = "PUT"
    	request.json = toJson({
    		token(tok)
    		password("sokewl")
		})
    	controller.resetPassword()

    	then:
    	response.status == SC_OK
    	 _token == tok
    }
}
