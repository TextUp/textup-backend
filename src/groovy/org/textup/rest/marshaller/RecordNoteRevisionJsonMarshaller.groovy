package org.textup.rest.marshaller

import grails.compiler.GrailsCompileStatic
import grails.plugin.springsecurity.SpringSecurityService
import org.codehaus.groovy.grails.web.mapping.LinkGenerator
import org.textup.*
import org.textup.rest.*

@GrailsCompileStatic
class RecordNoteRevisionJsonMarshaller extends JsonNamedMarshaller {
    static final Closure marshalClosure = { String namespace,
        SpringSecurityService springSecurityService, AuthService authService,
        LinkGenerator linkGenerator, ReadOnlyRecordNoteRevision rev ->

        Map json = [:]
        json.with {
            id = rev.id
            whenChanged = rev.whenChanged
            noteContents = rev.noteContents
            location = rev.location
            images = rev.images

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
