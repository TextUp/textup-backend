package org.textup

import grails.test.mixin.*
import grails.test.mixin.gorm.*
import grails.test.mixin.hibernate.*
import grails.test.mixin.support.*
import grails.test.runtime.*
import grails.validation.*
import org.joda.time.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*
import spock.lang.*

@Domain([AnnouncementReceipt, ContactNumber, CustomAccountDetails, FeaturedAnnouncement,
    FutureMessage, GroupPhoneRecord, IncomingSession, IndividualPhoneRecord, Location, MediaElement,
    MediaElementVersion, MediaInfo, Organization, OwnerPolicy, Phone, PhoneNumberHistory,
    PhoneOwnership, PhoneRecord, PhoneRecordMembers, Record, RecordCall, RecordItem,
    RecordItemReceipt, RecordNote, RecordNoteRevision, RecordText, Role, Schedule,
    SimpleFutureMessage, Staff, StaffRole, Team, Token])
@TestMixin(HibernateTestMixin)
class FutureMessageSpec extends Specification {

	static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test constraints"() {
    	when: "an empty future message"
    	FutureMessage fMsg = new FutureMessage()

    	then:
    	fMsg.validate() == false
    	fMsg.errors.errorCount == 3
    	fMsg.errors.getFieldErrorCount("record") == 1
    	fMsg.errors.getFieldErrorCount("media") == 1
        fMsg.errors.getFieldError("media").codes.contains("futureMessage.media.noInfo")
    	fMsg.errors.getFieldErrorCount("type") == 1

    	when: "all required fields filled out"
    	fMsg.type = FutureMessageType.TEXT
    	fMsg.record = TestUtils.buildRecord()
    	fMsg.message = TestUtils.randString()

    	then: "ok -- requires either media or message or both"
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
