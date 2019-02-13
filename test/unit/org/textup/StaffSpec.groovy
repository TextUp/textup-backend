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
@Unroll
class StaffSpec extends Specification {

	static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test constraints"() {
        given:
        String encoded = TestUtils.randString()
        MockedMethod encodeSecureString = MockedMethod.force(AuthUtils, "encodeSecureString") { encoded }

        Organization org1 = TestUtils.buildOrg()
        Role role1 = TestUtils.buildRole()
        String name = TestUtils.randString()
        String un1 = TestUtils.randString()
        String pwd = TestUtils.randString()
        String email = TestUtils.randEmail()

        when:
        Result res = Staff.tryCreate(null, null, null, null, null, null)

        then:
        res.status == ResultStatus.UNPROCESSABLE_ENTITY

        when:
        res = Staff.tryCreate(role1, org1, name, un1, pwd, email)

        then:
        res.status == ResultStatus.CREATED
        res.payload.org == org1
        res.payload.name == name
        res.payload.username == un1
        res.payload.password != pwd
        res.payload.password == encoded
        res.payload.email == email
        encodeSecureString.callCount > 0

        cleanup:
        encodeSecureString.restore()
    }

    void "test creation"() {
        given:
        boolean shouldBeNew = true
        MockedMethod isNew = MockedMethod.create(DomainUtils, "isNew") { shouldBeNew }

        Organization org1 = TestUtils.buildOrg()
        Role role1 = TestUtils.buildRole()
        String name = TestUtils.randString()
        String un1 = TestUtils.randString()
        String un2 = TestUtils.randString()
        String pwd = TestUtils.randString()
        String email = TestUtils.randEmail()

        int srBaseline = StaffRole.count()

        when: "is new"
        shouldBeNew = true
        Result res = Staff.tryCreate(role1, org1, name, un1, pwd, email)
        Staff.withSession { it.flush() }

        then:
        res.status == ResultStatus.CREATED
        res.payload.status == StaffStatus.ADMIN
        res.payload.authorities.size() == 1
        res.payload.authorities[0].authority == role1.authority
        isNew.callCount == 1
        StaffRole.count() == srBaseline + 1

        when: "is not new"
        shouldBeNew = false
        res = Staff.tryCreate(role1, org1, name, un2, pwd, email)
        Staff.withSession { it.flush() }

        then:
        res.status == ResultStatus.CREATED
        res.payload.status == StaffStatus.PENDING
        res.payload.authorities.size() == 1
        res.payload.authorities[0].authority == role1.authority
        isNew.callCount == 2
        StaffRole.count() == srBaseline + 2

        cleanup:
        isNew.restore()
    }

    void "test lockCode"() {
        given:
        Staff s1 = TestUtils.buildStaff()

        when: "lock code is empty"
        s1.lockCode = ""

        then:
        s1.validate() == false
        s1.errors.errorCount == 1
        s1.errors.getFieldErrorCount("lockCode") == 1

        when: "lock code is of varying length"
        s1.lockCode = "12"

        then: "still okay, we enforce condition on service"
        s1.validate() == true

        when: "lock code is default value"
        s1.lockCode = Constants.DEFAULT_LOCK_CODE

        then: "valid"
        s1.validate() == true
    }

    void "test valid username"() {
        given:
        Staff s1 = TestUtils.buildStaff()

        when:
        s1.username = null

        then:
        !s1.validate()

        when:
        s1.username = ""

        then:
        !s1.validate()

        when:
        s1.username = "kiki bai"

        then:
        !s1.validate()

        when:
        s1.username = "kiki!!!123--hi"

        then:
        !s1.validate()

        when:
        s1.username = "kikiBAI_-=@,.;"

        then:
        s1.validate()
    }
}
