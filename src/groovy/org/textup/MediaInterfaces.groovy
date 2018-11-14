package org.textup

import grails.compiler.GrailsTypeChecked
import org.joda.time.DateTime
import org.textup.type.MediaType

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
    DateTime getWhenCreated()
    List<? extends ReadOnlyMediaElementVersion> getAllVersions()
}

@GrailsTypeChecked
interface ReadOnlyMediaElementVersion {
    MediaType getType()
    URL getLink()
    Integer getWidthInPixels()
    Integer getHeightInPixels()
}
