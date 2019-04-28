package org.textup.structure

import grails.compiler.GrailsTypeChecked
import org.joda.time.DateTime
import org.textup.*
import org.textup.type.*

@GrailsTypeChecked
interface ReadOnlyMediaElement {
    String getUid()
    DateTime getWhenCreated()
    List<? extends ReadOnlyMediaElementVersion> getAllVersions()
}
