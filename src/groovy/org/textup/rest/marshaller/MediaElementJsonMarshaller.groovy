package org.textup.rest.marshaller

import grails.compiler.GrailsTypeChecked
import org.textup.*
import org.textup.rest.*

@GrailsTypeChecked
class MediaElementJsonMarshaller extends JsonNamedMarshaller {

    static final Closure marshalClosure = { ReadOnlyMediaElement el1 ->
        [
            uid         : el1.uid,
            versions    : el1.allVersions,
            whenCreated : el1.whenCreated
        ]
    }

    MediaElementJsonMarshaller() {
        super(ReadOnlyMediaElement, marshalClosure)
    }
}
