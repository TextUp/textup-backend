package org.textup

import grails.compiler.GrailsCompileStatic

// Adds some additional properties that a RecordNote should have but child
// RecordNoteRevisions should not have

@GrailsCompileStatic
interface ReadOnlyRecordNote extends ReadOnlyBaseRecordNote {
    boolean getIsDeleted()
    boolean getIsReadOnly()
    Set<ReadOnlyRecordNoteRevision> getRevisions()
}
