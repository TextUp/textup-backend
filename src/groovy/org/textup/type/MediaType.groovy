package org.textup.type

import grails.compiler.GrailsTypeChecked
import org.textup.*
import org.textup.util.*
import org.textup.validator.*

// If changing the audio mime types here, make sure to also check AudioUtils to see if building
// options from the MediaType should be updated too

@GrailsTypeChecked
enum MediaType {
    IMAGE_UNKNOWN(null),
    IMAGE_JPEG(["image/jpeg"]),
    IMAGE_PNG(["image/png"]),
    IMAGE_GIF(["image/gif"]),
    AUDIO_MP3(["audio/mpeg", "audio/mp3"]), // see https://stackoverflow.com/questions/10688588/which-mime-type-should-i-use-for-mp3
    AUDIO_OGG_VORBIS(["audio/ogg"]),
    AUDIO_OGG_OPUS(["audio/ogg;codecs=opus", "audio/ogg; codecs=opus"]),
    AUDIO_WEBM_VORBIS(["audio/webm"]),
    AUDIO_WEBM_OPUS(["audio/webm;codecs=opus", "audio/webm; codecs=opus"])

    private final List<String> mimeTypes

    MediaType(List<String> possibleTypes) {
        this.mimeTypes = possibleTypes
    }

    String getMimeType() { this.mimeTypes?.getAt(0) ?: "" }

    static HashSet<MediaType> IMAGE_TYPES = new HashSet<>([IMAGE_UNKNOWN, IMAGE_JPEG, IMAGE_PNG, IMAGE_GIF])
    static HashSet<MediaType> AUDIO_TYPES = new HashSet<>([AUDIO_MP3, AUDIO_OGG_VORBIS, AUDIO_OGG_OPUS,
        AUDIO_WEBM_VORBIS, AUDIO_WEBM_OPUS])

    static MediaType convertMimeType(String inputType) {
        String lowerCased = inputType?.toLowerCase()
        if (lowerCased) {
            MediaType.values().find { MediaType mType -> mType.mimeTypes?.contains(lowerCased) }
        }
    }

    static Result<MediaType> tryConvertMimeType(String inputType) {
        MediaType type = MediaType.convertMimeType(inputType)
        type ?
            IOCUtils.resultFactory.success(type) :
            IOCUtils.resultFactory.failWithCodeAndStatus("mediaType.invalidMimeType",
                ResultStatus.UNPROCESSABLE_ENTITY, [inputType])
    }

    static boolean isValidMimeType(String inputType) {
        MediaType.convertMimeType(inputType) != null
    }
}
