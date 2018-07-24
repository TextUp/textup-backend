package org.textup.rest.marshaller

import grails.compiler.GrailsCompileStatic
import org.textup.*
import org.textup.rest.*
import org.textup.type.MediaVersion
import org.textup.validator.MediaElement
import org.textup.validator.MediaElementVersion

@GrailsCompileStatic
class MediaElementJsonMarshaller extends JsonNamedMarshaller {

    static final Closure marshalClosure = { String namespace, GrailsApplication grailsApplication,
        MediaElement e1 ->

        StorageService storageService = grailsApplication.mainContext.getBean(StorageService)
        Map json = [uid: e1.uid]
        e1.versionsForDisplay.each { MediaVersion k, MediaElementVersion v ->
            json[k.displayName] = [
                // MIME type is provided in the Content-Type header of the response once
                // the user fetches the content from the provided url
                url: v.getLink(storageService)?.toString(),
                width: v.inherentWidth
            ]
        }
        json
    }

    MediaElementJsonMarshaller() {
        super(MediaElement, marshalClosure)
    }
}
