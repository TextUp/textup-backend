package org.textup.structure

import grails.compiler.GrailsTypeChecked
import org.textup.*
import org.textup.type.*

@GrailsTypeChecked
interface ReadOnlyMediaInfo {
    Long getId()
    List<? extends ReadOnlyMediaElement> getMediaElementsByType()
    List<? extends ReadOnlyMediaElement> getMediaElementsByType(Collection<MediaType> typesToRetrieve)
}
