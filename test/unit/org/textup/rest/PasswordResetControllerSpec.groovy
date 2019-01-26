package org.textup.rest

import org.textup.test.*
import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestFor
import grails.test.mixin.TestMixin
import org.joda.time.DateTime
import org.textup.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*
import spock.lang.*

@Domain([AnnouncementReceipt, ContactNumber, CustomAccountDetails, FeaturedAnnouncement,
    FutureMessage, GroupPhoneRecord, IncomingSession, IndividualPhoneRecord, Location, MediaElement,
    MediaElementVersion, MediaInfo, Organization, OwnerPolicy, Phone, PhoneNumberHistory,
    PhoneOwnership, PhoneRecord, PhoneRecordMembers, Record, RecordCall, RecordItem,
    RecordItemReceipt, RecordNote, RecordNoteRevision, RecordText, Role, Schedule,
    SimpleFutureMessage, Staff, StaffRole, Team, Token])
@TestFor(PasswordResetController)
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
    	response.status == HttpServletResponse.SC_BAD_REQUEST
    }

    void "test request request success"() {
        given:
        String un = TestUtils.randString()
        controller.passwordResetService = Mock(PasswordResetService)

    	when:
    	request.method = "POST"
        request.json = DataFormatUtils.toJsonString(username: un)
    	controller.requestReset()

    	then:
        1 * controller.passwordResetService.start(un) >> new Result(status: ResultStatus.NO_CONTENT)
    	response.status == HttpServletResponse.SC_NO_CONTENT
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
    	response.status == HttpServletResponse.SC_BAD_REQUEST
    }

    void "test complete request success"() {
    	given:
    	String tok = TestUtils.randString()
        String pwd = TestUtils.randString()
        controller.passwordResetService = Mock(PasswordResetService)

    	when:
    	request.method = "PUT"
        request.json = DataFormatUtils.toJsonString(token: tok, password: pwd)
    	controller.resetPassword()

    	then:
        1 * controller.passwordResetService.finish(tok, pwd) >> new Result()
    	response.status == HttpServletResponse.SC_NO_CONTENT
    }
}
