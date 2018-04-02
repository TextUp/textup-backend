package org.textup

import grails.gorm.DetachedCriteria
import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import java.util.concurrent.TimeUnit
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.textup.type.FutureMessageType
import org.textup.type.RecordItemType
import org.textup.util.CustomSpec
import org.textup.validator.OutgoingMessage
import spock.lang.Ignore
import spock.lang.Shared

@Domain([Contact, Phone, ContactTag, ContactNumber, Record, RecordItem, RecordText,
	RecordCall, RecordItemReceipt, SharedContact, Staff, Team, Organization, Schedule,
	Location, WeeklySchedule, PhoneOwnership, FeaturedAnnouncement, IncomingSession,
	AnnouncementReceipt, Role, StaffRole, FutureMessage, NotificationPolicy])
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
    	hasMembers[0].id == owner.id

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
    	hasMembers[0].id == owner.id

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
    		new Result(status:ResultStatus.OK)
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

    void "test building detached criteria for records"() {
        given: "valid record items"
        Record rec = new Record()
        rec.save(flush:true, failOnError:true)
        FutureMessage fm1 = new FutureMessage(type:FutureMessageType.TEXT, message:"hi", record:rec),
            fm2 = new FutureMessage(type:FutureMessageType.TEXT, message:"hi", record:rec)
        [fm1, fm2].each { FutureMessage fMsg ->
            fMsg.metaClass.refreshTrigger = { -> }
            fMsg.save(flush:true, failOnError:true)
        }

        when: "build detached criteria for these items"
        DetachedCriteria<FutureMessage> detachedCrit = FutureMessage.buildForRecords([rec])
        List<FutureMessage> fMsgList = detachedCrit.list()
        Collection<Long> targetIds = [fm1, fm2]*.id

        then: "we are able to fetch these items back from the db"
        fMsgList.size() == 2
        fMsgList.every { it.id in targetIds }
    }

    void "test checking for daylight savings time adjustment"() {
        given: "a valid future message with start date past next daylight savings change point"
        Record rec1 = new Record()
        rec1.save(flush:true, failOnError:true)
        FutureMessage fm1 = new FutureMessage(type:FutureMessageType.TEXT, message:"hi",
            record:rec1, startDate: DateTime.now().plusYears(10))
        fm1.metaClass.refreshTrigger = { -> }
        fm1.save(flush:true, failOnError:true)

        assert fm1.whenAdjustDaylightSavings == null
        assert fm1.hasAdjustedDaylightSavings == false
        assert fm1.daylightSavingsZone == null

        when: "check without timezone"
        fm1.checkScheduleDaylightSavingsAdjustment(null)

        then: "no change"
        fm1.whenAdjustDaylightSavings == null
        fm1.hasAdjustedDaylightSavings == false
        fm1.daylightSavingsZone == null

        when: "check with fixed timezone (doesn't observe daylight savings)"
        fm1.checkScheduleDaylightSavingsAdjustment(DateTimeZone.UTC)

        then: "no change"
        fm1.whenAdjustDaylightSavings == null
        fm1.hasAdjustedDaylightSavings == false
        fm1.daylightSavingsZone == null

        when: "check with mutable timezone"
        DateTimeZone tz1 = DateTimeZone.forID("America/New_York")
        fm1.checkScheduleDaylightSavingsAdjustment(tz1)

        then: "daylight savings adjustment data stored"
        fm1.whenAdjustDaylightSavings != null
        // adjustment time is in the future because we store the changepoint
        // immediately prior to the start date
        fm1.whenAdjustDaylightSavings.year > DateTime.now().year
        fm1.hasAdjustedDaylightSavings == false
        fm1.daylightSavingsZone == tz1

        when: "call again with same timezone"
        fm1.hasAdjustedDaylightSavings = true
        DateTime originalAdjustTime = fm1.whenAdjustDaylightSavings

        fm1.checkScheduleDaylightSavingsAdjustment(tz1)

        then: "short circuited because adjusted flag still true"
        fm1.whenAdjustDaylightSavings == originalAdjustTime
        fm1.hasAdjustedDaylightSavings == true
        fm1.daylightSavingsZone == tz1

        when: "call again with different timezone"
        DateTimeZone tz2 = DateTimeZone.forID("America/Los_Angeles")

        fm1.checkScheduleDaylightSavingsAdjustment(tz2)

        then: "reset to new adjustment time"
        fm1.whenAdjustDaylightSavings != originalAdjustTime
        fm1.whenAdjustDaylightSavings != null
        fm1.whenAdjustDaylightSavings.year > DateTime.now().year
        fm1.hasAdjustedDaylightSavings == false
        fm1.daylightSavingsZone == tz2
    }
}
