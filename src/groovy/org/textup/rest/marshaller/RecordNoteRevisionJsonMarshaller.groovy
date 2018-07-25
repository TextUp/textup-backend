package org.textup.rest.marshaller

import grails.compiler.GrailsCompileStatic
import org.textup.*
import org.textup.rest.*

@GrailsCompileStatic
class RecordNoteRevisionJsonMarshaller extends JsonNamedMarshaller {
    static final Closure marshalClosure = { ReadOnlyRecordNoteRevision rev ->

        Map json = [:]
        json.with {
            id = rev.id
            whenChanged = rev.whenChanged
            noteContents = rev.noteContents
            location = rev.location
            media = rev.media

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
