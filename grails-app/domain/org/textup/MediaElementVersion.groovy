package org.textup

import grails.compiler.GrailsTypeChecked
import groovy.transform.EqualsAndHashCode
import org.restapidoc.annotation.*
import org.textup.type.*

@GrailsTypeChecked
@EqualsAndHashCode
@RestApiObject(
    name        = "MediaElementVersion",
    description = "A version of media optimized for either sending or displaying on a particular device size")
class MediaElementVersion implements ReadOnlyMediaElementVersion {

    StorageService storageService

    String versionId
    Long sizeInBytes
    Integer widthInPixels
    Integer heightInPixels

    @RestApiObjectField(
        description    = "MIME type, if available. Currently: `image/jpeg`, `image/png`, `image/gif`, \
            `audio/mpeg`, `audio/mp3`, `audio/ogg`, `audio/ogg;codecs=opus`, `audio/ogg; codecs=opus`, \
            `audio/webm`, `audio/webm;codecs=opus`, `audio/webm; codecs=opus`",
        allowedType    = "String",
        useForCreation = false)
    MediaType type

    @RestApiObjectFields(params = [
        @RestApiObjectField(
            apiFieldName   = "link",
            description    = "Link to access this particular version",
            allowedType    = "String",
            useForCreation = false),
        @RestApiObjectField(
            apiFieldName   = "width",
            description    = "Inherent width the in `w` units as defined in the responsive image spec, if available",
            allowedType    = "Integer",
            useForCreation = false),
        @RestApiObjectField(
            apiFieldName   = "height",
            description    = "Height of the image in pixels, if available",
            allowedType    = "Integer",
            useForCreation = false)
    ])
    static transients = ["storageService"]
    static constraints = { // all nullable:false by default
        sizeInBytes min: 1l
        // inherent width in pixels for responsive media, intended for images
        widthInPixels nullable:true, min: 1
        // for pre-loading images in gallery on frontend, intended for images
        heightInPixels nullable:true, min: 1
    }

    // Property access
    // ---------------

    URL getLink() {
        Result<URL> res = storageService
            .generateAuthLink(versionId)
            .logFail('MediaElementVersion.getLink')
        res.success ? res.payload : null
    }
}
