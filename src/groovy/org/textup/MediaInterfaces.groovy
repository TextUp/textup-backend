package org.textup

import grails.compiler.GrailsCompileStatic

// The interfaces that define the contract for handling media

@GrailsCompileStatic
interface ReadOnlyWithMedia {
    ReadOnlyMediaInfo getMedia()
}

@GrailsCompileStatic
interface WithMedia extends ReadOnlyWithMedia {
    void setMedia(MediaInfo mInfo)
    MediaInfo getMedia()
}

@GrailsCompileStatic
interface ReadOnlyMediaInfo {
    List<? extends ReadOnlyMediaElement> getElements()
    List<? extends ReadOnlyMediaElement> getElements(Collection<MediaType> typesToRetrieve)
}

@GrailsCompileStatic
interface ReadOnlyMediaElement {
    String getUid()
    Map<MediaVersion, ? extends ReadOnlyMediaElementVersion> getDisplayVersions()
}

@GrailsCompileStatic
interface ReadOnlyMediaElementVersion {
    URL getLink()
    String getInherentWidth()
}
