package org.textup

import grails.compiler.GrailsCompileStatic
import groovy.transform.EqualsAndHashCode
import org.jadira.usertype.dateandtime.joda.PersistentDateTime
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.restapidoc.annotation.*
import org.textup.type.AuthorType
import org.textup.validator.Author

@EqualsAndHashCode
@RestApiObject(
    name        = "RecordNoteRevision",
    description = "Previous versions of the note.")
class RecordNoteRevision implements ReadOnlyRecordNoteRevision {

    @RestApiObjectField(
        description    = "When this revision happened",
        allowedType    = "DateTime",
        useForCreation = false)
	DateTime whenChanged

    @RestApiObjectField(
        description    = "Location this revision is associated with",
        allowedType    = "Location",
        useForCreation = true)
	Location location

    @RestApiObjectField(
        description    = "Author of this entry.",
        useForCreation = false)
	String authorName
    @RestApiObjectField(
        description    = "Id of the author of this entry.",
        allowedType    = "Number",
        useForCreation = false)
	Long authorId
    @RestApiObjectField(
        description    = "Type of author for this item",
        allowedType    = "AuthorType",
        useForCreation = false)
	AuthorType authorType

    @RestApiObjectField(
        description    = "Text of the note",
        allowedType    = "String",
        useForCreation = true)
    String noteContents

    @RestApiObjectField(
        description    = "Media associated with this revision",
        allowedType    = "MediaInfo",
        useForCreation = false)
    MediaInfo media

	static belongsTo = [note:RecordNote]
    static constraints = {
    	importFrom RecordItem
    	importFrom RecordNote
    }
    static mapping = {
    	whenChanged type: PersistentDateTime
        noteContents type: "text"
        location lazy: false, cascade: "save-update"
        media lazy: false, cascade: "save-update"
    }

    @GrailsCompileStatic
    ReadOnlyLocation getReadOnlyLocation() { location }

    @GrailsCompileStatic
    ReadOnlyMediaInfo getReadOnlyMedia() { media }
}
