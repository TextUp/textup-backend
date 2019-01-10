package org.textup.interface

import grails.compiler.GrailsTypeChecked
import org.textup.type.MediaType

@GrailsTypeChecked
interface ReadOnlyMediaElementVersion {
    MediaType getType()
    URL getLink()
    Integer getWidthInPixels()
    Integer getHeightInPixels()
}
