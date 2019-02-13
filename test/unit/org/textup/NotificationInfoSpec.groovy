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
class NotificationInfoSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test creation"() {
        given:
        Phone p1 = TestUtils.buildStaffPhone()
        Notification notif1 = Notification.tryCreate(p1).payload
        String randStr1 = TestUtils.randString()
        int randInt1 = TestUtils.randIntegerUpTo(88)
        int randInt2 = TestUtils.randIntegerUpTo(88)
        MockedMethod buildPublicNames = MockedMethod.create(NotificationUtils, "buildPublicNames") { randStr1 }
        MockedMethod countVoicemails = MockedMethod.create(notif1, "countVoicemails") { randInt1 }
        MockedMethod countItems = MockedMethod.create(notif1, "countItems") { randInt2 }
        ReadOnlyOwnerPolicy rop1 = GroovyMock() { asBoolean() >> true }

        when:
        NotificationInfo notifInfo = NotificationInfo.create(null, null)

        then:
        buildPublicNames.callCount == 0
        notifInfo.phoneName == null
        notifInfo.phoneNumber == null
        notifInfo.numVoicemail == 0
        notifInfo.numIncomingText == 0
        notifInfo.numIncomingCall == 0
        notifInfo.incomingNames == null
        notifInfo.numOutgoingText == 0
        notifInfo.numOutgoingCall == 0
        notifInfo.outgoingNames == null

        when:
        notifInfo = NotificationInfo.create(rop1, notif1)

        then:
        buildPublicNames.callCount == 2
        buildPublicNames.allArgs.find { it == [notif1, false] }
        buildPublicNames.allArgs.find { it == [notif1, true] }

        countVoicemails.callCount == 1
        countVoicemails.allArgs[0] == [rop1]

        countItems.callCount == 4
        countItems.allArgs.find { it == [false, rop1, RecordText] }
        countItems.allArgs.find { it == [false, rop1, RecordCall] }
        countItems.allArgs.find { it == [true, rop1, RecordText] }
        countItems.allArgs.find { it == [true, rop1, RecordCall] }

        notifInfo.phoneName == p1.buildName()
        notifInfo.phoneNumber.number == p1.number.number
        notifInfo.numVoicemail == randInt1
        notifInfo.numIncomingText == randInt2
        notifInfo.numIncomingCall == randInt2
        notifInfo.incomingNames == randStr1
        notifInfo.numOutgoingText == randInt2
        notifInfo.numOutgoingCall == randInt2
        notifInfo.outgoingNames == randStr1

        cleanup:
        buildPublicNames.restore()
        countVoicemails.restore()
        countItems.restore()
    }

    void "test building text message string"() {
        given:
        String randStr1 = TestUtils.randString()
        String randStr2 = TestUtils.randString()
        MockedMethod buildTextMessage = MockedMethod.create(NotificationUtils, "buildTextMessage") {
            randStr1
        }
        Token tok1 = GroovyStub() {
            getToken() >> randStr2
            asBoolean() >> true
        }
        NotificationInfo notifInfo = NotificationInfo.create(null, null)

        when:
        String msg = notifInfo.buildTextMessage()

        then:
        buildTextMessage.callCount == 1
        buildTextMessage.allArgs[0] == [notifInfo]
        msg.contains(randStr1)
        msg.contains(randStr2) == false
        msg.contains("notificationInfo.previewLink") == false

        when:
        msg = notifInfo.buildTextMessage(tok1)

        then:
        buildTextMessage.callCount == 2
        buildTextMessage.allArgs[1] == [notifInfo]
        msg.contains(randStr1)
        msg.contains(randStr2)
        msg.contains("notificationInfo.previewLink")

        cleanup:
        buildTextMessage.restore()
    }
}
