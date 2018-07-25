package org.textup

import grails.compiler.GrailsCompileStatic
import groovy.transform.EqualsAndHashCode
import org.restapidoc.annotation.*
import org.textup.type.*

@GrailsCompileStatic
@EqualsAndHashCode
@RestApiObject(
    name        = "MediaElementVersion",
    description = "A version of media optimized for either sending or displaying on a particular device size")
class MediaElementVersion implements ReadOnlyMediaElementVersion {

    StorageService storageService

    MediaVersion version
    String key
    Long sizeInBytes
    Integer widthInPixels

    @RestApiObjectFields(params=[
        @RestApiObjectField(
            apiFieldName   = "link",
            description    = "Link to access this particular version",
            allowedType    =  "String",
            useForCreation = false),
        @RestApiObjectField(
            apiFieldName   = "width",
            description    = "Inherent with the in `w` units as defined in the responsive image spec",
            allowedType    = "String",
            useForCreation = false)
    ])
    static transients = ["storageService"]
    static constraints = { // all nullable:false by default
        // inherent width in pixels for responsive media, currently only supported for images
        widthInPixels nullable:true
    }

    // Property access
    // ---------------

    // using the "w" unit for inherent width as called for in the `srcset` attribute as defined
    // in the responsive image specification
    String getInherentWidth() {
        Integer width1 = widthInPixels ?: version?.maxWidthInPixels
        width1 ? "${width1}w" : ""
    }

    URL getLink() {
        Result<URL> res = storageService
            .generateAuthLink(key)
            .logFail('MediaElementVersion.getLink')
        res.success ? res.payload : null
    }
}
