package org.textup

import com.amazonaws.services.s3.model.PutObjectResult
import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import java.nio.charset.StandardCharsets
import org.apache.commons.codec.binary.Base64
import org.apache.commons.codec.digest.DigestUtils
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.joda.time.DateTime
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*
import spock.lang.*

@Domain([Record, RecordItem, RecordText, RecordCall, RecordNote, RecordNoteRevision,
    RecordItemReceipt, Location, MediaInfo, MediaElement, MediaElementVersion])
@TestMixin(HibernateTestMixin)
@Unroll
class RecordNoteSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        Helpers.metaClass.'static'.getResultFactory = TestHelpers.getResultFactory(grailsApplication)
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
        note1.errors.getFieldErrorCount("location.lon") == 1

        when: "fill in location"
        note1.location.address = "hi"
        note1.location.lat = 0G
        note1.location.lon = 0G

        then: "parent becomes valid again"
        note1.validate() == true
    }

    void "test creating a revision"() {
    	given: "a valid note"
        Record rec = new Record()
        rec.save(flush: true, failOnError: true)
        RecordNote note1 = new RecordNote(record:rec)
        note1.authorName = "hello"
        note1.authorId = 88L
        note1.authorType = AuthorType.STAFF
        note1.noteContents = "hello there!"
        note1.location = new Location(address: "hi", lat: 0G, lon: 0G)
        note1.media = new MediaInfo()
        assert note1.validate()

    	when: "creating a revision for an unsaved note"
        RecordNoteRevision rev1 = note1.createRevision()

    	then: "null because revisions use PERSISTED values"
        rev1.authorName == null
        rev1.authorId == null
        rev1.authorType == null
        rev1.whenChanged == null
        rev1.noteContents == null
        rev1.location == null
        rev1.media == null

        when: "creating a revision for a saved note"
        note1.removeFromRevisions(rev1)
        rev1.discard()
        note1.save(flush:true, failOnError:true)
        int lBaseline = Location.count()
        int mBaseline = MediaInfo.count()
        rev1 = note1.createRevision()
        RecordNote.withSession { it.flush() }

        then: "revision created but no media because media is still empty"
        rev1.authorName == note1.authorName
        rev1.authorId == note1.authorId
        rev1.authorType == note1.authorType
        rev1.whenChanged == note1.whenChanged
        rev1.noteContents == note1.noteContents
        rev1.location instanceof Location
        rev1.location.id != note1.location.id
        note1.media.id != null
        rev1.media == null
        Location.count() == lBaseline + 1
        MediaInfo.count() == mBaseline

        when: "add some media elements to the media"
        MediaElement e1 = new MediaElement()
        e1.type = MediaType.IMAGE
        e1.sendVersion = new MediaElementVersion(mediaVersion: MediaVersion.SEND,
            key: UUID.randomUUID().toString(),
            sizeInBytes: Constants.MAX_MEDIA_SIZE_PER_MESSAGE_IN_BYTES / 2,
            widthInPixels: 888)
        note1.media.addToMediaElements(e1)
        note1.save(flush:true, failOnError:true)
        rev1 = note1.createRevision()
        RecordNote.withSession { it.flush() }

        then: "revision created this time has all fields, including media, populated"
        rev1.authorName == note1.authorName
        rev1.authorId == note1.authorId
        rev1.authorType == note1.authorType
        rev1.whenChanged == note1.whenChanged
        rev1.noteContents == note1.noteContents
        rev1.location instanceof Location
        rev1.location.id != note1.location.id
        rev1.media instanceof MediaInfo
        rev1.media.id != note1.media.id
        Location.count() == lBaseline + 2
        MediaInfo.count() == mBaseline + 1
    }

    void "test do not create revision for newly-created note"() {
        given:
        Record rec = new Record()
        rec.save(flush: true, failOnError: true)
        int lBaseline = Location.count()
        int mBaseline = MediaInfo.count()

        when: "an unsaved record note"
        RecordNote note1 = new RecordNote(record:rec)
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
        RecordNote note1 = new RecordNote(record:rec)
        note1.authorName = "hello"
        note1.authorId = 88L
        note1.authorType = AuthorType.STAFF
        note1.noteContents = "hello there!"
        note1.location = new Location(address: "hi", lat: 0G, lon: 0G)
        note1.media = new MediaInfo()
        assert note1.save(flush: true, failOnError: true)
        int initialNumRevisions = note1.revisions?.size() ?: 0
        DateTime initialWhenChanged = note1.whenChanged

        when: "change isDeleted property"
        note1.isDeleted = true
        assert note1.hasDirtyNonObjectFields() == false
        assert note1.tryCreateRevision().success == true
        note1.save(flush: true, failOnError: true)

        then: "no revision created"
        (note1.revisions?.size() ?: 0) == initialNumRevisions
        note1.whenChanged == initialWhenChanged

        when: "change a non-object property"
        note1.noteContents = "hi! what's up?"
        assert note1.hasDirtyNonObjectFields() == true
        assert note1.tryCreateRevision().success == true
        note1.save(flush: true, failOnError: true)

        then: "revision created"
        note1.revisions?.size() == initialNumRevisions + 1
        note1.whenChanged.isAfter(initialWhenChanged)

        when: "change location"
        note1.location.address = "hi! what's going on?"
        initialNumRevisions = note1.revisions.size()
        initialWhenChanged = note1.whenChanged

        assert note1.hasDirtyNonObjectFields() == false
        assert note1.tryCreateRevision().success == true
        note1.save(flush: true, failOnError: true)

        then: "revision created"
        note1.revisions?.size() == initialNumRevisions + 1
        note1.whenChanged.isAfter(initialWhenChanged)

        when: "add to media media"
        MediaElement e1 = new MediaElement()
        e1.type = MediaType.IMAGE
        e1.sendVersion = new MediaElementVersion(mediaVersion: MediaVersion.SEND,
            key: UUID.randomUUID().toString(),
            sizeInBytes: Constants.MAX_MEDIA_SIZE_PER_MESSAGE_IN_BYTES / 2,
            widthInPixels: 888)
        note1.media.addToMediaElements(e1)

        initialNumRevisions = note1.revisions.size()
        initialWhenChanged = note1.whenChanged
        assert note1.hasDirtyNonObjectFields() == false
        assert note1.tryCreateRevision().success == true
        note1.save(flush: true, failOnError: true)

        then: "revision created"
        note1.revisions?.size() == initialNumRevisions + 1
        note1.whenChanged.isAfter(initialWhenChanged)

        when: "make no changes after mediaElements in `media` child is initialized"
        initialNumRevisions = note1.revisions.size()
        initialWhenChanged = note1.whenChanged
        assert note1.hasDirtyNonObjectFields() == false
        assert note1.tryCreateRevision().success == true
        note1.save(flush: true, failOnError: true)

        then: "no revision created"
        note1.revisions.size() == initialNumRevisions
        note1.whenChanged == initialWhenChanged

        when: "remove from media"
        note1.media.removeMediaElement(e1.uid)

        initialNumRevisions = note1.revisions.size()
        initialWhenChanged = note1.whenChanged
        assert note1.hasDirtyNonObjectFields() == false
        assert note1.tryCreateRevision().success == true
        note1.save(flush: true, failOnError: true)

        then: "revision created"
        note1.revisions?.size() == initialNumRevisions + 1
        note1.whenChanged.isAfter(initialWhenChanged)

        when: "make no changes after mediaElements in `media` child is initialized"
        initialNumRevisions = note1.revisions.size()
        initialWhenChanged = note1.whenChanged
        assert note1.hasDirtyNonObjectFields() == false
        assert note1.tryCreateRevision().success == true
        note1.save(flush: true, failOnError: true)

        then: "no revision created"
        note1.revisions.size() == initialNumRevisions
        note1.whenChanged == initialWhenChanged
    }
}
