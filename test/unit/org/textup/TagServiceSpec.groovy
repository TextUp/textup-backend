package org.textup

import grails.test.mixin.*
import grails.test.mixin.gorm.*
import grails.test.mixin.hibernate.*
import grails.test.mixin.support.*
import grails.test.runtime.*
import grails.validation.*
import org.joda.time.*
import org.textup.action.*
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
@TestFor(TagService)
@TestMixin(HibernateTestMixin)
class TagServiceSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test updating notification settings"() {
        given:
        TypeMap body = TestUtils.randTypeMap()
        GroupPhoneRecord gpr1 = TestUtils.buildGroupPhoneRecord()

        service.notificationActionService = GroovyMock(NotificationActionService)

        when:
        Result res = service.tryNotifications(gpr1, body)

        then:
        1 * service.notificationActionService.tryHandleActions(
            { it.first == gpr1.phone && it.second == gpr1.record.id },
            body) >> Result.void()
        res.status == ResultStatus.OK
        res.payload == gpr1
    }

    void "test updating fields"() {
        given:
        TypeMap body = TypeMap.create(name: TestUtils.randString(),
            hexColor: "#112233",
            language: VoiceLanguage.KOREAN)
        GroupPhoneRecord gpr1 = TestUtils.buildGroupPhoneRecord()

        when:
        Result res = service.trySetFields(gpr1, body)

        then:
        res.status == ResultStatus.OK
        res.payload == gpr1
        gpr1.name == body.name
        gpr1.hexColor == body.hexColor
        gpr1.record.language == body.language
    }

    void "test deleting"() {
        given:
        GroupPhoneRecord gpr1 = TestUtils.buildGroupPhoneRecord()

        MockedMethod tryCancelFutureMessages = MockedMethod.create(gpr1, "tryCancelFutureMessages") {
            Result.void()
        }

        when:
        Result res = service.tryDelete(gpr1.id)

        then:
        tryCancelFutureMessages.callCount == 1
        res.status == ResultStatus.NO_CONTENT
        gpr1.isDeleted == true

        cleanup:
        tryCancelFutureMessages?.restore()
    }

    void "test updating"() {
        given:
        TypeMap body = TestUtils.randTypeMap()
        GroupPhoneRecord gpr1 = TestUtils.buildGroupPhoneRecord()

        service.tagActionService = GroovyMock(TagActionService)
        MockedMethod trySetFields = MockedMethod.create(service, "trySetFields") {
            Result.createSuccess(gpr1)
        }
        MockedMethod tryNotifications = MockedMethod.create(service, "tryNotifications") {
            Result.createSuccess(gpr1)
        }

        when:
        Result res = service.tryUpdate(gpr1.id, body)

        then:
        trySetFields.latestArgs == [gpr1, body]
        tryNotifications.latestArgs == [gpr1, body]
        1 * service.tagActionService.tryHandleActions(gpr1, body) >> Result.void()
        res.status == ResultStatus.NO_CONTENT

        cleanup:
        trySetFields?.restore()
        tryNotifications?.restore()
    }

    void "test creating"() {
        given:
        TypeMap body = TypeMap.create(name: TestUtils.randString())

        Phone tp1 = TestUtils.buildActiveTeamPhone()

        service.tagActionService = GroovyMock(TagActionService)
        MockedMethod trySetFields = MockedMethod.create(service, "trySetFields") { GroupPhoneRecord gpr1 ->
            Result.createSuccess(gpr1)
        }
        MockedMethod tryNotifications = MockedMethod.create(service, "tryNotifications") { GroupPhoneRecord gpr1 ->
            Result.createSuccess(gpr1)
        }

        when:
        Result res = service.tryCreate(tp1.id, body)

        then:
        trySetFields.latestArgs[1] == body
        tryNotifications.latestArgs[1] == body
        1 * service.tagActionService.tryHandleActions(_, body) >> { args ->
            Result.createSuccess(args[0])
        }
        res.status == ResultStatus.CREATED
        res.payload instanceof GroupPhoneRecord
        res.payload.phone == tp1
        res.payload.name == body.name

        cleanup:
        trySetFields?.restore()
        tryNotifications?.restore()
    }
}
