package org.textup.rest

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestFor
import grails.test.mixin.TestMixin
import org.joda.time.DateTime
import org.textup.*
import org.textup.util.*
import spock.lang.Shared
import static javax.servlet.http.HttpServletResponse.*

@TestFor(PasswordResetController)
@Domain([Contact, Phone, ContactTag, ContactNumber, Record, RecordItem, RecordText,
    RecordCall, RecordItemReceipt, SharedContact, Staff, Team, Organization,
    Schedule, Location, WeeklySchedule, PhoneOwnership, Role, StaffRole, NotificationPolicy,
    MediaInfo, MediaElement, MediaElementVersion])
@TestMixin(HibernateTestMixin)
class PasswordResetControllerSpec extends CustomSpec {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        setupData()
    }

    def cleanup() {
        cleanupData()
    }

    // Request reset
    // -------------

    void "test request reset without username"() {
        given:
        controller.passwordResetService = Mock(PasswordResetService)

    	when:
    	request.method = "POST"
    	request.json = "{}"
    	controller.requestReset()

    	then:
        0 * controller.passwordResetService._
    	response.status == SC_BAD_REQUEST
    }

    void "test request request success"() {
        given:
        String un = TestUtils.randString()
        controller.passwordResetService = Mock(PasswordResetService)

    	when:
    	request.method = "POST"
    	request.json = Utils.toJsonString { username(un) }
    	controller.requestReset()

    	then:
        1 * controller.passwordResetService.start(un) >> new Result(status: ResultStatus.NO_CONTENT)
    	response.status == SC_NO_CONTENT
    }

    // Complete request
    // ----------------

    void "test complete request with missing info"() {
        given:
        controller.passwordResetService = Mock(PasswordResetService)

    	when:
    	request.method = "PUT"
    	request.json = "{}"
    	controller.resetPassword()

    	then:
        0 * controller.passwordResetService._
    	response.status == SC_BAD_REQUEST
    }

    void "test complete request success"() {
    	given:
    	String tok = TestUtils.randString()
        String pwd = TestUtils.randString()
        controller.passwordResetService = Mock(PasswordResetService)

    	when:
    	request.method = "PUT"
    	request.json = Utils.toJsonString {
    		token(tok)
    		password(pwd)
		}
    	controller.resetPassword()

    	then:
        1 * controller.passwordResetService.finish(tok, pwd) >> new Result()
    	response.status == SC_NO_CONTENT
    }
}
