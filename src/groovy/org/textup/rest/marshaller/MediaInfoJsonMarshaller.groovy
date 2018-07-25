package org.textup.rest.marshaller

import grails.compiler.GrailsCompileStatic
import org.textup.*
import org.textup.rest.*
import org.textup.type.MediaType
import org.textup.validator.MediaInfo

@GrailsCompileStatic
class MediaInfoJsonMarshaller extends JsonNamedMarshaller {

    static final Closure marshalClosure = { ReadOnlyMediaInfo mInfo ->
        [images: mInfo.getElements([MediaType.IMAGE])]
    }

    MediaInfoJsonMarshaller() {
        super(MediaInfo, marshalClosure)
    }
}
