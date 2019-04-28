package org.textup

import grails.test.mixin.*
import grails.test.mixin.gorm.*
import grails.test.mixin.hibernate.*
import grails.test.mixin.support.*
import grails.test.runtime.*
import grails.validation.*
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
@TestMixin(HibernateTestMixin)
class RecordNoteRevisionSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test validation"() {
        given:
        Record rec = TestUtils.buildRecord()

        when: "empty revision"
        RecordNoteRevision rev1 = new RecordNoteRevision()

        then: "requires an associated note"
        rev1.validate() == false
        rev1.errors.getFieldErrorCount("whenChanged") == 1
        rev1.errors.getFieldErrorCount("note") == 1

        when: "associate revision with a note"
        RecordNote note1 = new RecordNote(record: rec)
        assert note1.validate()
        rev1.note = note1
        rev1.whenChanged = note1.whenChanged

        then:
        rev1.validate() == true

        when: "noteContents too long"
        rev1.noteContents = TestUtils.buildVeryLongString()

        then:
        rev1.validate() == false
        rev1.errors.errorCount == 1
        rev1.errors.getFieldErrorCount("noteContents") == 1
    }

    void "test creating a revision"() {
        given: "a valid note"
        Record rec = TestUtils.buildRecord()
        RecordNote note1 = new RecordNote(record: rec)
        note1.authorName = TestUtils.randString()
        note1.authorId = 88L
        note1.authorType = AuthorType.STAFF
        note1.noteContents = TestUtils.randString()
        note1.location = TestUtils.buildLocation()
        note1.media = new MediaInfo()
        assert note1.validate()

        when:
        Result res = RecordNoteRevision.tryCreate(null)

        then:
        res.status == ResultStatus.UNPROCESSABLE_ENTITY

        when: "creating a revision for an unsaved note"
        res = RecordNoteRevision.tryCreate(note1)

        then: "null because revisions use PERSISTED values"
        res.status == ResultStatus.UNPROCESSABLE_ENTITY

        when: "creating a revision for a saved note"
        note1.save(flush:true, failOnError:true)

        int lBaseline = Location.count()
        int mBaseline = MediaInfo.count()

        res = RecordNoteRevision.tryCreate(note1)
        RecordNote.withSession { it.flush() }

        then: "revision created but no media because media is still empty"
        res.status == ResultStatus.CREATED
        res.payload instanceof RecordNoteRevision
        res.payload.authorName == note1.authorName
        res.payload.authorId == note1.authorId
        res.payload.authorType == note1.authorType
        res.payload.whenChanged == note1.whenChanged
        res.payload.noteContents == note1.noteContents
        res.payload.location instanceof Location
        res.payload.location.id != note1.location.id
        note1.media.id != null
        res.payload.media == null
        Location.count() == lBaseline + 1
        MediaInfo.count() == mBaseline

        when: "add some media elements to the media"
        MediaElement el1 = TestUtils.buildMediaElement()
        note1.media.addToMediaElements(el1)
        note1.save(flush:true, failOnError:true)

        res = RecordNoteRevision.tryCreate(note1)
        RecordNote.withSession { it.flush() }

        then: "revision created this time has all fields, including media, populated"
        res.status == ResultStatus.CREATED
        res.payload instanceof RecordNoteRevision
        res.payload.authorName == note1.authorName
        res.payload.authorId == note1.authorId
        res.payload.authorType == note1.authorType
        res.payload.whenChanged == note1.whenChanged
        res.payload.noteContents == note1.noteContents
        res.payload.location instanceof Location
        res.payload.location.id != note1.location.id
        res.payload.media instanceof MediaInfo
        res.payload.media.id != note1.media.id
        Location.count() == lBaseline + 2
        MediaInfo.count() == mBaseline + 1
    }
}
