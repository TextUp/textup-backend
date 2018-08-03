package org.textup

import grails.compiler.GrailsTypeChecked
import groovy.transform.EqualsAndHashCode
import org.restapidoc.annotation.*
import org.textup.type.*
import org.textup.validator.UploadItem

@GrailsTypeChecked
@EqualsAndHashCode
@RestApiObject(
    name        = "MediaElement",
    description = "A media element contained within a media info object contains various versions optimized for sending or display")
class MediaElement implements ReadOnlyMediaElement {

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
    static hasMany = [displayVersions: MediaElementVersion]
    static constraints = { // all nullable:false by default
        sendVersion cascadeValidation: true, validator: { MediaElementVersion send1 ->
            if (send1?.sizeInBytes > Constants.MAX_MEDIA_SIZE_PER_MESSAGE_IN_BYTES) {
                return ["sizeTooBig"]
            }
        }
        displayVersions nullable: true, cascadeValidation: true
    }
    static mapping = {
        sendVersion lazy: false, cascade: "save-update"
        displayVersions lazy: false, cascade: "save-update"
    }

    // Static factory methods
    // ----------------------

    static Result<MediaElement> create(String mimeType, List<UploadItem> uItems) {
        MediaElement e1 = new MediaElement(type: MediaType.convertMimeType(mimeType))
        List<Result<MediaElementVersion>> failRes = []
        uItems.each { UploadItem uItem ->
            Result<MediaElementVersion> res = e1.addVersion(uItem)
            if (!res.success) { failRes << res }
        }
        if (failRes) {
            Helpers.resultFactory.failWithResultsAndStatus(failRes, ResultStatus.UNPROCESSABLE_ENTITY)
        }
        else if (e1.save()) {
            Helpers.resultFactory.success(e1)
        }
        else { Helpers.resultFactory.failWithValidationErrors(e1.errors) }
    }

    // Methods
    // -------

    Result<MediaElementVersion> addVersion(UploadItem uItem) {
        MediaElementVersion vers1 = new MediaElementVersion(mediaVersion: uItem.mediaVersion,
            key: uItem.key,
            sizeInBytes: uItem.sizeInBytes,
            widthInPixels: uItem.widthInPixels)
        if (uItem.mediaVersion == MediaVersion.SEND) {
            sendVersion = vers1
        }
        else { addToDisplayVersions(vers1) }
        vers1.save() ? Helpers.resultFactory.success(vers1) :
            Helpers.resultFactory.failWithValidationErrors(vers1.errors)
    }

    // Property access
    // ---------------

    Map<MediaVersion, MediaElementVersion> getVersionsForDisplay() {
        Map<MediaVersion, MediaElementVersion> vMap = [:]
        displayVersions?.each { MediaElementVersion vers1 -> vMap[vers1.mediaVersion] = vers1 }
        vMap ?: [(MediaVersion.LARGE): sendVersion]
    }
}
