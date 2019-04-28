package org.textup.validator

import grails.compiler.GrailsTypeChecked
import grails.validation.Validateable
import groovy.transform.EqualsAndHashCode
import groovy.transform.TupleConstructor
import java.awt.image.BufferedImage
import org.textup.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*

@EqualsAndHashCode
@GrailsTypeChecked
@TupleConstructor(includeFields = true, excludes = ["key", "isPublic"])
@Validateable
class UploadItem implements CanValidate {

    final String key = UUID.randomUUID().toString()
    final MediaType type
    final byte[] data
    final Integer widthInPixels
    final Integer heightInPixels

    boolean isPublic = false

    static constraints = { // default nullable: false
        // inherent width in pixels for responsive media, currently only intended for images
        widthInPixels nullable:true, min: 1
        // for pre-loading images in gallery on frontend, currently only intended for images
        heightInPixels nullable:true, min: 1
    }

    static Result<UploadItem> tryCreate(MediaType type, byte[] data, BufferedImage image = null) {
        UploadItem uItem1 = new UploadItem(type, data, image?.width, image?.height)
        DomainUtils.tryValidate(uItem1, ResultStatus.CREATED)
    }

    // Properties
    // ----------

    long getSizeInBytes() { data?.length ?: 0l } // so not null
}
