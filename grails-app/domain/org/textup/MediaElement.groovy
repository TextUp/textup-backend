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

    MediaElementVersion sendVersion

    @RestApiObjectFields(params=[
         @RestApiObjectField(
            apiFieldName   = "versions",
            description    = "various versions for display",
            allowedType    = "MediaElementVersion",
            useForCreation = false)
    ])
    static hasMany = [alternateVersions: MediaElementVersion]
    static constraints = { // all nullable:false by default
        sendVersion cascadeValidation: true, validator: { MediaElementVersion send1 ->
            if (send1?.sizeInBytes > Constants.MAX_MEDIA_SIZE_PER_MESSAGE_IN_BYTES) {
                return ["sizeTooBig"]
            }
        }
        alternateVersions nullable: true, cascadeValidation: true
    }
    static mapping = {
        sendVersion lazy: false, cascade: "save-update"
        alternateVersions lazy: false, cascade: "save-update"
    }

    // Static factory methods
    // ----------------------

    static Result<MediaElement> create(UploadItem sVersion, List<UploadItem> alternates) {
        MediaElement e1 = new MediaElement(sendVersion: sVersion.toMediaElementVersion())
        alternates.each { UploadItem uItem -> addToAlternateVersions(uItem.toMediaElementVersion()) }

        if (e1.save()) {
            Helpers.resultFactory.success(e1)
        }
        else { Helpers.resultFactory.failWithValidationErrors(e1.errors) }
    }

    // Methods
    // -------

    boolean hasType(Collection<MediaType> typesToCheckFor) {
        HashSet<MediaType> allTypes = getAllTypes()
        typesToCheckFor.any { MediaType t1 -> allTypes.contains(t1) }
    }

    // Property access
    // ---------------

    HashSet<MediaType> getAllTypes() {
        HashSet<MediaType> allTypes = new HashSet<>()
        getAllVersions().collect { MediaElementVersion v1 -> allTypes << v1.type }
        allTypes
    }

    List<MediaElementVersion> getAllVersions() {
        List<MediaElementVersion> allVersions = [sendVersion]
        alternateVersions?.each { MediaElementVersion vers1 -> allVersions << vers1 }
        allVersions
    }
}
