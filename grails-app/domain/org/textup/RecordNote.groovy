package org.textup

import grails.compiler.GrailsTypeChecked
import groovy.transform.EqualsAndHashCode
import org.jadira.usertype.dateandtime.joda.PersistentDateTime
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.restapidoc.annotation.*
import org.textup.util.*

@GrailsTypeChecked
@EqualsAndHashCode(callSuper = true)
class RecordNote extends RecordItem implements ReadOnlyRecordNote {

	// whenCreated is used for making notes show up in the correct
	// position in the chronological record, this `whenChanged` field
	// is when this note actually was created
	DateTime whenChanged = DateTimeUtils.now()

    boolean isDeleted = false
    Location location
    boolean isReadOnly = false

	static hasMany = [revisions: RecordNoteRevision]
    static mapping = {
        whenChanged type: PersistentDateTime
        location lazy: false, cascade: "all-delete-orphan"
        revisions lazy: false, cascade: "all-delete-orphan"
    }
    static constraints = {
    	location cascadeValidation: true, nullable: true
    }

    static Result<RecordNote> tryCreate(PhoneRecordWrapper w1, TempRecordItem temp1) {
        w1.tryGetRecord()
            .then { Record rec1 ->
                RecordNote rNote1 = new RecordNote(record: rec1,
                    noteContents: temp1.text,
                    media: temp1.media,
                    location: temp1.location)
                DomainUtils.trySave(rNote1, ResultStatus.CREATED)
            }
    }

    // Methods
    // -------

    Result<RecordNote> tryCreateRevision() {
        if (DomainUtils.hasDirtyNonObjectFields(this, ["isDeleted"]) ||
            location?.isDirty() || media?.isDirty()) {
            // update whenChanged timestamp to keep it current for any revisions
            whenChanged = DateTimeUtils.now()
            // create revision of persistent values
            RecordNoteRevision rev = createRevision()
            if (!rev.save()) {
                return IOCUtils.resultFactory.failWithValidationErrors(rev.errors)
            }
        }
        IOCUtils.resultFactory.success(this)
    }

    // Properties
    // ----------

    @Override
    ReadOnlyLocation getReadOnlyLocation() { location }

    // Helpers
    // -------

    protected RecordNoteRevision createRevision() {
        Closure doGet = { String propName -> getPersistentValue(propName) }
        RecordNoteRevision rev1 = new RecordNoteRevision(authorName: doGet("authorName"),
            authorId: doGet("authorId"),
            authorType: doGet("authorType"),
            whenChanged: doGet("whenChanged"),
            noteContents: doGet("noteContents"))
        Object originalLoc = doGet("location")
        if (originalLoc instanceof Location) {
            rev1.location = originalLoc.tryDuplicatePersistentState()
        }
        Object originalMedia = doGet("media")
        if (originalMedia instanceof MediaInfo) {
            rev1.media = originalMedia.tryDuplicatePersistentState()
        }
        addToRevisions(rev1)
        rev1
    }
}
