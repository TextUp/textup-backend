package org.textup.interface

import grails.compiler.GrailsTypeChecked
import org.joda.time.DateTime

@GrailsTypeChecked
interface ReadOnlyBaseRecordNote extends Authorable {
    Long getId()
    DateTime getWhenChanged()
    String getNoteContents()
    ReadOnlyLocation getReadOnlyLocation()
}
