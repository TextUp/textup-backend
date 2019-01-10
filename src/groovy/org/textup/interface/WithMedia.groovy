package org.textup.interface

import grails.compiler.GrailsTypeChecked

@GrailsTypeChecked
interface WithMedia extends ReadOnlyWithMedia {
    void setMedia(MediaInfo mInfo)
    MediaInfo getMedia()
}
