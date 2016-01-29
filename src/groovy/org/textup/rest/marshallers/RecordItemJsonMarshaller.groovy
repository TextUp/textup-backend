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
            contact = Contact.forRecord(item.record).get()?.id //contact owner id
            hasAwayMessage = item.hasAwayMessage
            isAnnouncement = item.isAnnouncement
            if (item.authorName) authorName = item.authorName
            if (item.authorId) authorId = item.authorId
            if (item.instanceOf(RecordCall)) {
                durationInSeconds = item.durationInSeconds
                hasVoicemail = item.hasVoicemail
                if (item.hasVoicemail) {
                    voicemailUrl = item.voicemailUrl
                    voicemailInSeconds = item.voicemailInSeconds
                }
                type = RecordItemType.CALL
            }
            else if (item.instanceOf(RecordText)) {
                contents = item.contents
                type = RecordItemType.TEXT
            }
        }
        json.receipts = item.receipts.collect { RecordItemReceipt r ->
            [
                id: r.id,
                status:r.status,
                receivedBy:r.receivedBy.number
            ]
        }

        json.links = [:]
        json.links << [self:linkGenerator.link(namespace:namespace, \
            resource:"record", action:"show", id:item.id, absolute:false)]
        json
    }

    RecordItemJsonMarshaller() {
        super(RecordItem, marshalClosure)
    }
}
