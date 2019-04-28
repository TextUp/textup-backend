package org.textup

import grails.plugin.springsecurity.SpringSecurityService
import grails.test.mixin.*
import grails.test.mixin.gorm.*
import grails.test.mixin.hibernate.*
import grails.test.mixin.support.*
import grails.test.runtime.*
import grails.validation.*
import javax.servlet.http.*
import org.joda.time.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*
import spock.lang.*

@TestFor(SuperController)
@Domain([AnnouncementReceipt, ContactNumber, CustomAccountDetails, FeaturedAnnouncement,
    FutureMessage, GroupPhoneRecord, IncomingSession, IndividualPhoneRecord, Location, MediaElement,
    MediaElementVersion, MediaInfo, Organization, OwnerPolicy, Phone, PhoneNumberHistory,
    PhoneOwnership, PhoneRecord, PhoneRecordMembers, Record, RecordCall, RecordItem,
    RecordItemReceipt, RecordNote, RecordNoteRevision, RecordText, Role, Schedule,
    SimpleFutureMessage, Staff, StaffRole, Team, Token])
@TestMixin(HibernateTestMixin)
class SuperControllerSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test approve org"() {
        given:
        Organization org1 = TestUtils.buildOrg(OrgStatus.PENDING)
        Staff s1 = TestUtils.buildStaff(org1)
        s1.status = StaffStatus.ADMIN
        s1.save(flush: true, failOnError: true)

        controller.mailService = GroovyMock(MailService)

        when:
        params.id = "nonexistent"
        controller.approveOrg()

        then:
        org1.status != OrgStatus.APPROVED
        response.redirectUrl == "/super/index"
        flash.messages != null

        when:
        response.reset()
        params.id = org1.id
        controller.approveOrg()

        then:
        1 * controller.mailService.notifyApproval(s1) >> Result.void()
        org1.status == OrgStatus.APPROVED
        response.redirectUrl == "/super/index"
        flash.messages != null
    }

    void "test reject org"() {
        given:
        Organization org1 = TestUtils.buildOrg(OrgStatus.PENDING)
        Staff s1 = TestUtils.buildStaff(org1)
        s1.status = StaffStatus.ADMIN
        s1.save(flush: true, failOnError: true)

        controller.mailService = GroovyMock(MailService)

        when:
        params.id = "nonexistent"
        controller.rejectOrg()

        then:
        org1.status != OrgStatus.REJECTED
        response.redirectUrl == "/super/index"
        flash.messages != null

        when:
        response.reset()
        params.id = org1.id
        controller.rejectOrg()

        then:
        1 * controller.mailService.notifyRejection(s1) >> Result.void()
        org1.status == OrgStatus.REJECTED
        response.redirectUrl == "/super/index"
        flash.messages != null
    }

    void "test updating password errors"() {
        given:
        String pwd1 = TestUtils.randString()
        String pwd2 = TestUtils.randString()
        Staff s1 = TestUtils.buildStaff()

        SpringSecurityService securityService = GroovyStub() { getCurrentUser() >> s1 }
        MockedMethod getSecurity = MockedMethod.create(IOCUtils, "getSecurity") { securityService }
        MockedMethod isSecureStringValid = MockedMethod.create(AuthUtils, "isSecureStringValid") {
            false
        }

        when: "passwords do not match"
        response.reset()
        params.newPassword = pwd1
        params.confirmNewPassword = pwd2
        controller.updateSettings()

        then:
        response.redirectUrl == "/super/settings"
        flash.messages.size() == 1
        flash.messages[0].contains("New passwords must match")

        when: "missing current password"
        response.reset()
        params.newPassword = pwd1
        params.confirmNewPassword = pwd1
        params.currentPassword = null
        controller.updateSettings()

        then:
        response.redirectUrl == "/super/settings"
        flash.messages instanceof List
        flash.messages.size() == 1
        flash.messages[0].contains("Current password is either blank or incorrect")

        when: "incorrect current password"
        response.reset()
        params.newPassword = pwd1
        params.confirmNewPassword = pwd1
        params.currentPassword = pwd2
        controller.updateSettings()

        then:
        isSecureStringValid.latestArgs == [s1.password, pwd2]
        response.redirectUrl == "/super/settings"
        flash.messages instanceof List
        flash.messages.size() == 1
        flash.messages[0].contains("Current password is either blank or incorrect")

        cleanup:
        getSecurity?.restore()
        isSecureStringValid?.restore()
    }

    void "test updating staff properties"() {
        given:
        String pwd1 = TestUtils.randString()
        String pwd2 = TestUtils.randString()
        String un1 = TestUtils.randString()

        Staff s1 = TestUtils.buildStaff()
        String un2 = s1.username

        SpringSecurityService securityService = GroovyMock() { getCurrentUser() >> s1 }
        MockedMethod getSecurity = MockedMethod.create(IOCUtils, "getSecurity") { securityService }
        MockedMethod isSecureStringValid = MockedMethod.create(AuthUtils, "isSecureStringValid") {
            true
        }

        when: "no inputs"
        controller.updateSettings()

        then:
        response.redirectUrl == "/super/settings"
        flash.messages instanceof List
        flash.messages.size() == 1
        flash.messages[0].contains("Successfully updated settings")

        when:
        response.reset()
        params.newPassword = pwd1
        params.confirmNewPassword = pwd1
        params.currentPassword = pwd2
        params.username = un1
        controller.updateSettings()

        then:
        1 * securityService.reauthenticate(un2)
        s1.username == un1
        s1.password == pwd1
        response.redirectUrl == "/super/settings"
        flash.messages.size() == 1
        flash.messages[0].contains("Successfully updated settings")

        cleanup:
        getSecurity?.restore()
        isSecureStringValid?.restore()
    }
}
