package org.textup.rest.marshaller

import grails.compiler.GrailsTypeChecked
import org.textup.*
import org.textup.rest.*
import org.textup.type.MediaVersion

@GrailsTypeChecked
class MediaElementJsonMarshaller extends JsonNamedMarshaller {

    static final Closure marshalClosure = { ReadOnlyMediaElement e1 ->
        Map json = [:]
        json.uid = e1.uid
        json.type = e1.type?.mimeType
        e1.versionsForDisplay.each { MediaVersion k, ReadOnlyMediaElementVersion v ->
            Map vInfo = [
                // MIME type is provided in the Content-Type header of the response once
                // the user fetches the content from the provided url
                link: v.getLink()?.toString(),
                width: v.getInherentWidth(),
                height: v.heightInPixels
            ]
            json[k.displayName] = vInfo
        }
        json
    }

    MediaElementJsonMarshaller() {
        super(MediaElement, marshalClosure)
    }
}
