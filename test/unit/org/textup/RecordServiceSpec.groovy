package org.textup

import org.textup.test.*
import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestFor
import grails.test.mixin.TestMixin
import grails.test.runtime.*
import java.util.concurrent.Future
import org.codehaus.groovy.grails.web.util.TypeConvertingMap
import org.joda.time.DateTime
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

    void "test updating note overall"() {
        given:
        service.mediaService                        = Mock(MediaService)
        service.authService                         = Mock(AuthService)
        Record rec                                  = new Record()
        assert rec.save(flush: true, failOnError: true)
        RecordNote note1 = new RecordNote(record:rec,
            noteContents: "hi",
            location: new Location(address: "hi", lat: 0G, lon: 0G),
            author: s1.toAuthor())
        assert note1.save(flush: true, failOnError: true)
        int lBaseline   = Location.count()
        int mBaseline   = MediaInfo.count()
        int nBaseline   = RecordNote.count()
        int revBaseline = RecordNoteRevision.count()

        when: "nonexistent note"
        Result<RecordItem> res = service.update(-88L, null)

        then:
        res.status                 == ResultStatus.NOT_FOUND
        res.errorMessages[0]       == "recordService.update.notFound"
        Location.count()           == lBaseline
        MediaInfo.count()          == mBaseline
        RecordNote.count()         == nBaseline
        RecordNoteRevision.count() == revBaseline

        when: "note is read only"
        note1.isReadOnly = true
        note1.save(flush:true, failOnError:true)
        res = service.update(note1.id, new TypeConvertingMap())

        then:
        res.status                 == ResultStatus.FORBIDDEN
        res.errorMessages[0]       == "recordService.update.readOnly"
        Location.count()           == lBaseline
        MediaInfo.count()          == mBaseline
        RecordNote.count()         == nBaseline
        RecordNoteRevision.count() == revBaseline

        when: "no longer read only, toggle deleted flag"
        note1.isReadOnly = false
        note1.save(flush:true, failOnError:true)
        TypeConvertingMap body = new TypeConvertingMap(isDeleted: true)
        res      = service.update(note1.id, body)

        then: "updated but no revisions"
        1 * service.mediaService.tryProcess(*_) >> { args ->
            new Result(payload: Tuple.create(args[0], null))
        }
        1 * service.authService.loggedInAndActive >> s1
        res.status                 == ResultStatus.OK
        res.payload                == note1
        res.payload.isDeleted      == true
        Location.count()           == lBaseline
        MediaInfo.count()          == mBaseline
        RecordNote.count()         == nBaseline
        RecordNoteRevision.count() == revBaseline


        when: "update contents"
        body = new TypeConvertingMap(noteContents: TestUtils.randString())
        res  = service.update(note1.id, body)

        then:
        1 * service.mediaService.tryProcess(*_) >> { args ->
            new Result(payload: Tuple.create(args[0], null))
        }
        1 * service.authService.loggedInAndActive >> s1
        res.status                 == ResultStatus.OK
        res.payload                == note1
        res.payload.noteContents   == body.noteContents
        Location.count()           == lBaseline + 1
        MediaInfo.count()          == mBaseline
        RecordNote.count()         == nBaseline
        RecordNoteRevision.count() == revBaseline + 1
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
