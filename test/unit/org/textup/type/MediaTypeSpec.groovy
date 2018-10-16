package org.textup.type

import spock.lang.*

class MediaTypeSpec extends Specification {

    void "test validating content types"() {
        expect: "case insensitive, input will be lowercased"
        ["image/png", "image/jpeg", "image/gif", "audio/ogg;codecs=opus"].every(MediaType.&isValidMimeType)
        MediaType.isValidMimeType("IMAGE/png") == true
        MediaType.isValidMimeType("image/Jpeg") == true
        MediaType.isValidMimeType("AUDIO/webm; codecs=opus") == true

        and: "symbols and spacing still matters and will not be corrected"
        MediaType.isValidMimeType("audio/webm;   codecs=opus") == false
    }

    void "test converting content types"() {
        expect: "case insensitive"
        ["image/png", "image/jpeg", "image/gif"].every(MediaType.&convertMimeType)
        MediaType.convertMimeType("IMAGE/png") == MediaType.IMAGE_PNG
        MediaType.convertMimeType("image/Jpeg") == MediaType.IMAGE_JPEG

        and: "multiple mime types can resolve to the same enum value"
        MediaType.convertMimeType("audio/mp3") == MediaType.AUDIO_MP3
        MediaType.convertMimeType("audio/mpeg") == MediaType.AUDIO_MP3
    }

    void "test getting mime type from enum"() {
        expect: "first mime type in list of possible mime types"
        MediaType.IMAGE_UNKNOWN.mimeType == ""
        MediaType.IMAGE_GIF.mimeType == "image/gif"
        MediaType.AUDIO_WEBM_OPUS.mimeType == "audio/webm;codecs=opus"
    }

    void "test lists of types"() {
        expect:
        MediaType.IMAGE_TYPES instanceof Collection<MediaType>
        MediaType.AUDIO_TYPES instanceof Collection<MediaType>
        MediaType.IMAGE_TYPES*.mimeType.every { it == "" || it.contains("image") }
        MediaType.AUDIO_TYPES*.mimeType.every { it.contains("audio") }
    }
}
