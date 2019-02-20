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
@TestFor(OwnerPolicyService)
class OwnerPolicyServiceSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test update"() {
        given:
        String tz = TestUtils.randString()
        TypeMap schedMap = TypeMap.create((TestUtils.randString()): TestUtils.randString())
        TypeMap body1 = TypeMap.create(frequency: NotificationFrequency.HOUR,
            level: NotificationLevel.NONE,
            method: NotificationMethod.EMAIL,
            shouldSendPreviewLink: true,
            schedule: schedMap)
        OwnerPolicy op1 = TestUtils.buildOwnerPolicy()

        service.scheduleService = GroovyMock(ScheduleService)

        when:
        Result res = service.tryUpdate(null, null, null)

        then:
        notThrown NullPointerException
        res.status == ResultStatus.INTERNAL_SERVER_ERROR

        when:
        res = service.tryUpdate(op1, body1, tz)

        then:
        1 * service.scheduleService.tryUpdate(op1.schedule, schedMap, tz) >> Result.void()
        res.status == ResultStatus.OK
        res.payload.frequency == body1.frequency
        res.payload.level == body1.level
        res.payload.method == body1.method
        res.payload.shouldSendPreviewLink == body1.shouldSendPreviewLink
    }
}
