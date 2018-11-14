package org.textup.rest.marshaller

import grails.compiler.GrailsTypeChecked
import org.textup.*
import org.textup.rest.*

@GrailsTypeChecked
class MediaElementJsonMarshaller extends JsonNamedMarshaller {

    static final Closure marshalClosure = { ReadOnlyMediaElement e1 ->
        Map json = [
            uid: e1.uid,
            whenCreated: e1.whenCreated,
            versions: e1.allVersions.collect { ReadOnlyMediaElementVersion v1 ->
                [
                    type   : v1.type.mimeType,
                    link   : v1.getLink()?.toString(),
                    width  : v1.widthInPixels,
                    height : v1.heightInPixels
                ]
            }
        ]
        json
    }

    MediaElementJsonMarshaller() {
        super(MediaElement, marshalClosure)
    }
}
