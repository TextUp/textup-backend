package org.textup.interface

import grails.compiler.GrailsTypeChecked
import org.textup.type.MediaType

@GrailsTypeChecked
interface ReadOnlyMediaInfo {
    Long getId()
    List<? extends ReadOnlyMediaElement> getMediaElementsByType()
    List<? extends ReadOnlyMediaElement> getMediaElementsByType(Collection<MediaType> typesToRetrieve)
}
