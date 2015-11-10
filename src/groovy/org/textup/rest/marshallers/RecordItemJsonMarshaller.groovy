package org.textup.rest.marshallers

import org.textup.*
import org.textup.rest.*
import org.codehaus.groovy.grails.web.mapping.LinkGenerator
import grails.plugin.springsecurity.SpringSecurityService

class RecordItemJsonMarshaller extends JsonNamedMarshaller {
    static final Closure marshalClosure = { String namespace,
        SpringSecurityService springSecurityService, AuthService authService,
        LinkGenerator linkGenerator, RecordItem item ->

        Map json = [:]
        json.with {
            id = item.id
            dateCreated = item.dateCreated
            outgoing = item.outgoing
            incoming = item.incoming
            if (item.authorName) authorName = item.authorName
            if (item.authorId) authorId = item.authorId
            if (item.instanceOf(RecordCall)) {
                durationInSeconds = item.durationInSeconds
                if (item.hasVoicemail) {

                    ////////////////////////////////////////////////
                    //TODO: generate time-sensitive voicemail Url //
                    ////////////////////////////////////////////////

                    voicemailInSeconds = item.voicemailInSeconds
                }
                type = Constants.RECORD_CALL
            }
            else if (item.instanceOf(RecordText)) {
                futureText = item.futureText
                if (item.sendAt) sendAt = item.sendAt
                contents = item.contents
                type = Constants.RECORD_TEXT
            }
            else if (item.instanceOf(RecordNote)) {
                note = item.note
                editable = item.editable
                type = Constants.RECORD_NOTE
            }
        }
        json.receipts = item.receipts.collect { RecordItemReceipt r ->
            [status:r.status, receivedBy:r.receivedBy.number]
        }

        json.links = [:]
        json.links << [self:linkGenerator.link(namespace:namespace, resource:"record", action:"show", id:item.id, absolute:false)]
        json
    }

    RecordItemJsonMarshaller() {
        super(RecordItem, marshalClosure)
    }
}
