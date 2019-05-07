package org.textup.rest

import grails.gorm.DetachedCriteria
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
@TestFor(ValidateController)
@TestMixin(HibernateTestMixin)
class ValidateControllerSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test save"() {
        given:
        String pwd = TestUtils.randString()
        String lockCode = TestUtils.randString()
        TypeMap body1 = TypeMap.create(password: pwd)
        TypeMap body2 = TypeMap.create(lockCode: lockCode)

        Staff s1 = TestUtils.buildStaff()

        MockedMethod tryGetJsonBody = MockedMethod.create(RequestUtils, "tryGetJsonBody") {
            Result.createSuccess(body1)
        }
        MockedMethod tryGetAnyAuthUser = MockedMethod.create(AuthUtils, "tryGetAnyAuthUser") {
            Result.createSuccess(s1)
        }
        MockedMethod isValidCredentials = MockedMethod.create(AuthUtils, "isValidCredentials") {
            true
        }
        MockedMethod isSecureStringValid = MockedMethod.create(AuthUtils, "isSecureStringValid") {
            false
        }

        when: "validate password"
        controller.save()

        then:
        tryGetJsonBody.latestArgs == [request, null]
        isValidCredentials.latestArgs == [s1.username, pwd]
        isSecureStringValid.notCalled
        response.status == ResultStatus.NO_CONTENT.intStatus

        when: "validate lock code"
        tryGetJsonBody = MockedMethod.create(tryGetJsonBody) { Result.createSuccess(body2) }
        response.reset()
        controller.save()

        then:
        tryGetJsonBody.latestArgs == [request, null]
        isSecureStringValid.latestArgs == [s1.lockCode, lockCode]
        response.status == ResultStatus.FORBIDDEN.intStatus
        response.json.errors.size() == 1
        response.json.errors[0].statusCode == ResultStatus.FORBIDDEN.intStatus
        response.json.errors[0].message == "validateController.invalidCredentials"

        cleanup:
        tryGetJsonBody?.restore()
        tryGetAnyAuthUser?.restore()
        isValidCredentials?.restore()
        isSecureStringValid?.restore()
    }
}
