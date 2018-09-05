package org.textup.type

import spock.lang.*

class MediaTypeSpec extends Specification {

    void "test validating content types"() {
        expect: "case insensitive, input will be lowercased"
        ["image/png", "image/jpeg", "image/gif"].every(MediaType.&isValidMimeType)
        MediaType.isValidMimeType("IMAGE/png") == true
        MediaType.isValidMimeType("image/Jpeg") == true
    }

    void "test converting content types"() {
        expect: "case insensitive"
        ["image/png", "image/jpeg", "image/gif"].every(MediaType.&convertMimeType)
        MediaType.convertMimeType("IMAGE/png") == MediaType.IMAGE_PNG
        MediaType.convertMimeType("image/Jpeg") == MediaType.IMAGE_JPEG
    }

    void "test image types"() {
        expect:
        MediaType.IMAGE_TYPES instanceof Collection<MediaType>
        MediaType.IMAGE_TYPES.size() == 4
    }
}
