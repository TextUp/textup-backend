package org.textup.interface

import grails.compiler.GrailsTypeChecked

@GrailsTypeChecked
interface ReadOnlyRecordNote extends ReadOnlyBaseRecordNote {
    boolean getIsDeleted()
    boolean getIsReadOnly()
    Set<? extends ReadOnlyRecordNoteRevision> getRevisions()
}
