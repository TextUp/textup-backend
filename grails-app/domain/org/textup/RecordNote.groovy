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

@EqualsAndHashCode(callSuper = true)
@GrailsTypeChecked
class RecordNote extends RecordItem implements ReadOnlyRecordNote {

	// whenCreated is used for making notes show up in the correct
	// position in the chronological record, this `whenChanged` field
	// is when this note actually was created
	DateTime whenChanged = JodaUtils.utcNow()

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
        revisions cascadeValidation: true
    }

    static Result<RecordNote> tryCreate(Record rec1, TempRecordItem temp1) {
        RecordNote rNote1 = new RecordNote(record: rec1,
            noteContents: temp1.text,
            media: temp1.media,
            location: temp1.location)
        DomainUtils.trySave(rNote1, ResultStatus.CREATED)
    }

    // Methods
    // -------

    Result<RecordNote> tryCreateRevision() {
        if (DomainUtils.hasDirtyNonObjectFields(this, ["isDeleted"]) ||
            location?.isDirty() || media?.isDirty()) {
            // update whenChanged timestamp to keep it current for any revisions
            whenChanged = JodaUtils.utcNow()
            // create revision of persistent values
            RecordNoteRevision.tryCreate(this)
                .then { DomainUtils.trySave(this) }
        }
        else { IOCUtils.resultFactory.success(this) }
    }

    // Properties
    // ----------

    @Override
    ReadOnlyLocation getReadOnlyLocation() { location }
}
