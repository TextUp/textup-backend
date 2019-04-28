package org.textup.util

import grails.plugin.springsecurity.SpringSecurityService
import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestFor
import grails.test.mixin.TestMixin
import grails.validation.ValidationErrors
import org.joda.time.DateTime
import org.springframework.context.MessageSource
import org.springframework.security.authentication.dao.DaoAuthenticationProvider
import org.springframework.security.authentication.encoding.PasswordEncoder // deprecated
import org.springframework.security.core.Authentication
import org.springframework.security.core.userdetails.UserDetailsService
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
class AuthUtilsSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    SpringSecurityService mockSecurity

    def setup() {
        IOCUtils.metaClass."static".getMessageSource = { -> TestUtils.mockMessageSource() }

        mockSecurity = Mock(SpringSecurityService)
        IOCUtils.metaClass."static".getSecurity = { -> mockSecurity }
    }

    void "test determining if a staff is active"() {
        given:
        MockedMethod encodeSecureString = MockedMethod.create(AuthUtils, "encodeSecureString") { it }
        Staff s1 = TestUtils.buildStaff()

        when:
        s1.status = StaffStatus.PENDING
        s1.org.status = OrgStatus.APPROVED

        then:
        AuthUtils.isActive(s1) == false

        when:
        s1.status = StaffStatus.STAFF
        s1.org.status = OrgStatus.PENDING

        then:
        AuthUtils.isActive(s1) == false

        when: "both staff and org are active"
        s1.status = StaffStatus.STAFF
        s1.org.status = OrgStatus.APPROVED

        then:
        AuthUtils.isActive(s1)

        cleanup:
        encodeSecureString.restore()
    }

    void "test try getting auth id"() {
        given:
        MockedMethod encodeSecureString = MockedMethod.create(AuthUtils, "encodeSecureString") { it }
        Staff s1 = TestUtils.buildStaff()

        when:
        Result res = AuthUtils.tryGetAuthId()

        then:
        1 * mockSecurity.isLoggedIn() >> false
        res.status == ResultStatus.FORBIDDEN

        when:
        res = AuthUtils.tryGetAuthId()

        then:
        1 * mockSecurity.isLoggedIn() >> true
        1 * mockSecurity.loadCurrentUser() >> s1
        res.status == ResultStatus.OK
        res.payload == s1.id

        cleanup:
        encodeSecureString.restore()
    }

    void "try getting any and active auth user"() {
        given:
        MockedMethod encodeSecureString = MockedMethod.create(AuthUtils, "encodeSecureString") { it }
        Staff s1 = TestUtils.buildStaff()

        when:
        Result res1 = AuthUtils.tryGetAnyAuthUser()
        Result res2 = AuthUtils.tryGetActiveAuthUser()

        then:
        (1.._) * mockSecurity.isLoggedIn() >> false
        res1.status == ResultStatus.FORBIDDEN
        res2.status == ResultStatus.FORBIDDEN

        when:
        s1.status = StaffStatus.PENDING

        res1 = AuthUtils.tryGetAnyAuthUser()
        res2 = AuthUtils.tryGetActiveAuthUser()

        then:
        (1.._) * mockSecurity.isLoggedIn() >> true
        (1.._) * mockSecurity.loadCurrentUser() >> s1
        res1.status == ResultStatus.OK
        res1.payload == s1
        res2.status == ResultStatus.FORBIDDEN

        when:
        s1.status = StaffStatus.STAFF
        s1.org.status = OrgStatus.APPROVED

        res1 = AuthUtils.tryGetAnyAuthUser()
        res2 = AuthUtils.tryGetActiveAuthUser()

        then:
        (1.._) * mockSecurity.isLoggedIn() >> true
        (1.._) * mockSecurity.loadCurrentUser() >> s1
        res1.status == ResultStatus.OK
        res1.payload == s1
        res2.status == ResultStatus.OK
        res2.payload == s1

        cleanup:
        encodeSecureString.restore()
    }

    void "test validating credentials"() {
        given:
        UserDetailsService mockUserDetails = Mock()
        DaoAuthenticationProvider mockAuthProvider = Mock()
        IOCUtils.metaClass."static".getAuthProvider = { -> mockAuthProvider }
        Authentication mockAuth = Mock()

        String un = TestUtils.randString()
        String pwd = TestUtils.randString()

        when:
        boolean isValid = AuthUtils.isValidCredentials(un, pwd)

        then:
        1 * mockSecurity.userDetailsService >> mockUserDetails
        1 * mockUserDetails.loadUserByUsername(un)
        1 * mockAuthProvider.authenticate(*_) >> mockAuth
        1 * mockAuth.authenticated >> true
        isValid == true
    }

    void "test encoding and checking secure strings"() {
        given:
        PasswordEncoder mockEncoder = Mock()
        String val = TestUtils.randString()
        String output = TestUtils.randString()

        when:
        String encoded = AuthUtils.encodeSecureString(val)

        then:
        1 * mockSecurity.passwordEncoder >> mockEncoder
        1 * mockEncoder.encodePassword(val, null) >> output
        encoded == output

        when:
        boolean isValid = AuthUtils.isSecureStringValid(encoded, val)

        then:
        1 * mockSecurity.passwordEncoder >> mockEncoder
        1 * mockEncoder.isPasswordValid(encoded, val, null) >> true
        isValid == true
    }
}
