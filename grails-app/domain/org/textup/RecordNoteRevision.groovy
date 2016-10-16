package org.textup

import com.amazonaws.HttpMethod
import grails.compiler.GrailsTypeChecked
import groovy.transform.EqualsAndHashCode
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.jadira.usertype.dateandtime.joda.PersistentDateTime
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.restapidoc.annotation.*
import org.textup.types.AuthorType
import org.textup.validator.Author

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

    @RestApiObjectField(
        description    = "Text of the revision",
        allowedType    = "String",
        useForCreation = true)
	String contents
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
    Collection<String> getImageLinks() {
        (this.imageKeys
            .collect(this.note.&buildObjectKeyFromImageKey) as Collection<String>)
            .collect { String objectKey ->
                Result<URL> res = storageService
                    .generateAuthLink(objectKey, HttpMethod.GET)
                    .logFail('RecordNoteRevision.getImageLinks')
                res.success ? res.payload.toString() : ""
            } as Collection<String>
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
