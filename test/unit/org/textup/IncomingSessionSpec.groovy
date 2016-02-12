package org.textup

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import grails.validation.ValidationErrors
import org.springframework.context.MessageSource
import org.textup.types.PhoneOwnershipType
import org.textup.types.StaffStatus
import org.textup.util.CustomSpec
import spock.lang.Ignore
import spock.lang.Shared
import org.joda.time.DateTime

@Domain([Contact, Phone, ContactTag, ContactNumber, Record, RecordItem, RecordText,
	RecordCall, RecordItemReceipt, SharedContact, Staff, Team, Organization,
	Schedule, Location, WeeklySchedule, PhoneOwnership, IncomingSession])
@TestMixin(HibernateTestMixin)
class IncomingSessionSpec extends CustomSpec {

	static doWithSpring = {
		resultFactory(ResultFactory)
	}

    def setup() {
    	setupData()
    }

    def cleanup() {
    	cleanupData()
    }

    void "test constraints"() {
        when: "we have an empty session"
        IncomingSession session = new IncomingSession()

        then: "invalid"
        session.validate() == false
        session.errors.errorCount == 2

        when: "we fill out necessary fields"
        session = new IncomingSession(phone:p1, numberAsString:"2223334444")

        then: "valid"
        session.validate() == true
    }

    void "test last sent instructions"() {
    	given: "a valid session"
    	IncomingSession session = new IncomingSession(phone:p1,
    		numberAsString:"2223334444")
    	DateTime currentTimestamp = session.lastSentInstructions

    	when: "we update last sent instructions"
    	session.updateLastSentInstructions()

    	then:
    	session.lastSentInstructions.isAfter(currentTimestamp)
    	session.shouldSendInstructions == false

    	when: "we change last sent to yesterday"
    	session.lastSentInstructions = DateTime.now().minusDays(2)
    	currentTimestamp = session.lastSentInstructions

    	then: "we should send info again"
    	session.shouldSendInstructions == true
    }
}
