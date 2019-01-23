package org.textup.rest.marshaller

import grails.compiler.GrailsTypeChecked
import org.textup.*
import org.textup.rest.*

@GrailsTypeChecked
class MediaElementVersionJsonMarshaller extends JsonNamedMarshaller {

    static final Closure marshalClosure = { ReadOnlyMediaElementVersion vers1 ->
        [
            height : vers1.heightInPixels,
            link   : vers1.getLink()?.toString(),
            type   : vers1.type.mimeType,
            width  : vers1.widthInPixels
        ]
    }

    MediaElementVersionJsonMarshaller() {
        super(ReadOnlyMediaElementVersion, marshalClosure)
    }
}
