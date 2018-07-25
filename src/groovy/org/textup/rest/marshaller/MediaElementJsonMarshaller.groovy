package org.textup.rest.marshaller

import grails.compiler.GrailsCompileStatic
import org.textup.*
import org.textup.rest.*
import org.textup.type.MediaVersion

@GrailsCompileStatic
class MediaElementJsonMarshaller extends JsonNamedMarshaller {

    static final Closure marshalClosure = { ReadOnlyMediaElement e1 ->
        Map json = [:]
        json.uid = e1.uid
        e1.versionsForDisplay.each { MediaVersion k, ReadOnlyMediaElementVersion v ->
            Map vInfo = [
                // MIME type is provided in the Content-Type header of the response once
                // the user fetches the content from the provided url
                link: v.getLink()?.toString(),
                width: v.getInherentWidth()
            ]
            json[k.displayName] = vInfo
        }
        json
    }

    MediaElementJsonMarshaller() {
        super(MediaElement, marshalClosure)
    }
}
