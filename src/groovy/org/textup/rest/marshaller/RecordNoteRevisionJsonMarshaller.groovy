package org.textup.rest.marshaller

import grails.compiler.GrailsTypeChecked
import org.textup.*
import org.textup.rest.*

@GrailsTypeChecked
class RecordNoteRevisionJsonMarshaller extends JsonNamedMarshaller {
    static final Closure marshalClosure = { ReadOnlyRecordNoteRevision rev ->

        Map json = [:]
        json.with {
            id = rev.id
            whenChanged = rev.whenChanged
            noteContents = rev.noteContents
            location = rev.readOnlyLocation
            media = rev.readOnlyMedia

            if (rev.authorName) authorName = rev.authorName
            if (rev.authorId) authorId = rev.authorId
            if (rev.authorType) authorType = rev.authorType.toString()
        }
        json
    }

    RecordNoteRevisionJsonMarshaller() {
        super(ReadOnlyRecordNoteRevision, marshalClosure)
    }
}
