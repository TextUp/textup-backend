package org.textup.rest.marshaller

import grails.compiler.GrailsTypeChecked
import org.textup.*
import org.textup.rest.*
import org.textup.type.*
import org.textup.util.*

@GrailsTypeChecked
class MediaInfoJsonMarshaller extends JsonNamedMarshaller {

    static final Closure marshalClosure = { ReadOnlyMediaInfo mInfo ->

        Map json = [
            id     : mInfo.id,
            images : mInfo.getMediaElementsByType(MediaType.IMAGE_TYPES),
            audio  : mInfo.getMediaElementsByType(MediaType.AUDIO_TYPES)
        ]
        // Display upload error from uploading the initial versions
        Result<?> res = Utils.tryGetFromRequest(Constants.REQUEST_UPLOAD_ERRORS)
            .logFail("MediaInfoJsonMarshaller: no available request", LogLevel.DEBUG)
        if (res.success) { json.uploadErrors = res.payload }

        json
    }

    MediaInfoJsonMarshaller() {
        super(ReadOnlyMediaInfo, marshalClosure)
    }
}
