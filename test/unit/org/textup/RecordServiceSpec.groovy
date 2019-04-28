package org.textup

import grails.test.mixin.*
import grails.test.mixin.gorm.*
import grails.test.mixin.hibernate.*
import grails.test.mixin.support.*
import grails.test.runtime.*
import grails.validation.*
import java.util.concurrent.*
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
@TestFor(RecordService)
@TestMixin(HibernateTestMixin)
class RecordServiceSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
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
        res.errorMessages[0] == "recordService.tooEarlyOrAlreadyEnded"

        when: "already ended"
        res = service.tryEndOngoingCall(rCall1)

        then:
        1 * rCall1.buildParentCallApiId() >> apiId
        1 * rCall1.isStillOngoing() >> false
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages[0] == "recordService.tooEarlyOrAlreadyEnded"

        when: "can end"
        res = service.tryEndOngoingCall(rCall1)

        then:
        1 * rCall1.buildParentCallApiId() >> apiId
        1 * rCall1.isStillOngoing() >> true
        1 * service.callService.hangUpImmediately(apiId, null) >> new Result()
        res.status == ResultStatus.OK
        res.payload == rCall1
    }

    void "test updating note fields"() {
        given:
        DateTime dt = DateTime.now().plusHours(1)
        TypeMap body1 = TypeMap.create(noteContents: TestUtils.randString(),
            isDeleted: true,
            after: DateTime.now())
        RecordNote rNote1 = TestUtils.buildRecordNote()
        Author author1 = TestUtils.buildAuthor()
        MockedMethod adjustPosition = MockedMethod.create(RecordUtils, "adjustPosition") { dt }

        when:
        Result res = service.trySetNoteFields(rNote1, body1, author1)

        then:
        res.status == ResultStatus.OK
        res.payload == rNote1
        rNote1.author == author1
        rNote1.noteContents == body1.noteContents
        rNote1.isDeleted == body1.isDeleted
        adjustPosition.latestArgs == [rNote1.record.id, body1.after]
        rNote1.whenCreated == dt

        when:
        res = service.trySetNoteFields(rNote1, TypeMap.create(noteContents: ""), author1)

        then: "empty string is okay"
        res.status == ResultStatus.OK
        res.payload == rNote1
        rNote1.noteContents == null

        when:
        res = service.trySetNoteFields(rNote1, TypeMap.create(noteContents: "  hi "), author1)

        then: "strings are trimmed"
        res.status == ResultStatus.OK
        res.payload == rNote1
        rNote1.noteContents == "hi"

        cleanup:
        adjustPosition?.restore()
    }

    void "test creating note with only a message of only spaces fails validation"() {
        given:
        PhoneNumber pNum1 = TestUtils.randPhoneNumber()
        TypeMap body = TypeMap.create(numbers: [pNum1], noteContents: "   ")

        Phone p1 = TestUtils.buildActiveStaffPhone()

        service.locationService = GroovyMock(LocationService)

        when:
        Result res = service.createNote(p1, body)

        then:
        1 * service.locationService.tryCreateOrUpdateIfPresent(*_) >> Result.void()
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages.contains("atLeastOneRequired")
    }

    void "test creating note"() {
        given:
        PhoneNumber pNum1 = TestUtils.randPhoneNumber()

        Staff s1 = TestUtils.buildStaff()
        Phone p1 = TestUtils.buildActiveStaffPhone()
        PhoneRecord spr1 = TestUtils.buildSharedPhoneRecord(null, p1)
        Location loc1 = TestUtils.buildLocation()
        MediaInfo mInfo1 = TestUtils.buildMediaInfo()

        TypeMap body1 = TypeMap.create("ids": [spr1.id], "numbers": [pNum1])
        TypeMap body2 = TypeMap.create(location: TestUtils.randTypeMap(),
            noteContents: TestUtils.randString(),
            "numbers": [pNum1])

        int prBaseline = PhoneRecord.count()

        service.locationService = GroovyMock(LocationService)
        MockedMethod tryGetActiveAuthUser = MockedMethod.create(AuthUtils, "tryGetActiveAuthUser") {
            Result.createSuccess(s1)
        }
        MockedMethod trySetNoteFields = MockedMethod.create(service, "trySetNoteFields") { RecordNote arg1 ->
            Result.createSuccess(arg1)
        }

        when:
        Result res = service.createNote(p1, body1, mInfo1)

        then:
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        PhoneRecord.count() == prBaseline + 1

        when:
        res = service.createNote(p1, body2, mInfo1)

        then:
        1 * service.locationService.tryCreateOrUpdateIfPresent(null, body2.location) >> Result.createSuccess(loc1)
        trySetNoteFields.latestArgs[0] instanceof RecordNote
        trySetNoteFields.latestArgs[1] == body2
        trySetNoteFields.latestArgs[2] == Author.create(s1)
        res.status == ResultStatus.CREATED
        res.payload instanceof Collection
        res.payload[0] instanceof RecordNote
        res.payload[0].noteContents == body2.noteContents
        res.payload[0].media == mInfo1
        res.payload[0].location == loc1
        PhoneRecord.count() == prBaseline + 1

        cleanup:
        tryGetActiveAuthUser?.restore()
        trySetNoteFields?.restore()
    }

    void "test creating call"() {
        given:
        PhoneNumber pNum1 = TestUtils.randPhoneNumber()

        Staff s1 = TestUtils.buildStaff()
        Phone p1 = TestUtils.buildActiveStaffPhone()
        GroupPhoneRecord gpr1 = TestUtils.buildGroupPhoneRecord(p1)
        PhoneRecord spr1 = TestUtils.buildSharedPhoneRecord(null, p1)

        TypeMap body1 = TypeMap.create("ids": [spr1.id], "numbers": [pNum1])
        TypeMap body2 = TypeMap.create("ids": [gpr1.id])
        TypeMap body3 = TypeMap.create("numbers": [pNum1])

        int prBaseline = PhoneRecord.count()

        RecordCall rCall1 = GroovyMock()
        service.outgoingCallService = GroovyMock(OutgoingCallService)
        MockedMethod tryGetActiveAuthUser = MockedMethod.create(AuthUtils, "tryGetActiveAuthUser") {
            Result.createSuccess(s1)
        }

        when: "too many"
        Result res = service.createCall(p1, body1)

        then:
        tryGetActiveAuthUser.notCalled
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        PhoneRecord.count() == prBaseline + 1

        when: "not an individual"
        res = service.createCall(p1, body2)

        then:
        tryGetActiveAuthUser.notCalled
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        PhoneRecord.count() == prBaseline + 1

        when:
        res = service.createCall(p1, body3)

        then:
        tryGetActiveAuthUser.hasBeenCalled
        1 * service.outgoingCallService.tryStart(s1.personalNumber, _, Author.create(s1)) >>
            Result.createSuccess(rCall1)
        res.status == ResultStatus.CREATED
        res.payload == [rCall1]

        cleanup:
        tryGetActiveAuthUser?.restore()
    }

    void "test creating text with only a message of only spaces fails validation"() {
        given:
        PhoneNumber pNum1 = TestUtils.randPhoneNumber()
        TypeMap body = TypeMap.create(numbers: [pNum1], contents: "   ")

        Phone p1 = TestUtils.buildActiveStaffPhone()

        when:
        Result res = service.createText(p1, body)

        then:
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages.contains("atLeastOneRequired")
    }

    void "test creating text"() {
        given:
        PhoneNumber pNum1 = TestUtils.randPhoneNumber()

        Staff s1 = TestUtils.buildStaff()
        Phone p1 = TestUtils.buildActiveStaffPhone()
        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord(p1)
        GroupPhoneRecord gpr1 = TestUtils.buildGroupPhoneRecord(p1)
        PhoneRecord spr1 = TestUtils.buildSharedPhoneRecord(null, p1)
        MediaInfo mInfo1 = TestUtils.buildMediaInfo()

        TypeMap body = TypeMap.create("ids": [ipr1, gpr1, spr1]*.id,
            "numbers": [pNum1],
            contents: TestUtils.randString())

        int prBaseline = PhoneRecord.count()

        Future fut1 = GroovyMock()
        RecordText rText1 = GroovyMock()
        service.outgoingMessageService = GroovyMock(OutgoingMessageService)
        MockedMethod tryGetActiveAuthUser = MockedMethod.create(AuthUtils, "tryGetActiveAuthUser") {
            Result.createSuccess(s1)
        }

        when:
        Result res = service.createText(p1, body, mInfo1, fut1)

        then:
        1 * service.outgoingMessageService.tryStart(RecordItemType.TEXT,
            {  it.countAsRecords },
            { it.text == body.contents && it.media == mInfo1 && it.location == null },
            Author.create(s1),
            fut1) >> Result.createSuccess(Tuple.create([rText1], null))
        res.status == ResultStatus.CREATED
        res.payload == [rText1]
        PhoneRecord.count() == prBaseline + 1

        cleanup:
        tryGetActiveAuthUser?.restore()
    }

    void "test deleting"() {
        given:
        RecordNote rNote1 = TestUtils.buildRecordNote()

        when:
        Result res = service.tryDelete(rNote1.id)

        then:
        res.status == ResultStatus.NO_CONTENT
        rNote1.isDeleted == true
    }

    // TODO
    void "test updating overall"() {
        given:
        String errMsg1 = TestUtils.randString()

        Staff s1 = TestUtils.buildStaff()
        RecordNote rNote1 = TestUtils.buildRecordNote()

        TypeMap body = TypeMap.create(location: TestUtils.randTypeMap())

        Future fut1 = GroovyMock()
        service.mediaService = GroovyMock(MediaService)
        service.locationService = GroovyMock(LocationService)
        MockedMethod tryGetActiveAuthUser = MockedMethod.create(AuthUtils, "tryGetActiveAuthUser") {
            Result.createSuccess(s1)
        }
        MockedMethod trySetNoteFields = MockedMethod.create(service, "trySetNoteFields") {
            Result.createSuccess(rNote1)
        }
        MockedMethod tryCreateRevision = MockedMethod.create(rNote1, "tryCreateRevision") {
            Result.createSuccess(rNote1)
        }

        when:
        Result res = service.tryUpdate(rNote1.id, body)

        then:
        1 * service.mediaService.tryCreateOrUpdate(rNote1, body) >> Result.createSuccess(fut1)
        1 * service.locationService.tryCreateOrUpdateIfPresent(rNote1.location, body.location) >>
            Result.createError([errMsg1], ResultStatus.BAD_REQUEST)
        tryCreateRevision.notCalled
        1 * fut1.cancel(true)
        res.status == ResultStatus.BAD_REQUEST
        res.errorMessages == [errMsg1]

        when:
        res = service.tryUpdate(rNote1.id, body)

        then:
        trySetNoteFields.latestArgs == [rNote1, body, Author.create(s1)]
        1 * service.mediaService.tryCreateOrUpdate(rNote1, body) >> Result.createSuccess(fut1)
        1 * service.locationService.tryCreateOrUpdateIfPresent(rNote1.location, body.location) >>
            Result.createSuccess(rNote1.location)
        tryCreateRevision.callCount == 1
        0 * fut1._
        res.status == ResultStatus.OK
        res.payload == rNote1
        res.payload.location == rNote1.location // location is preserved

        cleanup:
        tryGetActiveAuthUser?.restore()
        tryCreateRevision?.restore()
    }

    void "test creating overall"() {
        given:
        String errMsg1 = TestUtils.randString()
        TypeMap body = TestUtils.randTypeMap()

        Phone tp1 = TestUtils.buildActiveTeamPhone()
        MediaInfo mInfo1 = TestUtils.buildMediaInfo()

        Future fut1 = GroovyMock()
        service.mediaService = GroovyMock(MediaService)
        MockedMethod tryDetermineClass = MockedMethod.create(RecordUtils, "tryDetermineClass") {
            Result.createSuccess(RecordText)
        }
        MockedMethod createText = MockedMethod.create(service, "createText") {
            Result.createError([errMsg1], ResultStatus.BAD_REQUEST)
        }
        MockedMethod createCall = MockedMethod.create(service, "createCall") { Result.void() }
        MockedMethod createNote = MockedMethod.create(service, "createNote") { Result.void() }

        when:
        Result res = service.tryCreate(tp1.id, body)

        then:
        1 * service.mediaService.tryCreate(body) >> Result.createSuccess(Tuple.create(mInfo1, fut1))
        createText.hasBeenCalled
        1 * fut1.cancel(true)
        res.status == ResultStatus.BAD_REQUEST
        res.errorMessages == [errMsg1]

        when:
        createText = MockedMethod.create(createText) { Result.void() }
        res = service.tryCreate(tp1.id, body)

        then:
        tryDetermineClass.hasBeenCalled
        1 * service.mediaService.tryCreate(body) >> Result.createSuccess(Tuple.create(mInfo1, fut1))
        createText.latestArgs == [tp1, body, mInfo1, fut1]
        createCall.callCount == 0
        createNote.callCount == 0
        res.status == ResultStatus.NO_CONTENT

        when:
        tryDetermineClass = MockedMethod.create(tryDetermineClass) { Result.createSuccess(RecordCall) }
        res = service.tryCreate(tp1.id, body)

        then:
        tryDetermineClass.hasBeenCalled
        1 * service.mediaService.tryCreate(body) >> Result.createSuccess(Tuple.create(mInfo1, fut1))
        createText.hasBeenCalled
        createCall.latestArgs == [tp1, body]
        createNote.callCount == 0
        res.status == ResultStatus.NO_CONTENT

        when:
        tryDetermineClass = MockedMethod.create(tryDetermineClass) { Result.createSuccess(RecordNote) }
        res = service.tryCreate(tp1.id, body)

        then:
        tryDetermineClass.hasBeenCalled
        1 * service.mediaService.tryCreate(body) >> Result.createSuccess(Tuple.create(mInfo1, fut1))
        createText.hasBeenCalled
        createCall.hasBeenCalled
        createNote.latestArgs == [tp1, body, mInfo1]
        res.status == ResultStatus.NO_CONTENT

        cleanup:
        tryDetermineClass?.restore()
        createText?.restore()
        createCall?.restore()
        createNote?.restore()
    }
}
