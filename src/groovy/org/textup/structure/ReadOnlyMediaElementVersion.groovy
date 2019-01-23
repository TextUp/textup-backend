package org.textup.structure

import grails.compiler.GrailsTypeChecked
import org.textup.*
import org.textup.type.*

@GrailsTypeChecked
interface ReadOnlyMediaElementVersion {
    MediaType getType()
    URL getLink()
    Integer getWidthInPixels()
    Integer getHeightInPixels()
}
