package org.textup.structure

import grails.compiler.GrailsTypeChecked
import org.textup.*
import org.textup.type.*

@GrailsTypeChecked
interface ReadOnlyRecordNote extends ReadOnlyBaseRecordNote {
    boolean getIsReadOnly()
    Set<? extends ReadOnlyRecordNoteRevision> getRevisions()
}
