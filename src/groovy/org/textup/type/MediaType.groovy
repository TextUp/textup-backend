package org.textup.type

import grails.compiler.GrailsCompileStatic
import org.textup.Constants

@GrailsCompileStatic
enum MediaType {
    IMAGE_UNKNOWN(null),
    IMAGE_JPEG("image/jpeg"),
    IMAGE_PNG("image/png"),
    IMAGE_GIF("image/gif")

    private final String mimeType
    MediaType(String type) { this.mimeType = type }
    String mimeType() { this.mimeType }

    static Collection<MediaType> IMAGE_TYPES = [IMAGE_UNKNOWN, IMAGE_JPEG, IMAGE_PNG, IMAGE_GIF]

    static MediaType convertMimeType(String inputType) {
        String lowerCased = inputType?.toLowerCase()
        if (lowerCased) {
            MediaType.values().find { MediaType mType -> mType.mimeType == lowerCased }
        }
    }
    static boolean isValidMimeType(String inputType) {
        MediaType.convertMimeType(inputType) != null
    }
}
