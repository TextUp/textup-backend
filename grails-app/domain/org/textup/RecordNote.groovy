package org.textup

import com.amazonaws.HttpMethod
import grails.compiler.GrailsTypeChecked
import grails.util.Holders
import groovy.transform.EqualsAndHashCode
import java.util.UUID
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.jadira.usertype.dateandtime.joda.PersistentDateTime
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.restapidoc.annotation.*
import org.textup.types.AuthorType
import org.textup.validator.Author

@EqualsAndHashCode(callSuper=true)
@RestApiObject(name="RecordNote", description="Notes that are part of the record.")
class RecordNote extends RecordItem {

    GrailsApplication grailsApplication
	StorageService storageService

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
        description    = "Text of the note",
        allowedType    = "String",
        useForCreation = true)
	String contents
    @RestApiObjectField(
        description    = "Location this note is associated with",
        allowedType    = "Location",
        useForCreation = true)
	Location location
	String imageKeysAsString

    @RestApiObjectFields(params=[
        @RestApiObjectField(
            apiFieldName   = "images",
            description    = "List of image keys and image links associated with this note",
            allowedType    = "List<Map>",
            useForCreation = false),
        @RestApiObjectField(
            apiFieldName      = "doImageActions",
            description       = "List of actions to perform on this note related to images",
            allowedType       = "List<[noteImageAction]>",
            useForCreation    = false,
            presentInResponse = false),
        @RestApiObjectField(
            apiFieldName      = "uploadImages",
            description       = "List of urls to PUT or upload images to in the same order \
                that the metadata for each image was originally passed in",
            allowedType       = "List<String>",
            useForCreation    = false,
            presentInResponse = false)
    ])
	static transients = ['imageKeys', 'storageService', 'grailsApplication']
    @RestApiObjectField(
        description    = "Previous revisions of this note.",
        allowedType    = "List<RecordNoteRevision>",
        useForCreation = true)
	static hasMany = [revisions:RecordNoteRevision]
    static constraints = {
    	location nullable:true
    	contents blank:true, nullable:true, size:1..1000
    	imageKeysAsString blank:true, nullable:true, validator:{ String str, noteOrRevision ->
            if (!str) { // short circuit if no images
                return
            }
            Integer maxNum = Helpers.toInteger(
                noteOrRevision.grailsApplication.flatConfig['textup.maxNumImages'])
            int numImages = noteOrRevision.imageKeys.size()
            if (numImages > maxNum) {
                ['tooMany', numImages, maxNum]
            }
        }
    }
    static mapping = {
    	whenChanged type:PersistentDateTime
        location lazy:false, cascade:"all-delete-orphan"
        revisions lazy:false, cascade:"all-delete-orphan"
    }

    // Methods
    // -------

    @GrailsTypeChecked
    RecordNoteRevision createRevision() {
    	RecordNoteRevision rev1 = new RecordNoteRevision(authorName:this.authorName,
			authorId:this.authorId,
			authorType:this.authorType,
			whenChanged:this.whenChanged,
			contents:this.contents,
			location:this.location,
			imageKeysAsString:this.imageKeysAsString)
    	this.addToRevisions(rev1)
    	rev1
    }
    @GrailsTypeChecked
    String addImage(String mimeType, Long numBytes) {
        String imageKey = UUID.randomUUID().toString(),
            objectKey = buildObjectKeyFromImageKey(imageKey)
        storageService.generateAuthLink(objectKey, HttpMethod.PUT,
            ['Content-Type':mimeType, 'Content-Length':numBytes])
            .logFail('RecordNote.addImage')
            .then { URL link ->
                setImageKeys(this.imageKeys << imageKey)
                link.toString()
            }
    }
    @GrailsTypeChecked
    String removeImage(String imageKey) {
    	Collection<String> keys = this.imageKeys
    	if (keys.contains(imageKey)) {
            keys.remove(imageKey)
    		setImageKeys(keys)
    		imageKey
    	}
    	else { null }
    }
    @GrailsTypeChecked
    protected String buildObjectKeyFromImageKey(String imageKey) {
    	"note-${this.id}/${imageKey}"
    }

    // Property Access
    // ---------------

    @GrailsTypeChecked
    Collection<Map<String,String>> getImages() {
        this.imageKeys.collect { String imageKey ->
            String objectKey = this.buildObjectKeyFromImageKey(imageKey)
            Result<URL> res = storageService
                .generateAuthLink(objectKey, HttpMethod.GET)
                .logFail('RecordNote.getImageLinks')
            [key:imageKey, link:(res.success ? res.payload.toString() : "")]
        } as Collection<Map<String,String>>
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
            log.error("RecordNote.getImageKeys: invalid json string '${this.imageKeysAsString}'")
            []
        }
    }
    @GrailsTypeChecked
    void setImageKeys(Collection<String> imageKeys) {
    	if (imageKeys != null) {
    		this.imageKeysAsString = Helpers.toJsonString(imageKeys)
    	}
    }
}
