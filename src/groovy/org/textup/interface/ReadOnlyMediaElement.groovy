package org.textup.interface

import grails.compiler.GrailsTypeChecked
import org.joda.time.DateTime

@GrailsTypeChecked
interface ReadOnlyMediaElement {
    String getUid()
    DateTime getWhenCreated()
    List<? extends ReadOnlyMediaElementVersion> getAllVersions()
}
