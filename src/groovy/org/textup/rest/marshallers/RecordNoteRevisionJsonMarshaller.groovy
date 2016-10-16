package org.textup.rest.marshallers

import grails.compiler.GrailsCompileStatic
import grails.plugin.springsecurity.SpringSecurityService
import org.codehaus.groovy.grails.web.mapping.LinkGenerator
import org.textup.*
import org.textup.rest.*

@GrailsCompileStatic
class RecordNoteRevisionJsonMarshaller extends JsonNamedMarshaller {
    static final Closure marshalClosure = { String namespace,
        SpringSecurityService springSecurityService, AuthService authService,
        LinkGenerator linkGenerator, RecordNoteRevision rev ->

        Map json = [:]
        json.with {
            whenChanged = rev.whenChanged
            contents = rev.contents
            location = rev.location
            images = rev.imageLinks

            if (rev.authorName) authorName = rev.authorName
            if (rev.authorId) authorId = rev.authorId
            if (rev.authorType) authorType = rev.authorType.toString()
        }
        json
    }

    RecordNoteRevisionJsonMarshaller() {
        super(RecordNoteRevision, marshalClosure)
    }
}
