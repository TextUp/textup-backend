package org.textup.rest

import grails.plugin.springsecurity.SpringSecurityService
import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestFor
import grails.test.mixin.TestMixin
import grails.validation.ValidationErrors
import org.joda.time.DateTime
import org.springframework.context.MessageSource
import org.textup.*
import org.textup.util.CustomSpec
import spock.lang.Shared
import spock.lang.Specification
import static javax.servlet.http.HttpServletResponse.*

@TestFor(PublicRecordController)
@Domain([TagMembership, Contact, Phone, ContactTag,
    ContactNumber, Record, RecordItem, RecordNote, RecordText,
    RecordCall, RecordItemReceipt, PhoneNumber, SharedContact,
    TeamMembership, StaffPhone, Staff, Team, Organization,
    Schedule, Location, TeamPhone, WeeklySchedule, TeamContactTag])
@TestMixin(HibernateTestMixin)
class PublicRecordControllerSpec extends CustomSpec {

    static doWithSpring = {
        resultFactory(ResultFactory)
        twimlBuilder(TwimlBuilder)
    }
    def setup() {
        super.setupData()
        controller.twimlBuilder = getBean("twimlBuilder")

        //TODO: need to mock
        // callService
        // textService
        // recordService
        // staffService
        // authenticateRequest

    }
    def cleanup() {
        super.cleanupData()
    }

    void "test invalid action"() {
        expect:
        1 == 2
    }

    ////////////////////////
    // Repeating messages //
    ////////////////////////

    void "test repeating for invalid response code"() {

    }

    void "test repeating a response that requires parameters"() {

    }

    ////////////////////
    // Incoming calls //
    ////////////////////

    void "test incoming call to own staff phone"() {

    }

    void "test incoming call to a staff phone"() {

    }

    void "test incoming call to team phone"() {

    }

    void "test incoming call to nonexistent phone"() {

    }

    /////////////////////////
    // Setting call status //
    /////////////////////////

    void "test set status for existing receipt"() {

    }

    void "test set status for existing receipt for forwarded call"() {

    }

    void "test set status for nonexistent receipt with missing contactId"() {

    }

    void "test set status for nonexistent receipt from Twilio Client call"() {

    }

    ///////////////
    // Voicemail //
    ///////////////

    void "test voicemail for nonexistent receipt"() {

    }

    void "test voicemail for existing receipt"() {

    }

    /////////////////
    // Team digits //
    /////////////////

    void "test team digits for directly connecting to staff"() {

    }

    void "test handling digits that are not direct connection to staff"() {

    }

    //////////////////
    // Staff digits //
    //////////////////

    void "test self digits are valid code or number"() {

    }

    void "test self digits are invalid"() {

    }

    ///////////////////
    // Incoming text //
    ///////////////////

    void "test incoming text"() {

    }

    /////////////////////////
    // Setting text status //
    /////////////////////////

    void "test setting text status"() {

    }

    /////////////////////////
    // Not allowed methods //
    /////////////////////////

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
