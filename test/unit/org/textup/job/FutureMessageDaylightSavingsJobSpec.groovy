package org.textup.job

import grails.test.mixin.*
import grails.test.mixin.gorm.*
import grails.test.mixin.hibernate.*
import org.joda.time.*
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
@TestMixin(HibernateTestMixin)
class FutureMessageDaylightSavingsJobSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
        IOCUtils.metaClass."static".getQuartzScheduler = { -> TestUtils.mockScheduler() }
    }

    void "test adjusting daylight savings time by hour"() {
        given: "a future message needing adjusting"
        FutureMessage fMsg1 = TestUtils.buildFutureMessage()
        fMsg1.isDone = false
        fMsg1.hasAdjustedDaylightSavings = false
        fMsg1.whenAdjustDaylightSavings = DateTime.now().minusDays(1)
        fMsg1.daylightSavingsZone = DateTimeZone.forID("America/New_York")
        FutureMessage.withSession { it.flush() }

        when:
        FutureMessageDaylightSavingsJob job1 = new FutureMessageDaylightSavingsJob()
        job1.execute()

        then:
        fMsg1.hasAdjustedDaylightSavings == true
    }
}
