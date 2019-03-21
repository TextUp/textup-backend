package org.textup.action

import grails.test.mixin.*
import grails.test.mixin.gorm.*
import grails.test.mixin.hibernate.*
import org.textup.*
import org.textup.cache.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*
import org.textup.validator.action.*
import spock.lang.*

@Domain([AnnouncementReceipt, ContactNumber, CustomAccountDetails, FeaturedAnnouncement,
    FutureMessage, GroupPhoneRecord, IncomingSession, IndividualPhoneRecord, Location, MediaElement,
    MediaElementVersion, MediaInfo, Organization, OwnerPolicy, Phone, PhoneNumberHistory,
    PhoneOwnership, PhoneRecord, PhoneRecordMembers, Record, RecordCall, RecordItem,
    RecordItemReceipt, RecordNote, RecordNoteRevision, RecordText, Role, Schedule,
    SimpleFutureMessage, Staff, StaffRole, Team, Token])
@TestMixin(HibernateTestMixin)
@TestFor(PhoneActionService)
class PhoneActionServiceSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
        phoneCache(PhoneCache)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test has actions"() {
        expect:
        service.hasActions(doPhoneActions: null) == false
        service.hasActions(doPhoneActions: "hi")
    }

    void "test handling actions errors"() {
        given:
        Phone p1 = TestUtils.buildStaffPhone()
        p1.customAccount = TestUtils.buildCustomAccountDetails()
        Phone p2 = TestUtils.buildTeamPhone()

        PhoneAction a1 = GroovyMock()
        MockedMethod tryProcess = MockedMethod.create(ActionContainer, "tryProcess") {
            Result.createSuccess([a1])
        }
        MockedMethod tryDeactivatePhone = MockedMethod.create(service, "tryDeactivatePhone") {
            Result.createError([], ResultStatus.UNPROCESSABLE_ENTITY)
        }

        when:
        Result res = service.tryHandleActions(p1.id, [:])

        then:
        res.status == ResultStatus.FORBIDDEN

        when:
        res = service.tryHandleActions(p2.id, [:])

        then:
        1 * a1.toString() >> PhoneAction.DEACTIVATE
        tryDeactivatePhone.latestArgs == [p2]
        res.status == ResultStatus.UNPROCESSABLE_ENTITY

        cleanup:
        tryProcess?.restore()
        tryDeactivatePhone?.restore()
    }

    void "test handling actions success"() {
        given:
        String str1 = TestUtils.randString()
        PhoneOwnershipType pType = PhoneOwnershipType.values()[0]
        PhoneNumber pNum1 = TestUtils.randPhoneNumber()
        String numId = TestUtils.randString()
        Long targetId = TestUtils.randIntegerUpTo(88)

        Phone p1 = TestUtils.buildStaffPhone()
        PhoneAction a1 = GroovyMock() {
            getId() >> targetId
            buildPhoneOwnershipType() >> pType
            buildPhoneNumber() >> pNum1
            getNumberId() >> numId
        }
        MockedMethod tryProcess = MockedMethod.create(ActionContainer, "tryProcess") {
            Result.createSuccess([a1])
        }
        MockedMethod tryDeactivatePhone = MockedMethod.create(service, "tryDeactivatePhone") { Result.void() }
        MockedMethod tryExchangeOwners = MockedMethod.create(service, "tryExchangeOwners") { Result.void() }
        MockedMethod tryUpdatePhoneForNumber = MockedMethod.create(service, "tryUpdatePhoneForNumber") { Result.void() }
        MockedMethod tryUpdatePhoneForApiId = MockedMethod.create(service, "tryUpdatePhoneForApiId") { Result.void() }

        when:
        Result res = service.tryHandleActions(p1.id, [doPhoneActions: str1])

        then:
        tryProcess.latestArgs == [PhoneAction, str1]
        a1.toString() >> PhoneAction.DEACTIVATE
        tryDeactivatePhone.callCount == 1
        tryDeactivatePhone.latestArgs == [p1]
        tryExchangeOwners.callCount == 0
        tryUpdatePhoneForNumber.callCount == 0
        tryUpdatePhoneForApiId.callCount == 0
        res.status == ResultStatus.NO_CONTENT

        when:
        res = service.tryHandleActions(p1.id, [doPhoneActions: str1])

        then:
        tryProcess.latestArgs == [PhoneAction, str1]
        a1.toString() >> PhoneAction.TRANSFER
        tryDeactivatePhone.callCount == 1
        tryExchangeOwners.callCount == 1
        tryExchangeOwners.latestArgs == [p1, targetId, pType]
        tryUpdatePhoneForNumber.callCount == 0
        tryUpdatePhoneForApiId.callCount == 0
        res.status == ResultStatus.NO_CONTENT

        when:
        res = service.tryHandleActions(p1.id, [doPhoneActions: str1])

        then:
        tryProcess.latestArgs == [PhoneAction, str1]
        a1.toString() >> PhoneAction.NEW_NUM_BY_NUM
        tryDeactivatePhone.callCount == 1
        tryExchangeOwners.callCount == 1
        tryUpdatePhoneForNumber.callCount == 1
        tryUpdatePhoneForNumber.latestArgs == [p1, pNum1]
        tryUpdatePhoneForApiId.callCount == 0
        res.status == ResultStatus.NO_CONTENT

        when:
        res = service.tryHandleActions(p1.id, [doPhoneActions: str1])

        then:
        tryProcess.latestArgs == [PhoneAction, str1]
        a1.toString() >> PhoneAction.NEW_NUM_BY_ID
        tryDeactivatePhone.callCount == 1
        tryExchangeOwners.callCount == 1
        tryUpdatePhoneForNumber.callCount == 1
        tryUpdatePhoneForApiId.callCount == 1
        tryUpdatePhoneForApiId.latestArgs == [p1, numId]
        res.status == ResultStatus.NO_CONTENT

        cleanup:
        tryProcess?.restore()
        tryDeactivatePhone?.restore()
        tryExchangeOwners?.restore()
        tryUpdatePhoneForNumber?.restore()
        tryUpdatePhoneForApiId?.restore()
    }

    void "test cleaning existing number"() {
        given:
        String apiId = TestUtils.randString()
        Phone p1 = GroovyMock()
        service.numberService = GroovyMock(NumberService)

        when:
        Result res = service.tryCleanExistingNumber(p1, null)

        then:
        0 * service.numberService._
        res.status == ResultStatus.OK
        res.payload == p1

        when:
        res = service.tryCleanExistingNumber(p1, apiId)

        then:
        1 * service.numberService.freeExistingNumberToInternalPool(apiId) >> Result.void()
        res.status == ResultStatus.OK
        res.payload == p1
    }

    void "test trying to update phone given api id"() {
        given:
        String apiId1 = TestUtils.randString()
        String apiId2 = TestUtils.randString()
        PhoneNumber pNum1 = TestUtils.randPhoneNumber()

        Phone p1 = TestUtils.buildStaffPhone()
        p1.apiId = apiId1

        Phone.withSession { it.flush() }

        service.numberService = GroovyMock(NumberService)

        when:
        Result res = service.tryUpdatePhoneForApiId(p1, apiId1)

        then:
        0 * service.numberService._
        res.status == ResultStatus.OK
        res.payload == p1
        p1.apiId == apiId1
        p1.number != pNum1

        when:
        res = service.tryUpdatePhoneForApiId(p1, apiId2)

        then:
        1 * service.numberService.changeForApiId(apiId2) >> Result.createSuccess(pNum1)
        1 * service.numberService.freeExistingNumberToInternalPool(apiId1) >> Result.void()
        res.status == ResultStatus.OK
        res.payload == p1
        p1.apiId == apiId2
        p1.number == pNum1
    }

    void "test trying to update phone given new phone number"() {
        given:
        String apiId1 = TestUtils.randString()
        String apiId2 = TestUtils.randString()
        PhoneNumber pNum1 = TestUtils.randPhoneNumber()
        PhoneNumber pNum2 = TestUtils.randPhoneNumber()

        Phone p1 = TestUtils.buildStaffPhone()
        p1.number = pNum1
        p1.apiId = apiId1

        Phone.withSession { it.flush() }

        service.numberService = GroovyMock(NumberService)

        when:
        Result res = service.tryUpdatePhoneForNumber(p1, pNum1)

        then:
        0 * service.numberService._
        res.status == ResultStatus.OK
        res.payload == p1
        p1.number == pNum1

        when:
        res = service.tryUpdatePhoneForNumber(p1, pNum2)

        then:
        1 * service.numberService.changeForNumber(pNum2) >> Result.createSuccess(apiId2)
        1 * service.numberService.freeExistingNumberToInternalPool(apiId1) >> Result.void()
        res.status == ResultStatus.OK
        res.payload == p1
        p1.number == pNum2
        p1.number == pNum2
    }

    void "test trying exchange phone owners"() {
        given:
        Team t1 = TestUtils.buildTeam()
        Staff s1 = TestUtils.buildStaff()

        Phone p1 = TestUtils.buildStaffPhone()
        Phone p2 = TestUtils.buildStaffPhone(s1)

        when:
        Result res = service.tryExchangeOwners(p1, t1.id, PhoneOwnershipType.GROUP)

        then:
        res.status == ResultStatus.NO_CONTENT
        p1.owner.ownerId == t1.id
        p1.owner.type == PhoneOwnershipType.GROUP
        p2.owner.ownerId == s1.id
        p2.owner.type == PhoneOwnershipType.INDIVIDUAL

        when:
        res = service.tryExchangeOwners(p1, s1.id, PhoneOwnershipType.INDIVIDUAL)

        then:
        res.status == ResultStatus.NO_CONTENT
        p1.owner.ownerId == s1.id
        p1.owner.type == PhoneOwnershipType.INDIVIDUAL
        p2.owner.ownerId == t1.id
        p2.owner.type == PhoneOwnershipType.GROUP
    }

    void "test trying to deactivate phone"() {
        given:
        String apiId = TestUtils.randString()
        Phone p1 = TestUtils.buildActiveStaffPhone()
        p1.apiId = apiId

        Phone.withSession { it.flush() }
        assert p1.isActive()

        service.numberService = GroovyMock(NumberService)

        when:
        Result res = service.tryDeactivatePhone(p1)

        then:
        1 * service.numberService.freeExistingNumberToInternalPool(apiId) >> Result.void()
        res.status == ResultStatus.OK
        res.payload == p1
        res.payload.isActive() == false
    }
}
