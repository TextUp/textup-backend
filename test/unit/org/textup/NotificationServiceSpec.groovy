package org.textup

import grails.test.mixin.*
import grails.test.mixin.gorm.*
import grails.test.mixin.hibernate.*
import grails.test.mixin.support.*
import grails.test.mixin.web.ControllerUnitTestMixin
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
@TestMixin([HibernateTestMixin, ControllerUnitTestMixin])
@TestFor(NotificationService)
class NotificationServiceSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test redeeming"() {
        given:
        String str1 = TestUtils.randString()
        Long staffId = TestUtils.randIntegerUpTo(88)
        Notification notif1 = TestUtils.buildNotification()

        service.tokenService = GroovyMock(TokenService)

        when:
        Result res = service.redeem(str1)

        then:
        1 * service.tokenService.tryFindPreviewInfo(str1) >> Result.createSuccess(Tuple.create(staffId, notif1))
        RequestUtils.tryGet(RequestUtils.STAFF_ID).payload == staffId
        res.status == ResultStatus.OK
        res.payload == notif1
    }

    void "test sending"() {
        given:
        NotificationFrequency freq1 = NotificationFrequency.values()[0]
        Notification notif1 = TestUtils.buildNotification()
        OwnerPolicy op1 = TestUtils.buildOwnerPolicy()
        op1.method = NotificationMethod.TEXT
        OwnerPolicy op2 = TestUtils.buildOwnerPolicy()
        op2.method = NotificationMethod.EMAIL
        OwnerPolicy.withSession { it.flush() }

        Token tok1 = GroovyMock()
        NotificationGroup notifGroup1 = GroovyMock()
        service.mailService = GroovyMock(MailService)
        service.textService = GroovyMock(TextService)
        service.tokenService = GroovyMock(TokenService)

        when:
        Result res = service.send(null, null)

        then:
        notThrown NullPointerException
        res.status == ResultStatus.NO_CONTENT

        when: "send via text"
        res = service.send(freq1, notifGroup1)

        then:
        1 * notifGroup1.eachNotification(freq1, _ as Closure) >> { args -> args[1].call(op1, notif1) }
        1 * service.tokenService.tryGeneratePreviewInfo(op1, notif1) >> Result.createSuccess(tok1)
        1 * service.textService.send(notif1.mutablePhone.number,
            [op1.staff.personalNumber],
            _ as String,
            notif1.mutablePhone.customAccountId) >> Result.void()
        res.status == ResultStatus.NO_CONTENT

        when: "send via email"
        res = service.send(freq1, notifGroup1)

        then:
        1 * notifGroup1.eachNotification(freq1, _ as Closure) >> { args -> args[1].call(op2, notif1) }
        1 * service.tokenService.tryGeneratePreviewInfo(op2, notif1) >> Result.createSuccess(tok1)
        1 * service.mailService.notifyMessages(freq1,
            op2.staff,
            _ as NotificationInfo,
            tok1) >> Result.void()
        res.status == ResultStatus.NO_CONTENT
    }
}
