package org.textup.rest.marshaller

import grails.compiler.GrailsTypeChecked
import org.textup.*
import org.textup.rest.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*

@GrailsTypeChecked
class RecordNoteRevisionJsonMarshaller extends JsonNamedMarshaller {

    static final Closure marshalClosure = { ReadOnlyRecordNoteRevision rev1 ->
        Map json = [:]
        json.with {
            id           = rev1.id
            location     = rev1.readOnlyLocation
            media        = rev1.readOnlyMedia
            noteContents = rev1.noteContents
            whenChanged  = rev1.whenChanged

            if (rev1.authorName) authorName = rev1.authorName
            if (rev1.authorId) authorId     = rev1.authorId
            if (rev1.authorType) authorType = rev1.authorType.toString()
        }
        RequestUtils.tryGetFromRequest(RequestUtils.TIMEZONE).thenEnd { String tz ->
            json.whenChanged = JodaUtils.toDateTimeWithZone(json.whenChanged, tz)
        }
        json
    }

    RecordNoteRevisionJsonMarshaller() {
        super(ReadOnlyRecordNoteRevision, marshalClosure)
    }
}
