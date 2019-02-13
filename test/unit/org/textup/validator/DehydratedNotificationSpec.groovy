package org.textup.validator

import grails.test.mixin.*
import grails.test.mixin.gorm.*
import grails.test.mixin.hibernate.*
import grails.test.mixin.support.*
import grails.test.runtime.*
import grails.validation.*
import org.joda.time.*
import org.textup.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import spock.lang.*

@Domain([AnnouncementReceipt, ContactNumber, CustomAccountDetails, FeaturedAnnouncement,
    FutureMessage, GroupPhoneRecord, IncomingSession, IndividualPhoneRecord, Location, MediaElement,
    MediaElementVersion, MediaInfo, Organization, OwnerPolicy, Phone, PhoneNumberHistory,
    PhoneOwnership, PhoneRecord, PhoneRecordMembers, Record, RecordCall, RecordItem,
    RecordItemReceipt, RecordNote, RecordNoteRevision, RecordText, Role, Schedule,
    SimpleFutureMessage, Staff, StaffRole, Team, Token])
@TestMixin(HibernateTestMixin)
class DehydratedNotificationSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test creation + rehydration"() {
        given:
        Notification notif1 = TestUtils.buildNotification()

        when:
        Result res = DehydratedNotification.tryCreate(null)

        then:
        res.status == ResultStatus.INTERNAL_SERVER_ERROR

        when:
        res = DehydratedNotification.tryCreate(null, null)

        then:
        res.status == ResultStatus.UNPROCESSABLE_ENTITY

        when:
        res = DehydratedNotification.tryCreate(notif1)

        then:
        res.status == ResultStatus.CREATED
        res.payload.phoneId == notif1.mutablePhone.id
        res.payload.itemIds.size() == 2
        notif1.itemIds.every { it in res.payload.itemIds }

        when:
        res = res.payload.tryRehydrate()

        then:
        res.status == ResultStatus.OK
        res.payload.mutablePhone == notif1.mutablePhone
        res.payload.details.every { it in notif1.details }
    }
}
