package org.textup

import grails.compiler.GrailsCompileStatic
import org.joda.time.DateTime
import org.textup.validator.ImageInfo

@GrailsCompileStatic
interface ReadOnlyRecordNoteRevision extends Authorable {
    Long getId()
    DateTime getWhenChanged()
    String getNoteContents()
    ReadOnlyLocation getLocation()
    Collection<ImageInfo> getImages()
}
