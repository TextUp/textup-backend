package org.textup.util.domain

class RecordNotes {

    static Result<RecordNote> mustFindModifiableForId(Long noteId) {
        RecordNote note1 = RecordNote.get(noteId)
        if (note1) {
            if (note1.isReadOnly) {
                IOCUtils.resultFactory.failWithCodeAndStatus("recordService.update.readOnly", // TODO
                    ResultStatus.FORBIDDEN, [noteId])
            }
            else { IOCUtils.resultFactory.success(note1) }
        }
        else {
            IOCUtils.resultFactory.failWithCodeAndStatus("recordService.update.notFound", // TODO
                ResultStatus.NOT_FOUND, [noteId])
        }
    }
}
