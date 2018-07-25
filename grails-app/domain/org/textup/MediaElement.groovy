package org.textup

import grails.compiler.GrailsCompileStatic
import groovy.transform.EqualsAndHashCode
import org.restapidoc.annotation.*
import org.textup.type.*

@GrailsCompileStatic
@EqualsAndHashCode
@RestApiObject(
    name        = "MediaElement",
    description = "A media element contained within a media info object contains various versions optimized for sending or display")
class MediaElement {

    ResultFactory resultFactory

    @RestApiObjectField(
        description    = "unique id for this media element, used for deletion",
        allowedType    = "String",
        useForCreation = false)
    String uid = UUID.randomUUID().toString()

    @RestApiObjectField(
        description    = "Type of media this element represents. Currently only IMAGE",
        allowedType    = "String",
        useForCreation = false)
    MediaType type

    MediaElementVersion sendVersion

    @RestApiObjectFields(params=[
         @RestApiObjectField(
            apiFieldName   = "small",
            description    = "version for display on small screens",
            allowedType    = "MediaElementVersion",
            useForCreation = false),
         @RestApiObjectField(
            apiFieldName   = "large",
            description    = "version for display on large screens",
            allowedType    = "MediaElementVersion",
            useForCreation = false),
         @RestApiObjectField(
            apiFieldName   = "large",
            description    = "version for display on large screens",
            allowedType    = "MediaElementVersion",
            useForCreation = false)
    ])
    static transients = ["resultFactory"]
    static hasMany = [displayVersions: MediaElementVersion]
    static constraints = { // all nullable:false by default
        sendVersion validator: { MediaElementVersion send1 ->
            if (send1?.sizeInBytes > Constants.MAX_MEDIA_SIZE_PER_MESSAGE_IN_BYTES) {
                return ["sizeTooBig"]
            }
        }
    }
    static mapping = {
        versions lazy: false, cascade: "save-update"
    }

    // Static factory methods
    // ----------------------

    static Result<MediaElement> create(String mimeType, List<UploadItem> uItems) {
        MediaElement e1 = new MediaElement(type: MediaType.convertMimeType(mimeType))
        List<Result<MediaElement>> failRes = []
        uItems.each { UploadItem uItem ->
            Result<MediaElementVersion> res = e1.addVersion(uItem)
            if (!res.success) { failRes << res }
        }
        if (failRes) {
            resultFactory.failWithResultsAndStatus(failRes, ResultStatus.UNPROCESSABLE_ENTITY)
        }
        else if (e1.save()) {
            resultFactory.success(e1)
        }
        else { resultFactory.failWithValidationErrors(e1.errors) }
    }

    // Methods
    // -------

    Result<MediaElementVersion> addVersion(UploadItem uItem) {
        MediaElementVersion vers1 = new MediaElementVersion(version: uItem.version,
            key: uItem.key,
            sizeInBytes: uItem.sizeInBytes,
            widthInPixels: uItem.widthInPixels)
        if (uItem.version == MediaVersion.SEND) {
            sendVersion = vers1
        }
        else { addToVersions(vers1) }
        vers1.save() ? resultFactory.success(vers1) : resultFactory.failWithValidationErrors(vers1.errors)
    }

    // Property access
    // ---------------

    long getSendVersionSizeInBytes() {
        vMap[MediaVersion.SEND]?.sizeInBytes
    }
    Map<MediaVersion, MediaElementVersion> getDisplayVersions() {
        displayVersions?.collectEntries { MediaElementVersion vers1 -> [(vers1.version): vers1] } ?:
            [(MediaVersion.LARGE):sendVersion]
    }
}