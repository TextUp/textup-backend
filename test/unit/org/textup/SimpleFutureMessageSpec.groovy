package org.textup

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import java.util.concurrent.TimeUnit
import org.joda.time.DateTime
import org.quartz.SimpleTrigger
import org.textup.types.FutureMessageType
import org.textup.types.RecordItemType
import org.textup.util.CustomSpec
import org.textup.validator.OutgoingMessage
import spock.lang.Ignore
import spock.lang.Shared

@Domain([Contact, Phone, ContactTag, ContactNumber, Record, RecordItem, RecordText,
	RecordCall, RecordItemReceipt, SharedContact, Staff, Team, Organization, Schedule,
	Location, WeeklySchedule, PhoneOwnership, FeaturedAnnouncement, IncomingSession,
	AnnouncementReceipt, Role, StaffRole, FutureMessage, SimpleFutureMessage])
@TestMixin(HibernateTestMixin)
class SimpleFutureMessageSpec extends CustomSpec {

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
    	SimpleFutureMessage sMsg = new SimpleFutureMessage()

    	then:
    	sMsg.validate() == false
    	sMsg.errors.errorCount == 3
    	sMsg.errors.getFieldErrorCount("record") == 1
    	sMsg.errors.getFieldErrorCount("message") == 1
    	sMsg.errors.getFieldErrorCount("type") == 1

    	when: "all required fields filled out"
    	sMsg.type = FutureMessageType.TEXT
    	sMsg.record = c1.record
    	sMsg.message = "hi"

    	then: "ok"
    	sMsg.validate() == true

    	when: "repeat interval too small"
    	sMsg.message = "hi"
    	assert sMsg.validate()
    	sMsg.repeatIntervalInMillis = 2000

    	then:
    	sMsg.validate() == false
    	sMsg.errors.errorCount == 1
    	sMsg.errors.getFieldErrorCount("repeatIntervalInMillis") == 1

    	when: "try to repeat infinitely"
    	sMsg.repeatIntervalInDays = 1
    	assert sMsg.validate()
    	sMsg.repeatCount = SimpleTrigger.REPEAT_INDEFINITELY

    	then:
    	sMsg.validate() == false
    	sMsg.errors.getFieldErrorCount("repeatCount") == 1
    }

    void "test repeating"() {
    	given: "an empty future message"
    	SimpleFutureMessage sMsg = new SimpleFutureMessage()

    	when: "neither repeatCount nor end date specified"
    	assert sMsg.repeatCount == null
    	assert sMsg.endDate == null

    	then:
    	sMsg.isRepeating == false

    	when: "repeat count specified"
    	sMsg.repeatCount = 2

    	then: "end after repeat count"
    	sMsg.isRepeating == true

    	when: "repeat count AND end date specified"
    	assert sMsg.repeatCount != null
    	sMsg.endDate = DateTime.now().plusDays(1)

    	then: "end after end date (takes precedence over repeatCount)"
    	sMsg.isRepeating == true
    }
}
