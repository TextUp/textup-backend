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
@TestFor(PasswordResetController)
@TestMixin(HibernateTestMixin)
class PasswordResetControllerSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test save"() {
        given:
        TypeMap body = TypeMap.create(username: TestUtils.randString())

        controller.passwordResetService = GroovyMock(PasswordResetService)
        MockedMethod tryGetJsonBody = MockedMethod.create(RequestUtils, "tryGetJsonBody") {
            Result.createSuccess(body)
        }

        when:
        controller.save()

        then:
        1 * controller.passwordResetService.start(body.username) >> Result.void()
        response.status == ResultStatus.NO_CONTENT.intStatus

        cleanup:
        tryGetJsonBody?.restore()
    }

    void "test update"() {
        given:
        TypeMap body = TypeMap.create(token: TestUtils.randString(),
            password: TestUtils.randString())

        controller.passwordResetService = GroovyMock(PasswordResetService)
        MockedMethod tryGetJsonBody = MockedMethod.create(RequestUtils, "tryGetJsonBody") {
            Result.createSuccess(body)
        }

        when:
        controller.update()

        then:
        1 * controller.passwordResetService.finish(body.token, body.password) >> Result.void()
        response.status == ResultStatus.NO_CONTENT.intStatus

        cleanup:
        tryGetJsonBody?.restore()
    }
}
