package org.textup

import grails.test.mixin.*
import grails.test.mixin.gorm.*
import grails.test.mixin.hibernate.*
import grails.test.mixin.support.*
import grails.test.runtime.*
import grails.validation.*
import org.joda.time.*
import org.textup.cache.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*
import spock.lang.*

@Domain([AnnouncementReceipt, ContactNumber, CustomAccountDetails, FeaturedAnnouncement,
    FutureMessage, GroupPhoneRecord, IncomingSession, IndividualPhoneRecord, Location, MediaElement,
    MediaElementVersion, MediaInfo, Organization, OwnerPolicy, Phone, PhoneNumberHistory,
    PhoneOwnership, PhoneRecord, PhoneRecordMembers, Record, RecordCall, RecordItem,
    RecordItemReceipt, RecordNote, RecordNoteRevision, RecordText, Role, Schedule,
    SimpleFutureMessage, Staff, StaffRole, Team, Token])
@TestFor(StaffService)
@TestMixin(HibernateTestMixin)
class StaffServiceSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
        phoneCache(PhoneCache)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test updating phone"() {
        given:
        String tz = TestUtils.randString()
        TypeMap pInfo = TestUtils.randTypeMap()

        Organization org1 = TestUtils.buildOrg()
        Staff s1 = TestUtils.buildStaff(org1)
        Phone p1 = TestUtils.buildStaffPhone()

        service.phoneService = GroovyMock(PhoneService)
        MockedMethod isAllowed = MockedMethod.create(Staffs, "isAllowed") { Long sId ->
            Result.createSuccess(sId)
        }

        when:
        Result res = service.tryUpdatePhone(null, null, null)

        then:
        isAllowed.notCalled
        res.status == ResultStatus.NO_CONTENT

        when:
        res = service.tryUpdatePhone(s1, pInfo, tz)

        then:
        isAllowed.latestArgs == [s1.id]
        1 * service.phoneService.tryFindAnyIdOrCreateImmediatelyForOwner(s1.id, PhoneOwnershipType.INDIVIDUAL) >>
            Result.createSuccess(p1.id)
        1 * service.phoneService.tryUpdate(p1, pInfo, s1.id, tz) >> Result.void()
        res.status == ResultStatus.NO_CONTENT

        cleanup:
        isAllowed?.restore()
    }

    void "test updating status"() {
        given:
        Organization org1 = TestUtils.buildOrg()
        Staff s1 = TestUtils.buildStaff(org1)
        s1.status = StaffStatus.ADMIN
        Staff s2 = TestUtils.buildStaff(org1)

        MockedMethod tryGetAuthId = MockedMethod.create(AuthUtils, "tryGetAuthId") {
            Result.createSuccess(s1.id)
        }

        when:
        Result res = service.trySetStatus(null, null)

        then:
        tryGetAuthId.notCalled
        res.status == ResultStatus.OK
        res.payload == null

        when:
        res = service.trySetStatus(s1, s1.status)

        then: "same status also short circuits"
        tryGetAuthId.notCalled
        res.status == ResultStatus.OK
        res.payload == s1

        when:
        res = service.trySetStatus(s1, StaffStatus.PENDING)

        then:
        tryGetAuthId.hasBeenCalled
        res.status == ResultStatus.FORBIDDEN
        res.errorMessages == ["staffService.lastAdmin"]

        when:
        res = service.trySetStatus(s2, StaffStatus.PENDING)

        then:
        tryGetAuthId.hasBeenCalled
        res.status == ResultStatus.OK
        res.payload == s2
        s2.status == StaffStatus.PENDING

        cleanup:
        tryGetAuthId?.restore()
    }

    void "test updating lock code"() {
        given:
        String invalidCode = TestUtils.randString()
        String validCode = "1234"
        Staff s1 = TestUtils.buildStaff()

        when:
        Result res = service.trySetLockCode(s1, null)

        then:
        res.status == ResultStatus.OK
        res.payload == s1

        when:
        res = service.trySetLockCode(s1, invalidCode)

        then:
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages == ["staffService.lockCodeFormat"]

        when:
        res = service.trySetLockCode(s1, validCode)

        then:
        res.status == ResultStatus.OK
        res.payload == s1
        s1.lockCode == validCode
    }

    void "test updating fields"() {
        given:
        TypeMap body = TypeMap.create(name: TestUtils.randString(),
            username: TestUtils.randString(),
            password: TestUtils.randString(),
            email: TestUtils.randEmail(),
            personalNumber: TestUtils.randPhoneNumberString())
        Staff s1 = TestUtils.buildStaff()

        when:
        Result res = service.trySetFields(s1, body)

        then:
        res.status == ResultStatus.OK
        res.payload == s1
        s1.name == body.name
        s1.username == body.username
        s1.password == body.password
        s1.email == body.email
        s1.personalNumber == PhoneNumber.create(body.personalNumber)
    }

    void "test finishing update"() {
        given:
        Staff s1 = TestUtils.buildStaff()
        s1.status = StaffStatus.PENDING
        Staff s2 = TestUtils.buildStaff()
        s2.status = StaffStatus.PENDING
        Staff s3 = TestUtils.buildStaff()
        s3.status = StaffStatus.STAFF

        Staff.withSession { it.flush() }

        service.mailService = GroovyMock(MailService)

        when: "pending -> active"
        s1.status = StaffStatus.ADMIN
        Result res = service.finishUpdate(s1)

        then:
        1 * service.mailService.notifyApproval(s1) >> Result.void()
        res.status == ResultStatus.OK
        res.payload == s1

        when: "pending -> inactive"
        s2.status = StaffStatus.BLOCKED
        res = service.finishUpdate(s2)

        then:
        1 * service.mailService.notifyRejection(s2) >> Result.void()
        res.status == ResultStatus.OK
        res.payload == s2

        when: "stays not pending"
        s3.status = StaffStatus.ADMIN
        res = service.finishUpdate(s3)

        then:
        0 * service.mailService._
        res.status == ResultStatus.OK
        res.payload == s3
    }

    void "test finishing create"() {
        given:
        TypeMap body = TypeMap.create(password: TestUtils.randString(), lockCode: TestUtils.randString(), shouldAddToGeneralUpdatesList: true)

        Organization org1 = TestUtils.buildOrg()
        Staff authUser = TestUtils.buildStaff(org1)
        authUser.status = StaffStatus.ADMIN

        Staff s1 = TestUtils.buildStaff()
        s1.org.status = OrgStatus.PENDING
        Staff s2 = TestUtils.buildStaff(org1)
        s2.org.status = OrgStatus.APPROVED
        s2.status = StaffStatus.PENDING
        Staff s3 = TestUtils.buildStaff()
        s3.org.status = OrgStatus.APPROVED
        s3.status = StaffStatus.STAFF
        Staff s4 = TestUtils.buildStaff()
        s4.org.status = OrgStatus.APPROVED
        s4.status = StaffStatus.BLOCKED

        Staff.withSession { it.flush() }

        service.threadService = GroovyMock(ThreadService)
        service.mailService = GroovyMock(MailService)
        MockedMethod tryGetActiveAuthUser = MockedMethod.create(AuthUtils, "tryGetActiveAuthUser") {
            Result.createSuccess(authUser)
        }

        when:
        Result res = service.finishCreate(s1, body)

        then:
        1 * service.mailService.notifyAboutPendingOrg(s1.org) >> Result.void()
        0 * service.threadService.submit(*_)
        res.status == ResultStatus.CREATED
        res.payload == s1

        when:
        res = service.finishCreate(s2, body)

        then:
        1 * service.mailService.notifyAboutPendingStaff(s2, [authUser]) >> Result.void()
        0 * service.threadService.submit(*_)
        res.status == ResultStatus.CREATED
        res.payload == s2

        when:
        res = service.finishCreate(s3, body)

        then:
        1 * service.mailService.notifyInvitation(authUser, s3, body.password, body.lockCode) >> Result.void()
        1 * service.threadService.submit(*_)
        res.status == ResultStatus.CREATED
        res.payload == s3

        when:
        res = service.finishCreate(s4, body)

        then:
        0 * service.mailService._
        0 * service.threadService.submit(*_)
        res.status == ResultStatus.CREATED
        res.payload == s4

        cleanup:
        tryGetActiveAuthUser?.restore()
    }

    void "test correct information sent to marketing mail service"() {
        given:
        TypeMap body = TypeMap.create(password: TestUtils.randString(), lockCode: TestUtils.randString(), shouldAddToGeneralUpdatesList: true)

        Organization org1 = TestUtils.buildOrg()
        Staff authUser = TestUtils.buildStaff(org1)
        authUser.status = StaffStatus.ADMIN
        Staff s1 = TestUtils.buildStaff()
        s1.org.status = OrgStatus.APPROVED
        s1.status = StaffStatus.STAFF
        Staff.withSession { it.flush() }

        service.mailService = GroovyMock(MailService)
        service.marketingMailService = GroovyMock(MarketingMailService)
        service.threadService = GroovyMock(ThreadService)
        MockedMethod tryGetActiveAuthUser = MockedMethod.create(AuthUtils, "tryGetActiveAuthUser") {
            Result.createSuccess(authUser)
        }

        when: "user added with `shouldAddToGeneralUpdatesList` true"
        Result res = service.finishCreate(s1, body)

        then: "both `addEmailToGeneralUpdatesList` and `addEmailToUsersList` are called"
        service.mailService.notifyInvitation(*_) >> Result.void()
        1 * service.threadService.submit(*_) >> { arguments -> arguments[0].call(); null; }
        1 * service.marketingMailService.addEmailToGeneralUpdatesList(s1.email) >> Result.void()
        1 * service.marketingMailService.addEmailToUsersList(s1.email) >> Result.void()

        when: "user added with `shouldAddToGeneralUpdatesList` false"
        body.shouldAddToGeneralUpdatesList = false
        res = service.finishCreate(s1, body)

        then: "only `addEmailToUsersList` is called"
        service.mailService.notifyInvitation(*_) >> Result.void()
        1 * service.threadService.submit(*_) >> { arguments -> arguments[0].call(); null; }
        0 * service.marketingMailService.addEmailToGeneralUpdatesList(*_)
        1 * service.marketingMailService.addEmailToUsersList(s1.email) >> Result.void()

        cleanup:
        tryGetActiveAuthUser?.restore()
    }

    void "test updating overall"() {
        given:
        TypeMap body = TypeMap.create(lockCode: TestUtils.randString(),
            status: StaffStatus.ADMIN,
            phone: TestUtils.randTypeMap(),
            timezone: TestUtils.randString())
        Staff s1 = TestUtils.buildStaff()

        MockedMethod trySetFields = MockedMethod.create(service, "trySetFields") {
            Result.createSuccess(s1)
        }
        MockedMethod trySetLockCode = MockedMethod.create(service, "trySetLockCode") {
            Result.createSuccess(s1)
        }
        MockedMethod trySetStatus = MockedMethod.create(service, "trySetStatus") {
            Result.createSuccess(s1)
        }
        MockedMethod tryUpdatePhone = MockedMethod.create(service, "tryUpdatePhone") {
            Result.createSuccess(s1)
        }
        MockedMethod finishUpdate = MockedMethod.create(service, "finishUpdate") {
            Result.createSuccess(s1)
        }

        when:
        Result res = service.tryUpdate(s1.id, body)

        then:
        trySetFields.latestArgs == [s1, body]
        trySetLockCode.latestArgs == [s1, body.lockCode]
        trySetStatus.latestArgs == [s1, body.status]
        tryUpdatePhone.latestArgs == [s1, body.phone, body.timezone]
        finishUpdate.latestArgs == [s1]
        res.status == ResultStatus.OK
        res.payload == s1

        cleanup:
        trySetFields?.restore()
        trySetLockCode?.restore()
        trySetStatus?.restore()
        tryUpdatePhone?.restore()
        finishUpdate?.restore()
    }

    void "test creating overall"() {
        given:
        TypeMap body = TypeMap.create(org: TestUtils.randTypeMap(),
            name: TestUtils.randString(),
            username: TestUtils.randString(),
            password: TestUtils.randString(),
            email: TestUtils.randEmail(),
            lockCode: TestUtils.randString(),
            status: StaffStatus.ADMIN,
            phone: TestUtils.randTypeMap(),
            timezone: TestUtils.randString())

        Organization org1 = TestUtils.buildOrg()

        int srBaseline = StaffRole.count()
        int sBaseline = Staff.count()

        service.organizationService = GroovyMock(OrganizationService)
        MockedMethod trySetFields = MockedMethod.create(service, "trySetFields") { Staff s1 ->
            Result.createSuccess(s1)
        }
        MockedMethod trySetLockCode = MockedMethod.create(service, "trySetLockCode") { Staff s1 ->
            Result.createSuccess(s1)
        }
        MockedMethod trySetStatus = MockedMethod.create(service, "trySetStatus") { Staff s1 ->
            Result.createSuccess(s1)
        }
        MockedMethod tryUpdatePhone = MockedMethod.create(service, "tryUpdatePhone") { Staff s1 ->
            Result.createSuccess(s1)
        }
        MockedMethod finishCreate = MockedMethod.create(service, "finishCreate") { Staff s1 ->
            Result.createSuccess(s1)
        }

        when:
        Result res = service.tryCreate(body)

        then:
        1 * service.organizationService.tryFindOrCreate(body.org) >> Result.createSuccess(org1)
        trySetFields.latestArgs[1] == body
        trySetLockCode.latestArgs[1] == body.lockCode
        trySetStatus.latestArgs[1] == body.status
        tryUpdatePhone.latestArgs[1] == body.phone
        tryUpdatePhone.latestArgs[2] == body.timezone
        finishCreate.latestArgs[1] == body
        res.status == ResultStatus.OK
        res.payload instanceof Staff
        res.payload.org == org1
        res.payload.name == body.name
        res.payload.username == body.username
        res.payload.password == body.password
        res.payload.email == body.email
        StaffRole.count() == srBaseline + 1
        Staff.count() == sBaseline + 1

        cleanup:
        trySetFields?.restore()
        trySetLockCode?.restore()
        trySetStatus?.restore()
        tryUpdatePhone?.restore()
        finishCreate?.restore()
    }
}
