package org.textup

import grails.compiler.GrailsTypeChecked
import org.textup.type.MediaType
import org.textup.type.MediaVersion

// The interfaces that define the contract for handling media

@GrailsTypeChecked
interface ReadOnlyWithMedia {
    ReadOnlyMediaInfo getReadOnlyMedia()
}

@GrailsTypeChecked
interface WithMedia extends ReadOnlyWithMedia {
    void setMedia(MediaInfo mInfo)
    MediaInfo getMedia()
}

@GrailsTypeChecked
interface ReadOnlyMediaInfo {
    Long getId()
    List<? extends ReadOnlyMediaElement> getMediaElementsByType()
    List<? extends ReadOnlyMediaElement> getMediaElementsByType(Collection<MediaType> typesToRetrieve)
}

@GrailsTypeChecked
interface ReadOnlyMediaElement {
    String getUid()
    MediaType type
    Map<MediaVersion, ? extends ReadOnlyMediaElementVersion> getVersionsForDisplay()
}

@GrailsTypeChecked
interface ReadOnlyMediaElementVersion {
    URL getLink()
    Integer getInherentWidth()
    Integer getHeightInPixels()
}
