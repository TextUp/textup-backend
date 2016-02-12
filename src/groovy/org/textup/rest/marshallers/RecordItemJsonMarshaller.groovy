package org.textup.rest.marshallers

import grails.compiler.GrailsCompileStatic
import grails.plugin.springsecurity.SpringSecurityService
import org.codehaus.groovy.grails.web.mapping.LinkGenerator
import org.textup.*
import org.textup.rest.*
import org.textup.types.RecordItemType

@GrailsCompileStatic
class RecordItemJsonMarshaller extends JsonNamedMarshaller {
    static final Closure marshalClosure = { String namespace,
        SpringSecurityService springSecurityService, AuthService authService,
        LinkGenerator linkGenerator, RecordItem item ->

        Map json = [:]
        json.with {
            id = item.id
            whenCreated = item.whenCreated
            outgoing = item.outgoing
            contact = Contact.findByRecord(item.record)?.id //contact owner id
            hasAwayMessage = item.hasAwayMessage
            isAnnouncement = item.isAnnouncement
            if (item.authorName) {
                authorName = item.authorName
            }
            if (item.authorId) {
                authorId = item.authorId
            }
            if (item.authorType) {
                authorType = item.authorType.toString()
            }
            if (item.instanceOf(RecordCall)) {
                RecordCall call = item as RecordCall
                durationInSeconds = call.durationInSeconds
                hasVoicemail = call.hasVoicemail
                if (call.hasVoicemail) {
                    voicemailUrl = call.voicemailUrl
                    voicemailInSeconds = call.voicemailInSeconds
                }
                type = RecordItemType.CALL.toString()
            }
            else if (item.instanceOf(RecordText)) {
                RecordText text = item as RecordText
                contents = text.contents
                type = RecordItemType.TEXT.toString()
            }
        }
        json.receipts = item.receipts?.collect { RecordItemReceipt r ->
            [
                id: r.id,
                status:r.status.toString(),
                receivedBy:r.receivedBy.e164PhoneNumber
            ]
        } ?: []
        json.links = [:] << [self:linkGenerator.link(namespace:namespace,
            resource:"record", action:"show", id:item.id, absolute:false)]
        json
    }

    RecordItemJsonMarshaller() {
        super(RecordItem, marshalClosure)
    }
}
