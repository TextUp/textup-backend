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
class DehydratedNotificationGroupSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test creation + rehydration"() {
        given:
        Notification notif1 = TestUtils.buildNotification()
        Notification notif2 = TestUtils.buildNotification()
        NotificationGroup notifGroup = NotificationGroup.tryCreate([notif1, notif2]).payload

        when:
        Result res = DehydratedNotificationGroup.tryCreate(null)

        then:
        res.status == ResultStatus.INTERNAL_SERVER_ERROR

        when:
        res = DehydratedNotificationGroup.tryCreate(notifGroup)

        then:
        res.status == ResultStatus.CREATED
        notif1.itemIds.every { it in res.payload.itemIds }
        notif2.itemIds.every { it in res.payload.itemIds }

        when:
        res = res.payload.tryRehydrate()

        then:
        res.status == ResultStatus.CREATED
        res.payload.notifications.size() == 2
        res.payload.notifications.find {
            it.mutablePhone == notif1.mutablePhone && notif1.details.every { it2 -> it2 in it.details }
        }
        res.payload.notifications.find {
            it.mutablePhone == notif2.mutablePhone && notif2.details.every { it2 -> it2 in it.details }
        }
    }
}
