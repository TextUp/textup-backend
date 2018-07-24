package org.textup.type

import grails.compiler.GrailsCompileStatic

@GrailsCompileStatic
enum MediaType {
    IMAGE(["image/png", "image/jpeg", "image/gif"])

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
