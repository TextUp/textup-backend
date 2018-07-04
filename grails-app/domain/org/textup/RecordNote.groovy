package org.textup

import com.amazonaws.services.s3.model.PutObjectResult
import grails.compiler.GrailsTypeChecked
import grails.util.Holders
import groovy.transform.EqualsAndHashCode
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.jadira.usertype.dateandtime.joda.PersistentDateTime
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.restapidoc.annotation.*
import org.textup.type.AuthorType
import org.textup.validator.Author
import org.textup.validator.ImageInfo
import org.textup.validator.UploadItem

@EqualsAndHashCode(callSuper=true)
@RestApiObject(name="RecordNote", description="Notes that are part of the record.")
class RecordNote extends RecordItem implements ReadOnlyRecordNote {

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
        description    = "Whether this item is a read-only note",
        allowedType    = "Boolean",
        useForCreation = false)
    boolean isReadOnly = false

    @RestApiObjectField(
        description    = "Contents of the note",
        allowedType    = "String",
        useForCreation = true)
	String noteContents
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
            presentInResponse = false)
    ])
	static transients = ['imageKeys', 'storageService', 'grailsApplication']
    @RestApiObjectField(
        apiFieldName   = "revisions",
        description    = "Previous revisions of this note.",
        allowedType    = "List<RecordNoteRevision>",
        useForCreation = true)
	static hasMany = [revisions:RecordNoteRevision]
    static constraints = {
    	location nullable:true
    	noteContents blank:true, nullable:true, size:1..1000
    	imageKeysAsString blank:true, nullable:true, validator:{ String str, noteOrRevision ->
            if (!str) { // short circuit if no images
                return
            }
            // check duplicates of images
            HashSet<String> existingKeys = new HashSet<>()
            Collection<String> dupKeys = []
            noteOrRevision.imageKeys.each { String key ->
                if (existingKeys.contains(key)) { dupKeys << key }
                else { existingKeys.add(key) }
            }
            if (dupKeys) {
                return ["duplicates", dupKeys]
            }
            // check number of images
            Integer maxNum = Helpers.to(Integer,
                noteOrRevision.grailsApplication.flatConfig["textup.maxNumImages"])
            int numImages = noteOrRevision.imageKeys.size()
            if (numImages > maxNum) {
                return ["tooMany", numImages, maxNum]
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
        Closure doGet = { String propName -> this.getPersistentValue(propName) }
    	RecordNoteRevision rev1 = new RecordNoteRevision(authorName:doGet("authorName"),
			authorId:doGet("authorId"),
			authorType:doGet("authorType"),
			whenChanged:doGet("whenChanged"),
			noteContents:doGet("noteContents"),
			location:doGet("location"),
			imageKeysAsString:doGet("imageKeysAsString"))
    	this.addToRevisions(rev1)
    	rev1
    }
    @GrailsTypeChecked
    Result<PutObjectResult> addImage(UploadItem uItem) {
        String imageKey = UUID.randomUUID().toString(),
            objectKey = Helpers.buildObjectKeyFromImageKey(this.id, imageKey)
        Result<PutObjectResult> res = storageService.upload(objectKey, uItem)
        if (res.success) {
            setImageKeys(this.imageKeys << imageKey)
        }
        res
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

    // Property Access
    // ---------------

    @GrailsTypeChecked
    Collection<String> getImageKeys() {
    	if (!this.imageKeysAsString) {
        	return []
    	}
        try {
            Helpers.toJson(this.imageKeysAsString) as Collection<String>
        }
        catch (Throwable e) {
            log.error("RecordNote.getImageKeys: invalid json string '${this.imageKeysAsString}'")
            e.printStackTrace()
            []
        }
    }
    @GrailsTypeChecked
    void setImageKeys(Collection<String> imageKeys) {
    	if (imageKeys != null) {
    		this.imageKeysAsString = Helpers.toJsonString(imageKeys)
    	}
    }
    @GrailsTypeChecked
    Collection<ImageInfo> getImages() {
        Helpers.buildImagesFromImageKeys(storageService, this.id, this.imageKeys)
    }
}
