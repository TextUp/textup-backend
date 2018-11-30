package org.textup

import grails.compiler.GrailsTypeChecked
import groovy.transform.EqualsAndHashCode
import org.jadira.usertype.dateandtime.joda.PersistentDateTime
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.restapidoc.annotation.*

@EqualsAndHashCode(callSuper=true)
@RestApiObject(name="RecordNote", description="Notes that are part of the record.")
class RecordNote extends RecordItem implements ReadOnlyRecordNote {

	// whenCreated is used for making notes show up in the correct
	// position in the chronological record, this 'whenChanged' field
	// is when this note actually was created
    @RestApiObjectField(
        description    = "Date this note was actually added to record. For notes, \
            the whenCreated field is used for appropriate ordering in the record.",
        allowedType    = "DateTime",
        useForCreation = false)
	DateTime whenChanged = DateTime.now(DateTimeZone.UTC)

    @RestApiObjectField(
        description    = "Whether this item is deleted",
        allowedType    = "Boolean",
        useForCreation = false)
	boolean isDeleted = false

    @RestApiObjectField(
        description    = "Whether this item is a read-only note",
        allowedType    = "Boolean",
        useForCreation = false)
    boolean isReadOnly = false

    @RestApiObjectField(
        description    = "Location this note is associated with",
        allowedType    = "Location",
        useForCreation = true)
	Location location

    @RestApiObjectField(
        apiFieldName   = "revisions",
        description    = "Previous revisions of this note.",
        allowedType    = "List<RecordNoteRevision>",
        useForCreation = true)
	static hasMany = [revisions:RecordNoteRevision]
    static constraints = {
    	location cascadeValidation: true, nullable: true
    }
    static mapping = {
    	whenChanged type: PersistentDateTime
        location lazy: false, cascade: "all-delete-orphan"
        revisions lazy: false, cascade: "all-delete-orphan"
    }

    // Methods
    // -------

    @GrailsTypeChecked
    Result<RecordNote> tryCreateRevision() {
        if (hasDirtyNonObjectFields() || location?.isDirty() || media?.isDirty()) {
            // update whenChanged timestamp to keep it current for any revisions
            this.whenChanged = DateTime.now(DateTimeZone.UTC)
            // create revision of persistent values
            RecordNoteRevision rev = this.createRevision()
            if (!rev.save()) {
                return IOCUtils.resultFactory.failWithValidationErrors(rev.errors)
            }
        }
        IOCUtils.resultFactory.success(this)
    }

    @GrailsTypeChecked
    protected boolean hasDirtyNonObjectFields() {
        List<String> dirtyProps = this.dirtyPropertyNames
        !dirtyProps.isEmpty() && (dirtyProps.size() > 1 || dirtyProps[0] != "isDeleted")
    }

    @GrailsTypeChecked
    protected RecordNoteRevision createRevision() {
        Closure doGet = { String propName -> this.getPersistentValue(propName) }
    	RecordNoteRevision rev1 = new RecordNoteRevision(authorName:doGet("authorName"),
            authorId: doGet("authorId"),
            authorType: doGet("authorType"),
            whenChanged: doGet("whenChanged"),
            noteContents: doGet("noteContents"))
        Object originalLoc = doGet('location')
        if (originalLoc instanceof Location) {
            rev1.location = originalLoc.tryDuplicatePersistentState()
        }
        Object originalMedia = doGet('media')
        if (originalMedia instanceof MediaInfo) {
            rev1.media = originalMedia.tryDuplicatePersistentState()
        }
    	this.addToRevisions(rev1)
    	rev1
    }

    // Property access
    // ---------------

    @GrailsTypeChecked
    ReadOnlyLocation getReadOnlyLocation() { location }
}
