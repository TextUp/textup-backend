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
        Map json = [
            audio  : mInfo.getMediaElementsByType(MediaType.AUDIO_TYPES),
            id     : mInfo.id,
            images : mInfo.getMediaElementsByType(MediaType.IMAGE_TYPES)
        ]
        // Display upload error from uploading the initial versions
        RequestUtils.tryGet(RequestUtils.UPLOAD_ERRORS)
            .thenEnd { json.uploadErrors = it }

        json
    }

    MediaInfoJsonMarshaller() {
        super(ReadOnlyMediaInfo, marshalClosure)
    }
}
