package org.textup.util

import grails.test.mixin.*
import grails.test.mixin.gorm.*
import grails.test.mixin.hibernate.*
import grails.test.mixin.web.ControllerUnitTestMixin
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
@TestFor(MailService)
@TestMixin([HibernateTestMixin, ControllerUnitTestMixin])
class MailServiceSpec extends Specification {

	static doWithSpring = {
        resultFactory(ResultFactory)
    }

    MockedMethod send

    def setup() {
        TestUtils.standardMockSetup()
        send = MockedMethod.create(MailUtils, "send") { Result.void() }
    }

    def cleanup() {
        send?.restore()
    }

    void "test notify staff of invitation"() {
        given:
        Staff invitedBy = TestUtils.buildStaff()
        Staff invited = TestUtils.buildStaff()
        String pwd = TestUtils.randString()
        String lockCode = TestUtils.randString()

        when:
        Result res = service.notifyInvitation(null, null, null, null)

        then:
        res.status == ResultStatus.UNPROCESSABLE_ENTITY

        when:
        res = service.notifyInvitation(invitedBy, invited, pwd, lockCode)

        then:
        send.callCount == 1
        send.latestArgs[1].name == invited.name
        send.latestArgs[1].email == invited.email
        send.latestArgs[0] == MailUtils.defaultFromEntity()
        send.latestArgs[2] != null
        send.latestArgs[3].inviter == invitedBy.name
        send.latestArgs[3].invitee == invited.name
        send.latestArgs[3].username == invited.username
        send.latestArgs[3].password == pwd
        send.latestArgs[3].lockCode == lockCode
        send.latestArgs[3].link != null
        res.status == ResultStatus.NO_CONTENT
    }

    void "test notify staff of approval by admin"() {
        given:
        Staff s1 = TestUtils.buildStaff()

        when:
        Result res = service.notifyApproval(null)

        then:
        res.status == ResultStatus.UNPROCESSABLE_ENTITY

        when:
        res = service.notifyApproval(s1)

        then:
        send.callCount == 1
        send.latestArgs[1].name == s1.name
        send.latestArgs[1].email == s1.email
        send.latestArgs[0] == MailUtils.defaultFromEntity()
        send.latestArgs[2] != null
        send.latestArgs[3].name == s1.name
        send.latestArgs[3].username == s1.username
        send.latestArgs[3].org == s1.org.name
        send.latestArgs[3].link != null
        res.status == ResultStatus.NO_CONTENT
    }

    void "test notify admins of pending staff"() {
        given:
        Staff pendingStaff = TestUtils.buildStaff()
        Collection admins = [TestUtils.buildStaff(), TestUtils.buildStaff()]

        when:
        Result res = service.notifyAboutPendingStaff(null, null)

        then:
        res.status == ResultStatus.UNPROCESSABLE_ENTITY

        when:
        res = service.notifyAboutPendingStaff(pendingStaff, admins)

        then:
        send.callCount == admins.size()
        send.callArgs.every { it[1].name in admins*.name && it[1].email in admins*.email }
        send.callArgs.every { it[0] == MailUtils.defaultFromEntity() }
        send.callArgs.every { it[2] != null }
        send.callArgs.every { it[3].staff == pendingStaff.name }
        send.callArgs.every { it[3].org == pendingStaff.org.name }
        send.callArgs.every { it[3].link != null }
        res.status == ResultStatus.NO_CONTENT
    }

    void "test notify super about pending org"() {
        given:
        Organization org1 = TestUtils.buildOrg()

        when:
        Result res = service.notifyAboutPendingOrg(null)

        then:
        res.status == ResultStatus.UNPROCESSABLE_ENTITY

        when:
        res = service.notifyAboutPendingOrg(org1)

        then:
        send.callCount == 1
        send.latestArgs[1] == MailUtils.selfEntity()
        send.latestArgs[0] == MailUtils.defaultFromEntity()
        send.latestArgs[2] != null
        send.latestArgs[3].org == org1.name
        send.latestArgs[3].link != null
        res.status == ResultStatus.NO_CONTENT
    }

    void "test notify staff of rejection"() {
        given:
        Staff s1 = TestUtils.buildStaff()

        when:
        Result res = service.notifyRejection(null)

        then:
        res.status == ResultStatus.UNPROCESSABLE_ENTITY

        when:
        res = service.notifyRejection(s1)

        then:
        send.callCount == 1
        send.latestArgs[1].name == s1.name
        send.latestArgs[1].email == s1.email
        send.latestArgs[0] == MailUtils.defaultFromEntity()
        send.latestArgs[2] != null
        send.latestArgs[3].name == s1.name
        send.latestArgs[3].username == s1.username
        res.status == ResultStatus.NO_CONTENT
    }

    void "test notify staff of password reset"() {
        given:
        Staff s1 = TestUtils.buildStaff()
        Token tok1 = TestUtils.buildToken()

        when:
        Result res = service.notifyPasswordReset(null, null)

        then:
        res.status == ResultStatus.UNPROCESSABLE_ENTITY

        when:
        res = service.notifyPasswordReset(s1, tok1)

        then:
        send.callCount == 1
        send.latestArgs[1].name == s1.name
        send.latestArgs[1].email == s1.email
        send.latestArgs[0] == MailUtils.defaultFromEntity()
        send.latestArgs[2] != null
        send.latestArgs[3].name == s1.name
        send.latestArgs[3].username == s1.username
        send.latestArgs[3].link.contains(tok1.token)
        res.status == ResultStatus.NO_CONTENT
    }

    void "test send staff digest email notification"() {
        given:
        NotificationFrequency freq1 = NotificationFrequency.values()[0]
        Staff s1 = TestUtils.buildStaff()
        NotificationInfo notifInfo = TestUtils.buildNotificationInfo()
        Token tok1 = TestUtils.buildToken()

        when:
        Result res = service.notifyMessages(null, null, null)

        then:
        res.status == ResultStatus.UNPROCESSABLE_ENTITY

        when:
        res = service.notifyMessages(freq1, s1, notifInfo)

        then:
        send.callCount == 1
        send.latestArgs[1].name == s1.name
        send.latestArgs[1].email == s1.email
        send.latestArgs[0] == MailUtils.defaultFromEntity()
        send.latestArgs[2] != null
        send.latestArgs[3].staffName == s1.name
        send.latestArgs[3].phoneName == notifInfo.phoneName
        send.latestArgs[3].phoneNumber == notifInfo.phoneNumber.prettyPhoneNumber
        send.latestArgs[3].timePeriodDescription == freq1.readableDescription
        send.latestArgs[3].incomingDescription instanceof String
        send.latestArgs[3].outgoingDescription instanceof String
        send.latestArgs[3].numIncoming == notifInfo.numIncomingText + notifInfo.numIncomingCall
        send.latestArgs[3].numOutgoing == notifInfo.numOutgoingText + notifInfo.numOutgoingCall
        send.latestArgs[3].link == null
        res.status == ResultStatus.NO_CONTENT

        when:
        res = service.notifyMessages(freq1, s1, notifInfo, tok1)

        then:
        send.callCount == 2
        send.latestArgs[1].name == s1.name
        send.latestArgs[1].email == s1.email
        send.latestArgs[0] == MailUtils.defaultFromEntity()
        send.latestArgs[2] != null
        send.latestArgs[3].staffName == s1.name
        send.latestArgs[3].phoneName == notifInfo.phoneName
        send.latestArgs[3].phoneNumber == notifInfo.phoneNumber.prettyPhoneNumber
        send.latestArgs[3].timePeriodDescription == freq1.readableDescription
        send.latestArgs[3].incomingDescription instanceof String
        send.latestArgs[3].outgoingDescription instanceof String
        send.latestArgs[3].numIncoming == notifInfo.numIncomingText + notifInfo.numIncomingCall
        send.latestArgs[3].numOutgoing == notifInfo.numOutgoingText + notifInfo.numOutgoingCall
        send.latestArgs[3].link.contains(tok1.token)
        res.status == ResultStatus.NO_CONTENT
    }
}
