package org.textup

import grails.compiler.GrailsTypeChecked
import groovy.transform.EqualsAndHashCode
import org.jadira.usertype.dateandtime.joda.PersistentDateTime
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*

@EqualsAndHashCode
class RecordNoteRevision implements ReadOnlyRecordNoteRevision, WithId, CanSave<RecordNoteRevision> {

    AuthorType authorType
    DateTime whenChanged
    Location location
    Long authorId
    String authorName
    MediaInfo media
    String noteContents

	static belongsTo = [note: RecordNote]
    static mapping = {
        whenChanged type: PersistentDateTime
        noteContents type: "text"
        location lazy: false, cascade: "save-update"
        media lazy: false, cascade: "save-update"
    }
    static constraints = {
    	importFrom RecordItem
    	importFrom RecordNote
    }

    static Result<RecordNoteRevision> tryCreate(RecordNote rNote1) {
        RecordNoteRevision rev1 = new RecordNoteRevision(
            authorName: rNote1?.getPersistentValue("authorName"),
            authorId: rNote1?.getPersistentValue("authorId"),
            authorType: rNote1?.getPersistentValue("authorType"),
            whenChanged: rNote1?.getPersistentValue("whenChanged"),
            noteContents: rNote1?.getPersistentValue("noteContents"))
        Object originalLoc = rNote1?.getPersistentValue("location")
        if (originalLoc instanceof Location) {
            rev1.location = originalLoc.tryDuplicatePersistentState()
        }
        Object originalMedia = rNote1?.getPersistentValue("media")
        if (originalMedia instanceof MediaInfo) {
            rev1.media = originalMedia.tryDuplicatePersistentState()
        }
        rNote1?.addToRevisions(rev1)
        DomainUtils.trySave(rev1, ResultStatus.CREATED)
            .ifFail { Result<?> failRes ->
                rNote1?.removeFromRevisions(rev1)
                rev1.discard()
                failRes
            }
    }

    // Properties
    // ----------

    @GrailsTypeChecked
    @Override
    ReadOnlyLocation getReadOnlyLocation() { location }

    @GrailsTypeChecked
    @Override
    ReadOnlyMediaInfo getReadOnlyMedia() { media }
}
