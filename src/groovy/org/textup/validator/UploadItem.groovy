package org.textup.validator

import grails.compiler.GrailsTypeChecked
import grails.validation.Validateable
import groovy.transform.EqualsAndHashCode
import java.awt.image.BufferedImage
import org.textup.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*

@EqualsAndHashCode
@GrailsTypeChecked
@Validateable
class UploadItem implements CanValidate {

    String key = UUID.randomUUID().toString()
    MediaType type
    byte[] data
    boolean isPublic = false

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
        new MediaElementVersion(type: type,
            versionId: key,
            sizeInBytes: sizeInBytes,
            widthInPixels: widthInPixels,
            heightInPixels: heightInPixels,
            isPublic: isPublic)
    }

    // Property accesss
    // ----------------

    void setImage(BufferedImage image) {
        widthInPixels = image?.width
        heightInPixels = image?.height
    }

    long getSizeInBytes() { data?.length ?: 0l } // so not null
}
