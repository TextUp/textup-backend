package org.textup

import grails.compiler.GrailsCompileStatic
import org.joda.time.DateTime
import org.textup.validator.ImageInfo

@GrailsCompileStatic
interface ReadOnlyRecordNote extends ReadOnlyRecordItem {
    DateTime getWhenChanged()
    boolean getIsDeleted()
    boolean getIsReadOnly()
    Set<ReadOnlyRecordNoteRevision> getRevisions()
    String getNoteContents()
    ReadOnlyLocation getLocation()
    Collection<ImageInfo> getImages()
}
