package org.textup.validator

import grails.compiler.GrailsTypeChecked
import grails.validation.Validateable
import groovy.transform.EqualsAndHashCode
import java.awt.image.BufferedImage
import org.textup.*
import org.textup.type.*

@EqualsAndHashCode
@GrailsTypeChecked
@Validateable
class UploadItem {

    String key = UUID.randomUUID().toString()
    MediaType type
    byte[] data

    Integer widthInPixels
    Integer heightInPixels

    static constraints = { // default nullable: false
        // inherent width in pixels for responsive media, currently only intended for images
        widthInPixels nullable:true, min: 1
        // for pre-loading images in gallery on frontend, currently only intended for images
        heightInPixels nullable:true, min: 1
    }

    // Methods
    // -------

    MediaElementVersion toMediaElementVersion() {
        new MediaElementVersion(versionId: key,
            sizeInBytes: sizeInBytes,
            widthInPixels: widthInPixels,
            heightInPixels: heightInPixels)
    }

    // Property accesss
    // ----------------

    void setImage(BufferedImage image) {
        widthInPixels = image?.width
        heightInPixels = image?.height
    }

    long getSizeInBytes() { data?.length ?: 0l } // so not null
}
