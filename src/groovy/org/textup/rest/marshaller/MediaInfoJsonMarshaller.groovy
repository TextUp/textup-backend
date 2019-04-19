package org.textup.rest.marshaller

import grails.compiler.GrailsTypeChecked
import org.textup.*
import org.textup.rest.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*

@GrailsTypeChecked
class MediaInfoJsonMarshaller extends JsonNamedMarshaller {

    static final Closure marshalClosure = { ReadOnlyMediaInfo mInfo ->
        // Remove once we use SocketEvents
        Boolean mostRecentOnly = TypeUtils.to(Boolean,
            RequestUtils.tryGet(RequestUtils.MOST_RECENT_MEDIA_ELEMENTS_ONLY).payload)
        Map json = [:]
        json.with {
            id = mInfo.id
            if (mostRecentOnly) {
                audio = [mInfo.getReadOnlyMostRecentByType(MediaType.AUDIO_TYPES)]
                images = [mInfo.getReadOnlyMostRecentByType(MediaType.IMAGE_TYPES)]
            }
            else {
                audio = mInfo.getMediaElementsByType(MediaType.AUDIO_TYPES)
                images = mInfo.getMediaElementsByType(MediaType.IMAGE_TYPES)
            }
        }
        // Display upload error from uploading the initial versions
        RequestUtils.tryGet(RequestUtils.UPLOAD_ERRORS)
            .thenEnd { json.uploadErrors = it }

        json
    }

    MediaInfoJsonMarshaller() {
        super(ReadOnlyMediaInfo, marshalClosure)
    }
}
