package org.textup.type

import grails.compiler.GrailsCompileStatic
import org.textup.Constants

@GrailsCompileStatic
enum MediaType {
    IMAGE([Constants.MIME_TYPE_JPEG, Constants.MIME_TYPE_PNG, Constants.MIME_TYPE_GIF])

    private final Collection<String> mimeTypes

    MediaType(Collection<String> allowedMimeTypes) {
        this.mimeTypes = allowedMimeTypes
    }

    Collection<String> getMimeTypes() { this.mimeTypes }

    static MediaType convertMimeType(String mimeType) {
        MediaType.values().find { MediaType type -> type.mimeTypes.contains(mimeType) }
    }
    static boolean isValidMimeType(String mimeType) {
        MediaType.convertMimeType(mimeType) != null
    }
}
