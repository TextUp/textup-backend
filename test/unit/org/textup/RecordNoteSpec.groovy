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
@Unroll
class RecordNoteSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test validation + cascading to location"() {
        given:
        Record rec1 = TestUtils.buildRecord()

    	when: "empty note"
        Result res = RecordNote.tryCreate(null, null, null, null)

    	then: "invalid"
        res.status == ResultStatus.UNPROCESSABLE_ENTITY

        when: "fill in a record"
        res = RecordNote.tryCreate(rec1, null, null, null)

        then: "validation for info within note is handled in `TempRecordItem`"
        res.status == ResultStatus.CREATED

        when: "add a invalid location"
        res = RecordNote.tryCreate(rec1, null, null, new Location())

        then: "parent is invalid"
        res.status == ResultStatus.UNPROCESSABLE_ENTITY

        when: "fill in location"
        res = RecordNote.tryCreate(rec1, null, null, TestUtils.buildLocation())

        then: "parent becomes valid again"
        res.status == ResultStatus.CREATED
    }

    void "test do not create revision for newly-created note"() {
        given:
        Record rec1 = TestUtils.buildRecord()
        int lBaseline = Location.count()
        int mBaseline = MediaInfo.count()

        when: "an unsaved record note"
        RecordNote note1 = RecordNote.tryCreate(rec1, null).payload
        Result<RecordNote> res = note1.tryCreateRevision()
        RecordNote.withSession { it.flush() }

        then:
        res.status == ResultStatus.OK
        res.payload == note1
        note1.revisions == null
        Location.count() == lBaseline
        MediaInfo.count() == mBaseline

        when: "a valid, saved but not flushed record note"
        note1.save()
        res = note1.tryCreateRevision()

        then:
        res.status == ResultStatus.OK
        res.payload == note1
        note1.revisions == null
    }

    void "test determining if should create revision for an existing note"() {
        given: "a saved valid note"
        MediaElement el1 = TestUtils.buildMediaElement()
        RecordNote note1 = RecordNote.tryCreate(TestUtils.buildRecord(),
            TestUtils.randString(),
            TestUtils.buildMediaInfo(),
            TestUtils.buildLocation()).payload
        note1.author = TestUtils.buildAuthor()
        RecordNote.withSession { it.flush() }

        int initialNumRevisions = note1.revisions?.size() ?: 0
        DateTime initialWhenChanged = note1.whenChanged

        when: "change isDeleted property"
        note1.isDeleted = true
        assert note1.tryCreateRevision().success == true
        note1.save(flush: true, failOnError: true)

        then: "no revision created"
        (note1.revisions?.size() ?: 0) == initialNumRevisions
        note1.whenChanged == initialWhenChanged

        when: "change a non-object property"
        note1.noteContents = "hi! what's up?"
        assert note1.tryCreateRevision().success == true
        note1.save(flush: true, failOnError: true)

        then: "revision created"
        note1.revisions?.size() == initialNumRevisions + 1
        note1.whenChanged.isAfter(initialWhenChanged)

        when: "change location"
        note1.location.address = "hi! what's going on?"
        initialNumRevisions = note1.revisions.size()
        initialWhenChanged = note1.whenChanged

        assert note1.tryCreateRevision().success == true
        note1.save(flush: true, failOnError: true)

        then: "revision created"
        note1.revisions?.size() == initialNumRevisions + 1
        note1.whenChanged.isAfter(initialWhenChanged)

        when: "add to media"
        note1.media.addToMediaElements(el1)

        initialNumRevisions = note1.revisions.size()
        initialWhenChanged = note1.whenChanged
        assert note1.tryCreateRevision().success == true
        note1.save(flush: true, failOnError: true)

        then: "revision created"
        note1.revisions?.size() == initialNumRevisions + 1
        note1.whenChanged.isAfter(initialWhenChanged)

        when: "make no changes after mediaElements in `media` child is initialized"
        initialNumRevisions = note1.revisions.size()
        initialWhenChanged = note1.whenChanged
        assert note1.tryCreateRevision().success == true
        note1.save(flush: true, failOnError: true)

        then: "no revision created"
        note1.revisions.size() == initialNumRevisions
        note1.whenChanged == initialWhenChanged

        when: "remove from media"
        note1.media.removeMediaElement(el1.uid)

        initialNumRevisions = note1.revisions.size()
        initialWhenChanged = note1.whenChanged
        assert note1.tryCreateRevision().success == true
        note1.save(flush: true, failOnError: true)

        then: "revision created"
        note1.revisions?.size() == initialNumRevisions + 1
        note1.whenChanged.isAfter(initialWhenChanged)

        when: "make no changes after mediaElements in `media` child is initialized"
        initialNumRevisions = note1.revisions.size()
        initialWhenChanged = note1.whenChanged
        assert note1.tryCreateRevision().success == true
        note1.save(flush: true, failOnError: true)

        then: "no revision created"
        note1.revisions.size() == initialNumRevisions
        note1.whenChanged == initialWhenChanged
    }
}
