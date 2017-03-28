package org.textup.rest.marshallers

import grails.compiler.GrailsCompileStatic
import grails.plugin.springsecurity.SpringSecurityService
import javax.servlet.http.HttpServletRequest
import org.codehaus.groovy.grails.web.mapping.LinkGenerator
import org.codehaus.groovy.grails.web.util.WebUtils
import org.textup.*
import org.textup.rest.*
import org.textup.types.RecordItemType
import org.textup.validator.ImageInfo

@GrailsCompileStatic
class RecordItemJsonMarshaller extends JsonNamedMarshaller {
    static final Closure marshalClosure = { String namespace,
        SpringSecurityService springSecurityService, AuthService authService,
        LinkGenerator linkGenerator, RecordItem item ->

        HttpServletRequest request = WebUtils.retrieveGrailsWebRequest().currentRequest
        Collection<ImageInfo> uploadErrors =
            Helpers.toList(request.getAttribute(Constants.UPLOAD_ERRORS))

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
            else if (item.instanceOf(RecordNote)) {
                RecordNote note = item as RecordNote
                whenChanged = note.whenChanged
                isDeleted = note.isDeleted
                revisions = note.revisions

                noteContents = note.noteContents
                location = note.location
                images = note.images
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
        super(RecordItem, marshalClosure)
    }
}
