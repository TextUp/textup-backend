package org.textup

import grails.compiler.GrailsTypeChecked
import groovy.transform.EqualsAndHashCode
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.jadira.usertype.dateandtime.joda.PersistentDateTime
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.restapidoc.annotation.*
import org.textup.type.AuthorType
import org.textup.validator.Author
import org.textup.validator.ImageInfo

@EqualsAndHashCode
@RestApiObject(name="RecordNoteRevision",
	description="Previous versions of the note.")
class RecordNoteRevision {

    StorageService storageService
    // for executing imported RecordNote constraints
    GrailsApplication grailsApplication

    @RestApiObjectField(
        description    = "When this revision happened",
        allowedType    = "DateTime",
        useForCreation = false)
	DateTime whenChanged

	String noteContents
    @RestApiObjectField(
        description    = "Location this revision is associated with",
        allowedType    = "Location",
        useForCreation = true)
	Location location
	String imageKeysAsString

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

    @RestApiObjectFields(params=[
        @RestApiObjectField(
            apiFieldName   = "contents",
            description    = "Text of the note",
            allowedType    = "String",
            useForCreation = true),
        @RestApiObjectField(
            apiFieldName   = "images",
            description    = "List of image links associated with this revision",
            allowedType    = "List<String>",
            useForCreation = false),
    ])
    static transients = ['storageService', 'grailsApplication']
	static belongsTo = [note:RecordNote]
    static constraints = {
    	importFrom RecordItem
    	importFrom RecordNote
    }
    static mapping = {
    	whenChanged type:PersistentDateTime
    }

    // Methods
    // -------

    // Property Access
    // ---------------

    @GrailsTypeChecked
    Collection<ImageInfo> getImages() {
        Helpers.buildImagesFromImageKeys(storageService, this.note.id, this.imageKeys)
    }
    @GrailsTypeChecked
    Collection<String> getImageKeys() {
    	if (!this.imageKeysAsString) {
        	return []
    	}
        try {
            Helpers.toJson(this.imageKeysAsString) as Collection<String>
        }
        catch (e) {
            log.error("RecordNoteRevision.getImageKeys: \
            	invalid json string '${this.imageKeysAsString}'")
            []
        }
    }
}
