package org.textup

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestFor
import grails.test.mixin.TestMixin
import grails.test.runtime.*
import java.util.concurrent.Future
import org.codehaus.groovy.grails.web.util.TypeConvertingMap
import org.joda.time.DateTime
import org.textup.rest.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*
import spock.lang.*

@TestFor(RecordService)
@Domain([CustomAccountDetails, Contact, Phone, ContactTag, ContactNumber, Record, RecordItem, RecordText,
    RecordCall, RecordItemReceipt, SharedContact, Staff, Team, Organization,
    Schedule, Location, WeeklySchedule, PhoneOwnership, Role, StaffRole,
    RecordNote, RecordNoteRevision, NotificationPolicy,
    MediaInfo, MediaElement, MediaElementVersion])
@TestMixin(HibernateTestMixin)
class RecordServiceSpec extends CustomSpec {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        setupData()
        service.resultFactory = TestUtils.getResultFactory(grailsApplication)
    }

    def cleanup() {
        cleanupData()
    }

    // Create
    // ------

    void "test create error"() {
        given:
        service.authService = Mock(AuthService)

        when: "no phone"
        TypeConvertingMap params = [:]
        ResultGroup resGroup = service.create(null, params)

        then:
        0 * service.authService._
        resGroup.anySuccesses == false
        resGroup.failures.size() == 1
        resGroup.failures[0].errorMessages[0] == "recordService.create.noPhone"
        resGroup.failureStatus == ResultStatus.UNPROCESSABLE_ENTITY

        when: "not owner"
        resGroup = service.create(t1.phone.id, params)

        then:
        1 * service.authService.loggedInAndActive >> Mock(Staff)
        resGroup.anySuccesses == false
        resGroup.failures.size() == 1
        resGroup.failures[0].errorMessages[0] == "phone.notOwner"
        resGroup.failureStatus == ResultStatus.FORBIDDEN

        when: "invalid entity"
        resGroup = service.create(t1.phone.id, params)

        then:
        1 * service.authService.loggedInAndActive >> t1.phone.owner.buildAllStaff()[0]
        resGroup.anySuccesses == false
        resGroup.failures.size() == 1
        resGroup.failures[0].errorMessages[0] == "recordUtils.determineClass.unknownType"
        resGroup.failureStatus == ResultStatus.UNPROCESSABLE_ENTITY
    }

    @DirtiesRuntime
    void "test create overall"() {
        given:
        Phone p1 = t1.phone
        service.authService = Stub(AuthService) { getLoggedInAndActive() >> p1.owner.buildAllStaff()[0] }
        MockedMethod createText = TestUtils.mock(service, "createText") { new ResultGroup() }
        MockedMethod createCall = TestUtils.mock(service, "createCall") { new Result() }
        MockedMethod createNote = TestUtils.mock(service, "createNote") { new Result() }

        when: "text"
        TypeConvertingMap itemInfo = new TypeConvertingMap(contents:"hi", sendToPhoneNumbers:["2223334444"],
            sendToContacts:[tC1.id])
        service.create(p1.id, itemInfo)

        then:
        createText.callCount == 1
        createCall.callCount == 0
        createNote.callCount == 0

        when: "call"
        itemInfo = new TypeConvertingMap(callContact:8L)
        service.create(p1.id, itemInfo)

        then:
        createText.callCount == 1
        createCall.callCount == 1
        createNote.callCount == 0

        when: "note"
        itemInfo = new TypeConvertingMap(forContact:8L)
        service.create(p1.id, itemInfo)

        then:
        createText.callCount == 1
        createCall.callCount == 1
        createNote.callCount == 1
    }

    @DirtiesRuntime
    void "test creating text errors"() {
        given:
        service.authService = Stub(AuthService)
        service.mediaService = Mock(MediaService)
        service.outgoingMessageService = Mock(OutgoingMessageService)

        when: "has media + errors during processing"
        ResultGroup<RecordItem> resGroup = service.createText(null, null)

        then:
        1 * service.mediaService.hasMediaActions(*_) >> true
        1 * service.mediaService.tryProcess(*_) >> new Result(status: ResultStatus.FORBIDDEN)
        0 * service.outgoingMessageService._
        resGroup.anySuccesses == false
        resGroup.failures.size() == 1
        resGroup.failureStatus == ResultStatus.FORBIDDEN

        when: "error when building target"
        TestUtils.mock(RecordUtils, "buildOutgoingMessageTarget")
            { new Result(status: ResultStatus.UNPROCESSABLE_ENTITY) }
        resGroup = service.createText(null, null)

        then:
        1 * service.mediaService.hasMediaActions(*_) >> false
        0 * service.outgoingMessageService._
        resGroup.anySuccesses == false
        resGroup.failures.size() == 1
        resGroup.failureStatus == ResultStatus.UNPROCESSABLE_ENTITY
    }

    @DirtiesRuntime
    void "test creating text"() {
        given:
        service.authService = Stub(AuthService)
        service.mediaService = Mock(MediaService)
        service.outgoingMessageService = Mock(OutgoingMessageService)
        MockedMethod buildOutgoingMessageTarget = TestUtils
            .mock(RecordUtils, "buildOutgoingMessageTarget") { new Result() }

        when: "no media"
        ResultGroup<RecordItem> resGroup = service.createText(null, null)

        then:
        1 * service.mediaService.hasMediaActions(*_) >> false
        0 * service.mediaService.tryProcess(*_)
        1 * service.outgoingMessageService.processMessage(*_) >> Tuple.create(null, null)

        when: "with media"
        resGroup = service.createText(null, null)

        then:
        1 * service.mediaService.hasMediaActions(*_) >> true
        1 * service.mediaService.tryProcess(*_) >> new Result()
        1 * service.outgoingMessageService.processMessage(*_) >> Tuple.create(null, null)
    }

    @DirtiesRuntime
    void "test creating call"() {
        given:
        service.authService = Stub(AuthService)
        service.outgoingMessageService = Mock(OutgoingMessageService)
        RecordCall rCall = Mock()
        MockedMethod buildOutgoingCallTarget = TestUtils
            .mock(RecordUtils, "buildOutgoingCallTarget") { new Result() }

        when:
        Result<RecordItem> res = service.createCall(null, null)

        then:
        1 * service.outgoingMessageService.startBridgeCall(*_) >> new Result(payload: rCall)
        res.status == ResultStatus.OK
        res.payload == rCall
    }

    void "test merging note for non-object fields"() {
        given:
        Record rec = new Record()
        rec.save(flush: true, failOnError: true)
        RecordNote note1 = new RecordNote(record: rec)
        assert note1.validate()
        service.mediaService = Mock(MediaService)
        service.authService = Mock(AuthService)

        when:
        TypeConvertingMap body = new TypeConvertingMap(after:DateTime.now().minusDays(2),
            noteContents: TestUtils.randString(), isDeleted: true)
        Result<RecordNote> res = service.mergeNote(note1, body, ResultStatus.CREATED)

        then:
        1 * service.mediaService.tryProcess(*_) >> { args ->
            new Result(payload: Tuple.create(args[0], null))
        }
        1 * service.authService.loggedInAndActive >> s1
        res.status == ResultStatus.CREATED
        res.payload == note1
        res.payload.whenCreated.isBeforeNow()
        res.payload.noteContents == body.noteContents
        res.payload.isDeleted == body.isDeleted
        res.payload.revisions == null // no revisions for new notes!
    }

    void "test merging note for object fields"() {
        given:
        Record rec = new Record()
        assert rec.save(flush: true, failOnError: true)
        RecordNote note1 = new RecordNote(record: rec)
        assert note1.save(flush: true, failOnError: true)
        service.mediaService = Mock(MediaService)
        service.authService = Mock(AuthService)
        int lBaseline = Location.count()
        int mBaseline = MediaInfo.count()

        when: "with location"
        TypeConvertingMap body = new TypeConvertingMap(location: [address:"123 Main Street", lat:22G, lon:22G])
        Result<RecordNote> res = service.mergeNote(note1, body)
        RecordNote.withSession { it.flush() }

        then:
        1 * service.mediaService.tryProcess(*_) >> { args ->
            new Result(payload: Tuple.create(args[0], null))
        }
        1 * service.authService.loggedInAndActive >> s1
        res.status == ResultStatus.OK
        res.payload == note1
        res.payload.location instanceof Location
        res.payload.revisions instanceof Collection // existing note creates revisions
        res.payload.revisions.size() == 1
        Location.count() == lBaseline + 1
        MediaInfo.count() == mBaseline

        when: "with media"
        body = new TypeConvertingMap()
        res = service.mergeNote(note1, body)
        RecordNote.withSession { it.flush() }

        then:
        1 * service.mediaService.tryProcess(*_) >> { args ->
            args[0].media = new MediaInfo()
            assert args[0].media.save()
            new Result(payload: Tuple.create(args[0], null))
        }
        1 * service.authService.loggedInAndActive >> s1
        res.status == ResultStatus.OK
        res.payload == note1
        res.payload.location instanceof Location
        res.payload.revisions instanceof Collection // existing note creates revisions
        res.payload.revisions.size() == 2
        Location.count() == lBaseline + 2 // each revision creates its own duplicate location
        MediaInfo.count() == mBaseline + 1
    }

    void "test cancelling future processing if error during merging note"() {
        given:
        service.mediaService = Mock(MediaService)
        service.authService = Mock(AuthService)
        Future fut1 = Mock()

        when:
        TypeConvertingMap body = new TypeConvertingMap()
        Result<RecordNote> res = service.mergeNote(null, body)

        then:
        1 * service.mediaService.tryProcess(*_) >> new Result(payload: Tuple.create(null, fut1))
        1 * service.authService.loggedInAndActive >> s1
        1 * fut1.cancel(true)
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
    }

    void "test creating note overall"() {
        given:
        service.mediaService   = Mock(MediaService)
        service.authService    = Mock(AuthService)
        int lBaseline          = Location.count()
        int mBaseline          = MediaInfo.count()
        int nBaseline          = RecordNote.count()
        int revBaseline        = RecordNoteRevision.count()

        when:
        TypeConvertingMap body = new TypeConvertingMap(forContact: c1.id,
            noteContents: TestUtils.randString(),
            location: [address:"123 Main Street", lat:22G, lon:22G])
        Result<RecordItem> res = service.createNote(p1, body)

        then:
        1 * service.mediaService.tryProcess(*_) >> { args ->
            args[0].media = new MediaInfo()
            assert args[0].media.save()
            new Result(payload: Tuple.create(args[0], null))
        }
        1 * service.authService.loggedInAndActive >> s1
        Location.count()           == lBaseline + 1
        MediaInfo.count()          == mBaseline + 1
        RecordNote.count()         == nBaseline + 1
        RecordNoteRevision.count() == revBaseline
    }

    void "test trying to end call"() {
        given:
        String apiId = TestUtils.randString()
        RecordCall rCall1 = GroovyMock()
        service.callService = GroovyMock(CallService)

        when: "too early"
        Result res = service.tryEndOngoingCall(rCall1)

        then:
        1 * rCall1.buildParentCallApiId() >> null
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages[0] == "recordService.tryEndOngoingCall.tooEarlyOrAlreadyEnded"

        when: "already ended"
        res = service.tryEndOngoingCall(rCall1)

        then:
        1 * rCall1.buildParentCallApiId() >> apiId
        1 * rCall1.isStillOngoing() >> false
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages[0] == "recordService.tryEndOngoingCall.tooEarlyOrAlreadyEnded"

        when: "can end"
        res = service.tryEndOngoingCall(rCall1)

        then:
        1 * rCall1.buildParentCallApiId() >> apiId
        1 * rCall1.isStillOngoing() >> true
        1 * service.callService.hangUpImmediately(apiId, null) >> new Result()
        res.status == ResultStatus.OK
        res.payload == rCall1
    }

    void "test trying to update note"() {
        given:
        RecordNote rNote1 = GroovyMock()
        TypeConvertingMap body = new TypeConvertingMap()
        MockedMethod mergeNote = TestUtils.mock(service, "mergeNote")

        when:
        Result res = service.tryUpdateNote(rNote1, body)

        then:
        1 * rNote1.isReadOnly >> true
        mergeNote.callCount == 0
        res.status == ResultStatus.FORBIDDEN
        res.errorMessages[0] == "recordService.update.readOnly"

        when:
        res = service.tryUpdateNote(rNote1, body)

        then:
        1 * rNote1.isReadOnly >> false
        mergeNote.callCount == 1
        mergeNote.callArguments[0] == [rNote1, body, null]

        cleanup:
        mergeNote?.restore()
    }

    @DirtiesRuntime
    void "test updating note overall"() {
        given:
        Long itemId = TestUtils.randIntegerUpTo(88)
        RecordItem rItem1 = GroovyMock()
        TypeConvertingMap body1 = new TypeConvertingMap(endOngoing: false)
        TypeConvertingMap body2 = new TypeConvertingMap(endOngoing: true)
        MockedMethod tryUpdateNote = TestUtils.mock(service, "tryUpdateNote")
        MockedMethod tryEndOngoingCall = TestUtils.mock(service, "tryEndOngoingCall")

        when: "not found"
        RecordItem.metaClass."static".get = { Long arg1 -> null }
        Result res = service.update(itemId, body1)

        then:
        res.status == ResultStatus.NOT_FOUND
        res.errorMessages[0] == "recordService.update.notFound"
        tryUpdateNote.callCount == 0
        tryEndOngoingCall.callCount == 0

        when: "is note"
        RecordItem.metaClass."static".get = { Long arg1 -> rItem1 }
        res = service.update(itemId, body1)

        then:
        1 * rItem1.asBoolean() >> true
        1 * rItem1.instanceOf(RecordNote) >> true
        tryUpdateNote.callCount == 1
        tryEndOngoingCall.callCount == 0

        when: "is call but do not end outgoing"
        res = service.update(itemId, body1)

        then:
        1 * rItem1.asBoolean() >> true
        1 * rItem1.instanceOf(RecordCall) >> true
        tryUpdateNote.callCount == 1
        tryEndOngoingCall.callCount == 0

        when: "is call and end outgoing"
        res = service.update(itemId, body2)

        then:
        1 * rItem1.asBoolean() >> true
        1 * rItem1.instanceOf(RecordCall) >> true
        tryUpdateNote.callCount == 1
        tryEndOngoingCall.callCount == 1

        when: "is neither note nor call"
        res = service.update(itemId, body1)

        then:
        1 * rItem1.asBoolean() >> true
        1 * rItem1.instanceOf(RecordNote) >> false
        1 * rItem1.instanceOf(RecordCall) >> false
        tryUpdateNote.callCount == 1
        tryEndOngoingCall.callCount == 1

        cleanup:
        tryUpdateNote?.restore()
        tryEndOngoingCall?.restore()
    }

    void "test deleting note"() {
        given: "baselines and an existing note"
        RecordNote note1 = new RecordNote(record:c1.record)
        note1.save(flush:true, failOnError:true)
        int rBaseline = RecordNoteRevision.count()
        int nBaseline = RecordNote.count()

        when: "deleting a nonexistent note"
        Result res = service.delete(-88L)

        then:
        res.success == false
        res.status == ResultStatus.NOT_FOUND
        res.errorMessages[0] == "recordService.delete.notFound"
        RecordNoteRevision.count() == rBaseline
        RecordNote.count() == nBaseline

        when: "deleting a readonly note"
        note1.isReadOnly = true
        note1.save(flush:true, failOnError:true)
        res = service.delete(note1.id)

        then: "forbidden"
        res.success == false
        res.status == ResultStatus.FORBIDDEN
        res.errorMessages[0] == "recordService.delete.readOnly"
        RecordNoteRevision.count() == rBaseline
        RecordNote.count() == nBaseline

        when: "deleting an existing note"
        note1.isReadOnly = false
        note1.save(flush:true, failOnError:true)
        res = service.delete(note1.id)

        then:
        res.success == true
        res.status == ResultStatus.NO_CONTENT
        res.payload == null
        RecordNoteRevision.count() == rBaseline
        RecordNote.count() == nBaseline
        RecordNote.get(note1.id).isDeleted == true
    }
}
