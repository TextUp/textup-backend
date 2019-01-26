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
@TestFor(PasswordResetService)
class PasswordResetServiceSpec extends CustomSpec {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        setupData()
        service.resultFactory = TestUtils.getResultFactory(grailsApplication)
    }

    def cleanup() {
        cleanupData()
    }

    void "test starting password reset"() {
        given:
        service.tokenService = Mock(TokenService)
        service.mailService = Mock(MailService)

        when: "username is null"
        Result<Void> res = service.start(null)

        then:
        0 * service.tokenService._
        0 * service.mailService._
        res.status == ResultStatus.NOT_FOUND
        res.errorMessages[0] == "passwordResetService.start.staffNotFound"

        when: "username is nonexistent"
        res = service.start(TestUtils.randString())

        then:
        0 * service.tokenService._
        0 * service.mailService._
        res.status == ResultStatus.NOT_FOUND
        res.errorMessages[0] == "passwordResetService.start.staffNotFound"

        when:
        res = service.start(s1.username)

        then:
        1 * service.tokenService.generatePasswordReset(*_) >> new Result(payload: [token: "hi"] as Token)
        1 * service.mailService.notifyPasswordReset(*_) >> new Result()
        res.status == ResultStatus.OK
    }

    void "test finishing password reset"() {
        given:
        service.tokenService = Mock(TokenService)

        when:
        Result<Staff> res = service.finish("token", "password")

        then:
        1 * service.tokenService.findPasswordResetStaff(*_) >> new Result(payload: s1)
        res.status == ResultStatus.OK
        res.payload == s1
    }
}
