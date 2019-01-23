package org.textup.structure

import grails.compiler.GrailsTypeChecked
import org.textup.*
import org.textup.type.*

@GrailsTypeChecked
interface WithMedia extends ReadOnlyWithMedia {
    void setMedia(MediaInfo mInfo)
    MediaInfo getMedia()
}
