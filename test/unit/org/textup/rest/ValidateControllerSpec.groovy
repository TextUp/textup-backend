package org.textup.rest

import grails.converters.JSON
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
import org.textup.util.CustomSpec
import spock.lang.Shared
import static javax.servlet.http.HttpServletResponse.*

@TestFor(ValidateController)
@Domain([Contact, Phone, ContactTag, ContactNumber, Record, RecordItem, RecordText,
    RecordCall, RecordItemReceipt, SharedContact, Staff, Team, Organization,
    Schedule, Location, WeeklySchedule, PhoneOwnership, Role, StaffRole, NotificationPolicy,
    MediaInfo, MediaElement, MediaElementVersion])
@TestMixin(HibernateTestMixin)
class ValidateControllerSpec extends CustomSpec {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }
    String _un, _pwd, _code

    def setup() {
        super.setupData()
        controller.authService = [isValidUsernamePassword:{ String un, String pwd ->
        	_un = un
        	_pwd = pwd
        	return true
    	}, isValidLockCode: { String un, String code ->
    		_un = un
    		_code = code
    		return true
		}] as AuthService
    }
    def cleanup() {
        super.cleanupData()
    }

    protected String toJsonString(Map data) {
        (data as JSON).toString()
    }

    void "test validating bad request"() {
    	when:
        request.json = toJsonString([:])
        request.method = "POST"
        controller.save()

        then:
        response.status == SC_BAD_REQUEST
    }
    void "test validating credentials"() {
    	when:
        String un = "hi"
        String pwd = "mypwd"
        _un = null
        _pwd = null
        request.json = toJsonString([username:un, password:pwd])
        request.method = "POST"
        controller.save()

        then:
        response.status == SC_NO_CONTENT
        _un == un
        _pwd == pwd
    }
    void "test validating lock code"() {
    	when:
        String un = "hi"
        String code = "1290"
        _un = null
        _code = null
        request.json = toJsonString([username:un, lockCode:code])
        request.method = "POST"
        controller.save()

        then:
        response.status == SC_NO_CONTENT
        _un == un
        _code == code
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
        params.id = s1.id
        request.method = "GET"
        controller.show()

        then:
        response.status == SC_METHOD_NOT_ALLOWED
    }

    void "test update"() {
        when:
        params.id = s1.id
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
