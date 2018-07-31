package org.textup.type

import spock.lang.*

class MediaTypeSpec extends Specification {

    void "test validating content types"() {
        expect: "case sensitive, must match exactly"
        ["image/png", "image/jpeg", "image/gif"].every(MediaType.&isValidMimeType)
        MediaType.isValidMimeType("IMAGE/png") == false
        MediaType.isValidMimeType("image/Jpeg") == false
    }

    void "test converting content types"() {
        expect: "must match exactly or else will return null"
        ["image/png", "image/jpeg", "image/gif"].every(MediaType.&convertMimeType)
        MediaType.convertMimeType("IMAGE/png") == null
        MediaType.convertMimeType("image/Jpeg") == null
    }
}
