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
class FutureMessageCleanupJobSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
        IOCUtils.metaClass."static".getQuartzScheduler = { -> TestUtils.mockScheduler() }
    }

    void "test cleaning up completed messages not marked as such"() {
        given: "a future message not properly marked as done"
        FutureMessage fMsg1 = TestUtils.buildFutureMessage()
        fMsg1.startDate = DateTime.now().minusDays(10)
        fMsg1.isDone = false
        fMsg1.withSession { it.flush() }

        when: "executing this job"
        // isReallyDone will return true because the futureMessage's trigger
        // will be null because we've overridden `refreshTrigger` to avoid
        // setting the `trigger` property of the future message
        FutureMessageCleanupJob job1 = new FutureMessageCleanupJob()
        job1.execute()

        then: "future message is not properly marked as done"
        fMsg1.isDone == true
    }
}
