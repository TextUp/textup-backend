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
@TestFor(ContactService)
@TestMixin(HibernateTestMixin)
class ContactServiceSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test handling merge actions"() {
        given:
        TypeMap body = TestUtils.randTypeMap()
        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord()
        service.mergeActionService = GroovyMock(MergeActionService)

        when:
        Result res = service.tryMerge(ipr1.toWrapper(), body)

        then:
        1 * service.mergeActionService.hasActions(body) >> false
        res.status == ResultStatus.OK
        res.payload == ipr1.toWrapper()

        when:
        res = service.tryMerge(ipr1.toWrapper(), body)

        then:
        1 * service.mergeActionService.hasActions(body) >> true
        1 * service.mergeActionService.tryHandleActions(ipr1.id, body) >> Result.void()
        res.status == ResultStatus.OK
        res.payload == ipr1.toWrapper()
    }

    void "test handling share actions"() {
        given:
        TypeMap body = TestUtils.randTypeMap()
        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord()
        service.shareActionService = GroovyMock(ShareActionService)

        when:
        Result res = service.trySharing(ipr1.toWrapper(), body)

        then:
        1 * service.shareActionService.hasActions(body) >> false
        res.status == ResultStatus.OK
        res.payload == ipr1.toWrapper()

        when:
        res = service.trySharing(ipr1.toWrapper(), body)

        then:
        1 * service.shareActionService.hasActions(body) >> true
        1 * service.shareActionService.tryHandleActions(ipr1, body) >> Result.void()
        res.status == ResultStatus.OK
        res.payload == ipr1.toWrapper()
    }

    void "test handling notification actions"() {
        given:
        TypeMap body = TestUtils.randTypeMap()
        PhoneRecord spr1 = TestUtils.buildSharedPhoneRecord()
        service.notificationActionService = GroovyMock(NotificationActionService)

        when:
        Result res = service.tryNotifications(spr1.toWrapper(), body)

        then:
        1 * service.notificationActionService.hasActions(body) >> false
        res.status == ResultStatus.OK
        res.payload == spr1.toWrapper()

        when:
        res = service.tryNotifications(spr1.toWrapper(), body)

        then:
        1 * service.notificationActionService.hasActions(body) >> true
        1 * service.notificationActionService.tryHandleActions(Tuple.create(spr1.phone, spr1.record.id), body) >>
            Result.void()
        res.status == ResultStatus.OK
        res.payload == spr1.toWrapper()
    }

    void "test updating fields"() {
        given:
        TypeMap body = TypeMap.create(name: TestUtils.randString(),
            note: TestUtils.randString(),
            language: VoiceLanguage.CHINESE,
            status: PhoneRecordStatus.UNREAD)
        PhoneRecord spr1 = TestUtils.buildSharedPhoneRecord()

        when:
        Result res = service.trySetFields(spr1.toWrapper(), body)

        then:
        res.status == ResultStatus.OK
        res.payload == spr1.toWrapper()
        spr1.shareSource.name == body.name
        spr1.shareSource.note == body.note
        spr1.record.language == body.language
        spr1.status == body.status
        spr1.shareSource.status != body.status
    }

    void "test deleting"() {
        given:
        TypeMap body = TestUtils.randTypeMap()
        PhoneRecord spr1 = TestUtils.buildSharedPhoneRecord()

        MockedMethod tryCancelFutureMessages = MockedMethod.create(spr1.shareSource, "tryCancelFutureMessages") {
            Result.void()
        }

        when:
        Result res = service.tryDelete(spr1.id)

        then:
        tryCancelFutureMessages.notCalled
        res.status == ResultStatus.FORBIDDEN
        spr1.shareSource.isDeleted == false

        when:
        res = service.tryDelete(spr1.shareSource.id)

        then:
        tryCancelFutureMessages.callCount == 1
        res.status == ResultStatus.NO_CONTENT
        spr1.shareSource.isDeleted

        cleanup:
        tryCancelFutureMessages?.restore()
    }

    void "test updating overall"() {
        given:
        TypeMap body = TestUtils.randTypeMap()
        PhoneRecord spr1 = TestUtils.buildSharedPhoneRecord()
        IndividualPhoneRecordWrapper w1 = spr1.toWrapper()

        service.numberActionService = GroovyMock(NumberActionService)
        MockedMethod trySetFields = MockedMethod.create(service, "trySetFields") { arg1 ->
            Result.createSuccess(arg1)
        }
        MockedMethod tryNotifications = MockedMethod.create(service, "tryNotifications") { arg1 ->
            Result.createSuccess(arg1)
        }
        MockedMethod trySharing = MockedMethod.create(service, "trySharing") { arg1 ->
            Result.createSuccess(arg1)
        }
        MockedMethod tryMerge = MockedMethod.create(service, "tryMerge") { arg1 ->
            Result.createSuccess(arg1)
        }

        when:
        Result res = service.tryUpdate(spr1.id, body)

        then:
        trySetFields.latestArgs == [w1, body]
        tryNotifications.latestArgs == [w1, body]
        1 * service.numberActionService.tryHandleActions(w1, body) >> Result.createSuccess(w1)
        trySharing.latestArgs == [w1, body]
        tryMerge.latestArgs == [w1, body]
        res.status == ResultStatus.OK
        res.payload == w1

        cleanup:
        trySetFields?.restore()
        tryNotifications?.restore()
        trySharing?.restore()
        tryMerge?.restore()
    }

    void "test creating overall"() {
        given:
        TypeMap body = TestUtils.randTypeMap()
        Phone p1 = TestUtils.buildActiveStaffPhone()

        int iprBaseline = IndividualPhoneRecord.count()

        service.numberActionService = GroovyMock(NumberActionService)
        MockedMethod trySetFields = MockedMethod.create(service, "trySetFields") { arg1 ->
            Result.createSuccess(arg1)
        }
        MockedMethod tryNotifications = MockedMethod.create(service, "tryNotifications") { arg1 ->
            Result.createSuccess(arg1)
        }
        MockedMethod trySharing = MockedMethod.create(service, "trySharing") { arg1 ->
            Result.createSuccess(arg1)
        }
        MockedMethod tryMerge = MockedMethod.create(service, "tryMerge") { arg1 ->
            Result.createSuccess(arg1)
        }

        when:
        Result res = service.tryCreate(p1.id, body)

        then:
        trySetFields.latestArgs[1] == body
        tryNotifications.latestArgs[1] == body
        1 * service.numberActionService.tryHandleActions(_, body) >> { args ->
            Result.createSuccess(args[0])
        }
        trySharing.latestArgs[1] == body
        tryMerge.latestArgs[1] == body
        res.status == ResultStatus.CREATED
        res.payload instanceof IndividualPhoneRecordWrapper
        res.payload.tryUnwrap().payload.phone == p1
        IndividualPhoneRecord.count() == iprBaseline + 1

        cleanup:
        trySetFields?.restore()
        tryNotifications?.restore()
        trySharing?.restore()
        tryMerge?.restore()
    }
}
