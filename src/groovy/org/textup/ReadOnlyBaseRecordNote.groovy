package org.textup

import grails.compiler.GrailsCompileStatic
import org.joda.time.DateTime

// An interface that delineates the basic properties that a note or a revision
// of a note should have

@GrailsCompileStatic
interface ReadOnlyBaseRecordNote extends Authorable {
    Long getId()
    DateTime getWhenChanged()
    String getNoteContents()
    ReadOnlyLocation getLocation()
}
