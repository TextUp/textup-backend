package org.textup.rest.marshaller

import grails.compiler.GrailsCompileStatic
import groovy.util.logging.Log4j
import javax.servlet.http.HttpServletRequest
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.codehaus.groovy.grails.web.mapping.LinkGenerator
import org.textup.*
import org.textup.rest.*
import org.textup.type.*

@GrailsCompileStatic
@Log4j
class RecordItemJsonMarshaller extends JsonNamedMarshaller {
    static final Closure marshalClosure = { String namespace, GrailsApplication grailsApplication,
        LinkGenerator linkGenerator, ReadOnlyRecordItem item ->

        List<String> uploadErrors = Collections.emptyList()
        Helpers.<List<String>>tryGetFromRequest(Constants.UPLOAD_ERRORS)
            .logFail("RecordItemJsonMarshaller: no available request", LogLevel.DEBUG)
            .then { List<String> errors -> uploadErrors = errors }

        Map json = uploadErrors ? [uploadErrors: uploadErrors] : [:]
        json.with {
            id = item.id
            whenCreated = item.whenCreated
            outgoing = item.outgoing
            hasAwayMessage = item.hasAwayMessage
            isAnnouncement = item.isAnnouncement
            receipts = item.groupReceiptsByStatus()
            media = item.media
            if (item.authorName) { authorName = item.authorName }
            if (item.authorId) { authorId = item.authorId }
            if (item.authorType) { authorType = item.authorType.toString() }
            if (item.noteContents) { noteContents = item.noteContents }

            if (item instanceof ReadOnlyRecordCall) {
                durationInSeconds = item.durationInSeconds
                hasVoicemail = item.hasVoicemail
                if (item.hasVoicemail) {
                    voicemailUrl = item.voicemailUrl
                    voicemailInSeconds = item.voicemailInSeconds
                }
                type = RecordItemType.CALL.toString()
            }
            else if (item instanceof ReadOnlyRecordText) {
                contents = item.contents
                type = RecordItemType.TEXT.toString()
            }
            else if (item instanceof RecordNote) {
                whenChanged = item.whenChanged
                isDeleted = item.isDeleted
                isReadOnly = item.isReadOnly
                revisions = item.revisions
                location = item.location
            }
        }
        // find owner, may be a contact or a tag
        json.contact = Contact.findByRecord(item.record)?.id
        if (!json.contact) {
            json.tag = ContactTag.findByRecord(item.record)?.id
        }
        json.links = [:] << [self:linkGenerator.link(namespace:namespace,
            resource:"record", action:"show", id:item.id, absolute:false)]
        json
    }

    RecordItemJsonMarshaller() {
        super(ReadOnlyRecordItem, marshalClosure)
    }
}
