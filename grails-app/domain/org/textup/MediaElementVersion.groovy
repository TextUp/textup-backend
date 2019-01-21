package org.textup

import grails.compiler.GrailsTypeChecked
import groovy.transform.EqualsAndHashCode
import org.textup.type.*
import org.textup.util.*

@GrailsTypeChecked
@EqualsAndHashCode
class MediaElementVersion implements ReadOnlyMediaElementVersion, WithId, Saveable<MediaElementVersion> {

    String versionId
    Long sizeInBytes
    Integer widthInPixels
    Integer heightInPixels
    boolean isPublic = false
    MediaType type

    static constraints = { // all nullable:false by default
        sizeInBytes min: 1l
        widthInPixels nullable:true, min: 1 // inherent width in pixels for responsive media, intended for images
        heightInPixels nullable:true, min: 1 // for pre-loading images in gallery on frontend, intended for images
    }

    // Property access
    // ---------------

    URL getLink() {
        isPublic ? LinkUtils.unsignedLink(versionId) : LinkUtils.signedLink(versionId)
    }
}
