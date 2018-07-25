package org.textup.rest.marshaller

import grails.compiler.GrailsCompileStatic
import org.textup.*
import org.textup.rest.*
import org.textup.type.MediaVersion

@GrailsCompileStatic
class MediaElementJsonMarshaller extends JsonNamedMarshaller {

    static final Closure marshalClosure = { ReadOnlyMediaElement e1 ->
        Map json = [uid: e1.uid]
        e1.displayVersions.each { MediaVersion k, ReadOnlyMediaElementVersion v ->
            json[k.displayName] = [
                // MIME type is provided in the Content-Type header of the response once
                // the user fetches the content from the provided url
                link: v.link?.toString(),
                width: v.inherentWidth
            ]
        }
        json
    }

    MediaElementJsonMarshaller() {
        super(MediaElement, marshalClosure)
    }
}
