package org.textup.util.domain

import grails.compiler.GrailsTypeChecked
import grails.gorm.DetachedCriteria
import groovy.transform.TypeCheckingMode
import org.joda.time.DateTime
import org.textup.*
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*

@GrailsTypeChecked
class RecordNotes {

    static Result<RecordNote> mustFindModifiableForId(Long noteId) {
        RecordNote note1 = RecordNote.get(noteId)
        if (note1) {
            if (note1.isReadOnly) {
                IOCUtils.resultFactory.failWithCodeAndStatus("recordNotes.cannotModify",
                    ResultStatus.FORBIDDEN, [noteId])
            }
            else { IOCUtils.resultFactory.success(note1) }
        }
        else {
            IOCUtils.resultFactory.failWithCodeAndStatus("recordNotes.notFound",
                ResultStatus.NOT_FOUND, [noteId])
        }
    }
}
