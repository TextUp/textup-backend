package org.textup

import grails.compiler.GrailsTypeChecked
import groovy.transform.EqualsAndHashCode
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*

@EqualsAndHashCode
@GrailsTypeChecked
class MediaElementVersion implements ReadOnlyMediaElementVersion, WithId, CanSave<MediaElementVersion> {

    // Need to declare id for it to be considered in equality operator
    // see: https://stokito.wordpress.com/2014/12/19/equalsandhashcode-on-grails-domains/
    Long id

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

    static MediaElementVersion createIfPresent(UploadItem uItem1) {
        if (uItem1) {
            new MediaElementVersion(type: uItem1.type,
                versionId: uItem1.key,
                sizeInBytes: uItem1.sizeInBytes,
                widthInPixels: uItem1.widthInPixels,
                heightInPixels: uItem1.heightInPixels,
                isPublic: uItem1.isPublic)
        }
        else { null }
    }

    // Properties
    // ----------

    URL getLink() {
        isPublic ? LinkUtils.unsignedLink(versionId) : LinkUtils.signedLink(versionId)
    }
}
