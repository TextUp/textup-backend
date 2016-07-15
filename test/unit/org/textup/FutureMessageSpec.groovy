package org.textup

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import java.util.concurrent.TimeUnit
import org.joda.time.DateTime
import org.textup.types.FutureMessageType
import org.textup.types.RecordItemType
import org.textup.util.CustomSpec
import org.textup.validator.OutgoingMessage
import spock.lang.Ignore
import spock.lang.Shared

@Domain([Contact, Phone, ContactTag, ContactNumber, Record, RecordItem, RecordText,
	RecordCall, RecordItemReceipt, SharedContact, Staff, Team, Organization, Schedule,
	Location, WeeklySchedule, PhoneOwnership, FeaturedAnnouncement, IncomingSession,
	AnnouncementReceipt, Role, StaffRole, FutureMessage])
@TestMixin(HibernateTestMixin)
class FutureMessageSpec extends CustomSpec {

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
    	when: "an empty future message"
    	FutureMessage fMsg = new FutureMessage()

    	then:
    	fMsg.validate() == false
    	fMsg.errors.errorCount == 3
    	fMsg.errors.getFieldErrorCount("record") == 1
    	fMsg.errors.getFieldErrorCount("message") == 1
    	fMsg.errors.getFieldErrorCount("type") == 1

    	when: "all required fields filled out"
    	fMsg.type = FutureMessageType.TEXT
    	fMsg.record = c1.record
    	fMsg.message = "hi"

    	then: "ok"
    	fMsg.validate() == true

    	when: "message is too long"
    	fMsg.message = '''
			Far far away, behind the word mountains, far from the countries Vokalia and
			Consonantia, there live the blind texts. Separated they live in Bookmarksgrove
			right at the coast of the Semantics, a large language ocean. A small river
			named Duden flows by their place and supplies it with the necessary regelialia.
			It is a paradisemati
		'''

    	then:
    	fMsg.validate() == false
    	fMsg.errors.errorCount == 1
    	fMsg.errors.getFieldErrorCount("message") == 1

    	when: "try to end before start"
    	fMsg.message = "hi"
    	assert fMsg.validate()
    	fMsg.startDate = DateTime.now()
    	fMsg.endDate = DateTime.now().minusDays(1)

    	then:
    	fMsg.validate() == false
    	fMsg.errors.getFieldErrorCount("endDate") == 1
    }

    void "test converting to outgoing message for #type's record"() {
    	given: "a valid future message"
    	def owner = (type == "contact") ? c1 : tag1
    	FutureMessage fMsg = new FutureMessage(type:FutureMessageType.CALL,
    		record:owner.record, message:"hi")
    	assert fMsg.validate()

    	when: "message is a text"
    	fMsg.type = FutureMessageType.TEXT
    	OutgoingMessage msg = fMsg.toOutgoingMessage()
    	Collection hasMembers, noMembers
    	if (type == "contact") {
			hasMembers = msg.contacts
			noMembers = msg.tags
    	}
    	else {
    		hasMembers = msg.tags
			noMembers = msg.contacts
    	}

    	then: "make outgoing message without flushing"
    	msg.type == RecordItemType.TEXT
    	msg.message == fMsg.message
		noMembers.isEmpty()
    	hasMembers.size() == 1
    	hasMembers[0] == owner

    	when: "message is a call"
    	fMsg.type = FutureMessageType.CALL
    	msg = fMsg.toOutgoingMessage()
    	if (type == "contact") {
			hasMembers = msg.contacts
			noMembers = msg.tags
    	}
    	else {
    		hasMembers = msg.tags
			noMembers = msg.contacts
    	}

    	then: "make outgoing message without flushing"
    	msg.type == RecordItemType.CALL
    	msg.message == fMsg.message
    	noMembers.isEmpty()
    	hasMembers.size() == 1
    	hasMembers[0] == owner

    	where:
		type      | _
		"contact" | _
		"tag"     | _
    }

    void "test cancelling"() {
    	when: "have a valid future message"
    	boolean calledUnschedule = false
    	FutureMessage fMsg = new FutureMessage(type:FutureMessageType.TEXT,
    		message:"hi", record:c1.record)
    	fMsg.futureMessageService = [unschedule:{ FutureMessage fMessage ->
    		calledUnschedule = true
    		new Result(success:true)
		}] as FutureMessageService
		assert fMsg.validate()

    	then: "not done yet"
    	fMsg.isDone == false

    	when: "call cancel"
    	fMsg.cancel()

    	then:
    	fMsg.validate()
    	calledUnschedule == true
    	fMsg.isDone == true
    }
}
