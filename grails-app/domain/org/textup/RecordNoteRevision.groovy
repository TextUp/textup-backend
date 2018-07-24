package org.textup

import grails.compiler.GrailsTypeChecked
import groovy.transform.EqualsAndHashCode
import org.jadira.usertype.dateandtime.joda.PersistentDateTime
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.restapidoc.annotation.*
import org.textup.type.AuthorType
import org.textup.validator.Author
import org.textup.validator.MediaInfo

@EqualsAndHashCode
@RestApiObject(name="RecordNoteRevision",
	description="Previous versions of the note.")
class RecordNoteRevision implements ReadOnlyBaseRecordNote {

    MediaService mediaService

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
            apiFieldName   = "contents",
            description    = "Text of the note",
            allowedType    = "String",
            useForCreation = true)
    String noteContents
    String serializedMedia
    MediaInfo media

    @RestApiObjectFields(params=[
        @RestApiObjectField(
            apiFieldName   = "images",
            description    = "List of image links associated with this revision",
            allowedType    = "List<String>",
            useForCreation = false),
    ])
    static transients = ["media", "mediaService"]
	static belongsTo = [note:RecordNote]
    static constraints = {
    	importFrom RecordItem
    	importFrom RecordNote
    }
    static mapping = {
    	whenChanged type:PersistentDateTime
        noteContents type: "text"
        serializedMedia type: "text"
    }

    // Property Access
    // ---------------

    @GrailsTypeChecked
    MediaInfo getMedia() {
        mediaService.getMedia(this)
    }
}
