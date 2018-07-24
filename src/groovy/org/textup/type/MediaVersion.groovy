package org.textup.type

import grails.compiler.GrailsCompileStatic

@GrailsCompileStatic
enum MediaVersion {
    SEND("send", 640, 5000, null),
    SMALL("small", 320, 50000, null),
    MEDIUM("medium", 640, 100000, SMALL),
    LARGE("large", 1280, 200000, MEDIUM)

    private static final String displayName
    private static final int maxWidthInPixels
    private static final long maxSizeInBytes
    private static final MediaVersion next

    MediaVersion(String name, int width, long size, MediaVersion next) {
        this.displayName = name
        this.maxWidthInPixels = width
        this.maxSizeInBytes = size
        this.next = next
    }

    String getDisplayName() { this.displayName }
    int getMaxWidthInPixels() { this.maxWidthInPixels }
    long getMaxSizeInBytes() { this.maxSizeInBytes }
    MediaVersion getNext() { this.next }
}
