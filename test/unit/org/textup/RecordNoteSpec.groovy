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
        Record rec1 = new Record()
        rec1.save(flush: true, failOnError: true)

    	when: "empty note"
        RecordNote note1 = new RecordNote()

    	then: "invalid"
        note1.validate() == false
        note1.errors.getFieldErrorCount("record") == 1

        when: "fill in a record"
        note1.record = rec1

        then:
        note1.validate() == true

        when: "add a invalid location"
        note1.location = new Location()

        then: "parent is invalid"
        note1.validate() == false
        note1.errors.getFieldErrorCount("location.address") == 1
        note1.errors.getFieldErrorCount("location.lat") == 1
        note1.errors.getFieldErrorCount("location.lng") == 1

        when: "fill in location"
        note1.location.address = "hi"
        note1.location.lat = 0G
        note1.location.lng = 0G

        then: "parent becomes valid again"
        note1.validate() == true
    }

    void "test do not create revision for newly-created note"() {
        given:
        Record rec = new Record()
        rec.save(flush: true, failOnError: true)
        int lBaseline = Location.count()
        int mBaseline = MediaInfo.count()

        when: "an unsaved record note"
        RecordNote note1 = new RecordNote(record: rec)
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
        Record rec = new Record()
        rec.save(flush: true, failOnError: true)
        RecordNote note1 = new RecordNote(record: rec)
        note1.authorName = "hello"
        note1.authorId = 88L
        note1.authorType = AuthorType.STAFF
        note1.noteContents = "hello there!"
        note1.location = new Location(address: "hi", lat: 0G, lng: 0G)
        note1.media = new MediaInfo()
        assert note1.save(flush: true, failOnError: true)
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
        MediaElement e1 = TestUtils.buildMediaElement()
        note1.media.addToMediaElements(e1)

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
        note1.media.removeMediaElement(e1.uid)

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
