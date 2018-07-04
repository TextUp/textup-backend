package org.textup.rest.marshaller

import grails.compiler.GrailsCompileStatic
import grails.plugin.springsecurity.SpringSecurityService
import groovy.util.logging.Log4j
import javax.servlet.http.HttpServletRequest
import org.codehaus.groovy.grails.web.mapping.LinkGenerator
import org.codehaus.groovy.grails.web.util.WebUtils
import org.textup.*
import org.textup.rest.*
import org.textup.type.RecordItemType
import org.textup.validator.ImageInfo

@GrailsCompileStatic
@Log4j
class RecordItemJsonMarshaller extends JsonNamedMarshaller {
    static final Closure marshalClosure = { String namespace,
        SpringSecurityService springSecurityService, AuthService authService,
        LinkGenerator linkGenerator, ReadOnlyRecordItem item ->

        Collection<ImageInfo> uploadErrors = []
        try {
            HttpServletRequest request = WebUtils.retrieveGrailsWebRequest().currentRequest
            uploadErrors = Helpers.to(List, request.getAttribute(Constants.UPLOAD_ERRORS))
        }
        catch (IllegalStateException e) {
            log.debug("RecordItemJsonMarshaller: no available request: $e")
        }

        Map json = [:]
        json.with {
            id = item.id
            whenCreated = item.whenCreated
            outgoing = item.outgoing
            hasAwayMessage = item.hasAwayMessage
            isAnnouncement = item.isAnnouncement
            receipts = item.receipts
            if (item.authorName) authorName = item.authorName
            if (item.authorId) authorId = item.authorId
            if (item.authorType) authorType = item.authorType.toString()

            if (item instanceof ReadOnlyRecordCall) {
                durationInSeconds = item.durationInSeconds
                hasVoicemail = item.hasVoicemail
                if (item.hasVoicemail) {
                    voicemailUrl = item.voicemailUrl
                    voicemailInSeconds = item.voicemailInSeconds
                }
                if (item.callContents) {
                    callContents = item.callContents
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

                noteContents = item.noteContents
                location = item.location
                images = item.images
            }
        }
        if (uploadErrors) {
            json.uploadErrors = uploadErrors
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
