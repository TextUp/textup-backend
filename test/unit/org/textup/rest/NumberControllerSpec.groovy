package org.textup.rest

import grails.test.mixin.*
import grails.test.mixin.gorm.*
import grails.test.mixin.hibernate.*
import org.textup.*
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
@TestFor(NumberController)
@TestMixin(HibernateTestMixin)
class NumberControllerSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test index"() {
        given:
        String search = TestUtils.randString()
        AvailablePhoneNumber aNum1 = AvailablePhoneNumber.tryCreateExisting(TestUtils.randPhoneNumberString(), TestUtils.randString()).payload
        AvailablePhoneNumber aNum2 = AvailablePhoneNumber.tryCreateExisting(TestUtils.randPhoneNumberString(), TestUtils.randString()).payload

        Staff s1 = TestUtils.buildStaff()

        controller.numberService = GroovyMock(NumberService)
        MockedMethod tryGetActiveAuthUser = MockedMethod.create(AuthUtils, "tryGetActiveAuthUser") {
            Result.createSuccess(s1)
        }
        MockedMethod respondWithClosures = MockedMethod.create(controller, "respondWithClosures")

        when:
        params.search = search
        controller.index()

        then:
        1 * controller.numberService.listExistingNumbers() >> Result.createSuccess([aNum1])
        1 * controller.numberService.listNewNumbers(search, s1.org.location) >> Result.createSuccess([aNum2])
        respondWithClosures.latestArgs[0] instanceof Closure
        respondWithClosures.latestArgs[0].call() == 2
        respondWithClosures.latestArgs[1] instanceof Closure
        aNum1 in respondWithClosures.latestArgs[1].call()
        aNum2 in respondWithClosures.latestArgs[1].call()
        respondWithClosures.latestArgs[2] == TypeMap.create(params)
        respondWithClosures.latestArgs[3] == MarshallerUtils.KEY_PHONE_NUMBER

        cleanup:
        tryGetActiveAuthUser?.restore()
        respondWithClosures?.restore()
    }

    void "test show"() {
        given:
        PhoneNumber pNum1 = TestUtils.randPhoneNumber()

        controller.numberService = GroovyMock(NumberService)

        when:
        params.id = pNum1.number
        controller.show()

        then:
        1 * controller.numberService.validateNumber(pNum1) >> Result.void()
        response.status == ResultStatus.NO_CONTENT.intStatus
    }

    void "test save"() {
        given:
        PhoneNumber pNum1 = TestUtils.randPhoneNumber()
        TypeMap body1 = TypeMap.create(phoneNumber: pNum1.number)
        TypeMap body2 = TypeMap.create(phoneNumber: pNum1.number, token: TestUtils.randString())

        controller.numberService = GroovyMock(NumberService)
        MockedMethod tryGetJsonBody = MockedMethod.create(RequestUtils, "tryGetJsonBody") {
            Result.createSuccess(body1)
        }

        when:
        controller.save()

        then:
        1 * controller.numberService.startVerifyOwnership(pNum1) >> Result.void()
        response.status == ResultStatus.NO_CONTENT.intStatus

        when:
        tryGetJsonBody = MockedMethod.create(tryGetJsonBody) { Result.createSuccess(body2) }
        params.clear()
        response.reset()
        controller.save()

        then:
        1 * controller.numberService.finishVerifyOwnership(body2.token, pNum1) >> Result.void()
        response.status == ResultStatus.NO_CONTENT.intStatus

        cleanup:
        tryGetJsonBody?.restore()
    }
}
