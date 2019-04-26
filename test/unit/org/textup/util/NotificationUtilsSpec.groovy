package org.textup.util

import grails.test.mixin.*
import grails.test.mixin.gorm.*
import grails.test.mixin.hibernate.*
import grails.test.mixin.support.*
import org.textup.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
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
class NotificationUtilsSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test building outgoing message string"() {
        given:
        int numText = 1
        int numCall = 2
        String outgoingNames = TestUtils.randString()
        NotificationInfo nInfo = new NotificationInfo(null,
            null,
            0,
            0,
            0,
            null,
            numText,
            numCall,
            outgoingNames)

        when:
        String msg = NotificationUtils.buildOutgoingMessage(null)

        then:
        msg == ""

        when:
        msg = NotificationUtils.buildOutgoingMessage(nInfo)

        then:
        msg.contains("notificationInfo.scheduledText")
        msg.contains("notificationInfo.scheduledCalls") // pluralized
        msg.contains("notificationInfo.to")
        msg.contains(outgoingNames)
    }

    void "test building incoming message string"() {
        given:
        int numText = 1
        int numCall = 2
        int numVoicemail = 3
        String incomingNames = TestUtils.randString()
        NotificationInfo nInfo = new NotificationInfo(null,
            null,
            numVoicemail,
            numText,
            numCall,
            incomingNames,
            0,
            0,
            null)

        when:
        String msg = NotificationUtils.buildIncomingMessage(null)

        then:
        msg == ""

        when:
        msg = NotificationUtils.buildIncomingMessage(nInfo)

        then:
        msg.contains("notificationInfo.text")
        msg.contains("notificationInfo.calls") // pluralized
        msg.contains("notificationInfo.voicemails") // pluralized
        msg.contains("notificationInfo.from")
        msg.contains(incomingNames)
    }

    void "test building text message notification string"() {
        given:
        String phoneName = TestUtils.randString()
        int numIncomingText = 3
        int numOutgoingCall = 3
        NotificationInfo nInfo1 = new NotificationInfo(phoneName,
            null,
            0,
            numIncomingText,
            0,
            null,
            0,
            0,
            null)
        NotificationInfo nInfo2 = new NotificationInfo(phoneName,
            null,
            0,
            0,
            0,
            null,
            0,
            numOutgoingCall,
            null)
        NotificationInfo nInfo3 = new NotificationInfo(phoneName,
            null,
            0,
            numIncomingText,
            0,
            null,
            0,
            numOutgoingCall,
            null)

        when:
        String msg = NotificationUtils.buildTextMessage(null, null)

        then:
        msg == ""

        when: "only incoming"
        msg = NotificationUtils.buildTextMessage(null, nInfo1)

        then:
        msg.contains("notificationInfo.context")
        msg.contains("notificationInfo.received")
        msg.contains("notificationInfo.and") == false
        msg.contains("notificationInfo.sent") == false
        msg.contains("notificationInfo.text")
        msg.contains("notificationInfo.scheduledCall") == false

        when: "only outgoing"
        msg = NotificationUtils.buildTextMessage(null, nInfo2)

        then:
        msg.contains("notificationInfo.context")
        msg.contains("notificationInfo.received") == false
        msg.contains("notificationInfo.and") == false
        msg.contains("notificationInfo.sent")
        msg.contains("notificationInfo.text") == false
        msg.contains("notificationInfo.scheduledCall")

        when: "both incoming + outgoing"
        msg = NotificationUtils.buildTextMessage(null, nInfo3)

        then:
        msg.contains("notificationInfo.context")
        msg.contains("notificationInfo.received")
        msg.contains("notificationInfo.and")
        msg.contains("notificationInfo.sent")
        msg.contains("notificationInfo.text")
        msg.contains("notificationInfo.scheduledCall")
    }

    void "test building public names string for notification"() {
        given:
        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord()
        ipr1.name = "A B"
        GroupPhoneRecord gpr1 = TestUtils.buildGroupPhoneRecord()
        gpr1.name = "C D"
        PhoneRecord spr1 = TestUtils.buildSharedPhoneRecord()
        spr1.permission = SharePermission.NONE
        spr1.shareSource.name = "E F"
        PhoneRecord spr2 = TestUtils.buildSharedPhoneRecord()
        spr2.shareSource.name = "G H"

        Notification notif1 = GroovyMock()

        when:
        String names = NotificationUtils.buildPublicNames(null, true)

        then:
        names == ""

        when:
        names = NotificationUtils.buildPublicNames(notif1, true)

        then:
        1 * notif1.getWrappersForOutgoing(true) >> [ipr1, gpr1, spr1, spr2]*.toWrapper()
        names.contains("A.B.")
        names.contains("C.D.")
        names.contains("E.F.") == false
        names.contains("G.H.")
    }

    void "test building notifications for items"() {
        given:
        Phone p1 = TestUtils.buildActiveStaffPhone()
        Phone p2 = TestUtils.buildActiveStaffPhone()

        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord(p1)
        IndividualPhoneRecord ipr2 = TestUtils.buildIndPhoneRecord(p2)

        PhoneRecord spr1 = TestUtils.buildSharedPhoneRecord(ipr1, p2)
        PhoneRecord spr2 = TestUtils.buildSharedPhoneRecord(ipr2, p1)

        RecordItem rItem1 = TestUtils.buildRecordItem(ipr1.record)
        RecordItem rItem2 = TestUtils.buildRecordItem(ipr2.record)

        when:
        Result res = NotificationUtils.tryBuildNotificationsForItems(null)

        then:
        res.status == ResultStatus.OK
        res.payload == []

        when:
        res = NotificationUtils.tryBuildNotificationsForItems([rItem1, rItem2])

        then:
        res.status == ResultStatus.OK
        res.payload.size() == 2

        res.payload.find { it.mutablePhone == p1 }.details.size() == 2
        res.payload.find { it.mutablePhone == p1 }.details.find {
            it.wrapper == ipr1.toWrapper() && it.items.size() == 1 && rItem1 in it.items
        }
        res.payload.find { it.mutablePhone == p1 }.details.find {
            it.wrapper == spr2.toWrapper() && it.items.size() == 1 && rItem2 in it.items
        }

        res.payload.find { it.mutablePhone == p2 }.details.size() == 2
        res.payload.find { it.mutablePhone == p2 }.details.find {
            it.wrapper == ipr2.toWrapper() && it.items.size() == 1 && rItem2 in it.items
        }
        res.payload.find { it.mutablePhone == p2 }.details.find {
            it.wrapper == spr1.toWrapper() && it.items.size() == 1 && rItem1 in it.items
        }

        when:
        res = NotificationUtils.tryBuildNotificationsForItems([rItem1, rItem2], [p1.id])

        then:
        res.status == ResultStatus.OK
        res.payload.size() == 1
        res.payload[0].mutablePhone == p1
        res.payload[0].details.size() == 2
        res.payload[0].details.find {
            it.wrapper == ipr1.toWrapper() && it.items.size() == 1 && rItem1 in it.items
        }
        res.payload[0].details.find {
            it.wrapper == spr2.toWrapper() && it.items.size() == 1 && rItem2 in it.items
        }
    }

    void "test building notifications for items with some failures"() {
        given:
        Phone p1 = TestUtils.buildActiveStaffPhone()
        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord(p1)
        PhoneRecord spr1 = TestUtils.buildSharedPhoneRecord(null, p1)
        spr1.permission = SharePermission.VIEW

        RecordItem rItem1 = TestUtils.buildRecordItem(ipr1.record)
        RecordItem rItem2 = TestUtils.buildRecordItem(spr1.record)

        when:
        Result res = NotificationUtils.tryBuildNotificationsForItems([rItem1, rItem2])

        then: "allow some errors because we expect view-only to fail"
        res.status == ResultStatus.OK
        // one is the owned contact and the second is the sharedSource
        res.payload.size() == 2
        res.payload.find {
            it.mutablePhone == p1 &&
                it.details.size() == 1 &&
                it.details[0].wrapper == ipr1.toWrapper() &&
                it.details[0].items.size() == 1 &&
                it.details[0].items[0] == rItem1
        }
        res.payload.find {
            it.mutablePhone == spr1.shareSource.phone &&
                it.details.size() == 1 &&
                it.details[0].wrapper == spr1.shareSource.toWrapper() &&
                it.details[0].items.size() == 1 &&
                it.details[0].items[0] == rItem2
        }
    }

    void "test building notifications excludes non-visible `PhoneRecord`s"() {
        given:
        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord()
        ipr1.status = PhoneRecordStatus.BLOCKED

        RecordItem rItem1 = TestUtils.buildRecordItem(ipr1.record)

        when:
        Result res = NotificationUtils.tryBuildNotificationsForItems([rItem1])

        then: "non-visible (blocked) phone records are excluded"
        res.status == ResultStatus.OK
        res.payload.size() == 0
    }

    void "test building notification group"() {
        given:
        Phone tp1 = TestUtils.buildActiveTeamPhone()
        Notification notif1 = Notification.tryCreate(tp1).payload
        MockedMethod tryBuildNotificationsForItems = MockedMethod.create(NotificationUtils, "tryBuildNotificationsForItems") {
            Result.createSuccess([notif1])
        }
        RecordItem rItem1 = TestUtils.buildRecordItem()

        when:
        Result res = NotificationUtils.tryBuildNotificationGroup([rItem1])

        then:
        tryBuildNotificationsForItems.callCount == 1
        tryBuildNotificationsForItems.allArgs[0] == [[rItem1], null]
        res.status == ResultStatus.CREATED
        res.payload.notifications.size() == 1
        res.payload.notifications[0].mutablePhone == tp1
        res.payload.notifications[0].details.isEmpty()

        cleanup:
        tryBuildNotificationsForItems.restore()
    }
}
