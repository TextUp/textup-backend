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

    MediaVersion mediaVersion
    String key
    Long sizeInBytes
    Integer widthInPixels
    Integer heightInPixels

    @RestApiObjectFields(params=[
        @RestApiObjectField(
            apiFieldName   = "link",
            description    = "Link to access this particular version",
            allowedType    =  "String",
            useForCreation = false),
        @RestApiObjectField(
            apiFieldName   = "width",
            description    = "Inherent width the in `w` units as defined in the responsive image spec",
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
        // inherent width in pixels for responsive media, currently only intended for images
        widthInPixels nullable:true, min: 1
        // for pre-loading images in gallery on frontend, currently only intended for images
        heightInPixels nullable:true, min: 1
    }

    // Property access
    // ---------------

    // DON'T prepend the "w" unit for inherent width as called for in the `srcset` attribute
    // becuase we want to give the frontend the flexibility to do number comparisons
    // on these widths without having to manually strip the unit
    Integer getInherentWidth() {
        widthInPixels ?: mediaVersion?.maxWidthInPixels
    }

    URL getLink() {
        Result<URL> res = storageService
            .generateAuthLink(key)
            .logFail('MediaElementVersion.getLink')
        res.success ? res.payload : null
    }
}
