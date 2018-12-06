package org.textup.rest.marshaller

import grails.compiler.GrailsTypeChecked
import groovy.util.logging.Log4j
import javax.servlet.http.HttpServletRequest
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.codehaus.groovy.grails.web.mapping.LinkGenerator
import org.textup.*
import org.textup.rest.*
import org.textup.type.*
import org.textup.util.*

@GrailsTypeChecked
@Log4j
class RecordItemJsonMarshaller extends JsonNamedMarshaller {
    static final Closure marshalClosure = { String namespace, GrailsApplication grailsApplication,
        LinkGenerator linkGenerator, ReadOnlyRecordItem item ->

        Map json = [:]
        json.with {
            id = item.id
            whenCreated = item.whenCreated
            outgoing = item.outgoing
            hasAwayMessage = item.hasAwayMessage
            isAnnouncement = item.isAnnouncement
            wasScheduled = item.wasScheduled
            receipts = item.groupReceiptsByStatus()
            media = item.readOnlyMedia
            if (item.authorName) { authorName = item.authorName }
            if (item.authorId) { authorId = item.authorId }
            if (item.authorType) { authorType = item.authorType.toString() }
            if (item.noteContents) { noteContents = item.noteContents }

            if (item instanceof ReadOnlyRecordCall) {
                durationInSeconds = item.durationInSeconds
                voicemailInSeconds = item.voicemailInSeconds
                type = RecordItemType.CALL.toString()
            }
            else if (item instanceof ReadOnlyRecordText) {
                contents = item.contents
                type = RecordItemType.TEXT.toString()
            }
            else if (item instanceof ReadOnlyRecordNote) {
                whenChanged = item.whenChanged
                isDeleted = item.isDeleted
                isReadOnly = item.isReadOnly
                revisions = item.revisions
                location = item.readOnlyLocation
                type = "NOTE"
            }
        }
        Utils.tryGetFromRequest(Constants.REQUEST_TIMEZONE)
            .logFail("RecordItemJsonMarshaller: no available request", LogLevel.DEBUG)
            .thenEnd { String tz = null ->
                json.whenCreated = DateTimeUtils.toDateTimeWithZone(json.whenCreated, tz)
                json.whenChanged = DateTimeUtils.toDateTimeWithZone(json.whenChanged, tz)
            }
        // find owner, may be a contact or a tag
        Contact c1 = Contact.findByRecord(item.readOnlyRecord)
        json.contact = c1?.id
        json.ownerName = c1?.nameOrNumber
        if (!json.contact) {
            ContactTag ct1 = ContactTag.findByRecord(item.readOnlyRecord)
            json.tag = ct1?.id
            json.ownerName = ct1?.name
        }
        json.links = [:] << [self:linkGenerator.link(namespace:namespace,
            resource:"record", action:"show", id:item.id, absolute:false)]
        json
    }

    RecordItemJsonMarshaller() {
        super(ReadOnlyRecordItem, marshalClosure)
    }
}
