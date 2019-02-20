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
class PasswordResetServiceSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test starting password reset"() {
        given:
        Staff s1 = TestUtils.buildStaff()
        Token mockToken = GroovyMock()

        service.tokenService = GroovyMock(TokenService)
        service.mailService = GroovyMock(MailService)

        when: "username is null"
        Result res = service.start(null)

        then:
        res.status == ResultStatus.NOT_FOUND

        when: "username is nonexistent"
        res = service.start(s1.username)

        then:
        1 * service.tokenService.generatePasswordReset(s1.id) >> Result.createSuccess(mockToken)
        1 * service.mailService.notifyPasswordReset(s1, mockToken) >> Result.void()
        res.status == ResultStatus.NO_CONTENT
    }

    void "test finishing password reset"() {
        given:
        String token = TestUtils.randString()
        String password = TestUtils.randString()

        Staff s1 = TestUtils.buildStaff()

        service.tokenService = GroovyMock(TokenService)

        when:
        Result res = service.finish(token, password)

        then:
        1 * service.tokenService.findPasswordResetStaff(token) >> Result.createSuccess(s1)
        res.status == ResultStatus.OK
        res.payload == s1
        s1.password == password
    }
}
