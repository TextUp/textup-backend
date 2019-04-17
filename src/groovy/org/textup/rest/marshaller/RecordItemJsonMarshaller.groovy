package org.textup.rest.marshaller

import grails.compiler.GrailsTypeChecked
import groovy.util.logging.Log4j
import javax.servlet.http.HttpServletRequest
import org.textup.*
import org.textup.rest.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*

@GrailsTypeChecked
@Log4j
class RecordItemJsonMarshaller extends JsonNamedMarshaller {

    static final Closure marshalClosure = { ReadOnlyRecordItem rItem1 ->
        Map json = [:]
        json.with {
            hasAwayMessage = rItem1.hasAwayMessage
            id             = rItem1.id
            isAnnouncement = rItem1.isAnnouncement
            isDeleted      = rItem1.isDeleted
            links          = MarshallerUtils.buildLinks(RestUtils.RESOURCE_RECORD_ITEM, rItem1.id)
            media          = rItem1.readOnlyMedia
            outgoing       = rItem1.outgoing
            receipts       = rItem1.groupReceiptsByStatus()
            wasScheduled   = rItem1.wasScheduled
            whenCreated    = rItem1.whenCreated

            if (rItem1.authorName) authorName     = rItem1.authorName
            if (rItem1.authorId) authorId         = rItem1.authorId
            if (rItem1.authorType) authorType     = rItem1.authorType.toString()
            if (rItem1.noteContents) noteContents = rItem1.noteContents

            if (rItem1 instanceof ReadOnlyRecordCall) {
                durationInSeconds  = rItem1.durationInSeconds
                type               = RecordItemType.CALL.toString()
                voicemailInSeconds = rItem1.voicemailInSeconds
            }
            else if (rItem1 instanceof ReadOnlyRecordText) {
                contents = rItem1.contents
                type     = RecordItemType.TEXT.toString()
            }
            else if (rItem1 instanceof ReadOnlyRecordNote) {
                isReadOnly  = rItem1.isReadOnly
                location    = rItem1.readOnlyLocation
                revisions   = rItem1.revisions
                type        = RecordItemType.NOTE.toString()
                whenChanged = rItem1.whenChanged
            }
        }

        RequestUtils.tryGet(RequestUtils.TIMEZONE)
            .thenEnd { Object zoneName ->
                json.whenCreated = JodaUtils.toDateTimeWithZone(json.whenCreated, zoneName)
                json.whenChanged = JodaUtils.toDateTimeWithZone(json.whenChanged, zoneName)
            }

        // Associated phone owners
        Long pId = TypeUtils.to(Long, RequestUtils.tryGet(RequestUtils.PHONE_ID).payload)
        Collection<PhoneRecord> prs = PhoneRecords
            .buildActiveForRecordIds([rItem1.readOnlyRecord.id])
            .list()
        PhoneRecord ownerPr = pId ? prs.find { PhoneRecord pr1 -> pr1.phone.id == pId } : prs[0]
        // For frontend to properly associate this item with all available record owners
        Collection<Long> tagIds = [], contactIds = []
        prs.each { PhoneRecord pr1 ->
            if (pr1 instanceof GroupPhoneRecord) {
                tagIds << pr1.id
            }
            else { contactIds << pr1.id }
        }
        json.tags = tagIds
        json.contacts = contactIds
        // For pdf export, need to have the owner name for `From` or `To` fields
        if (ownerPr) {
            json.ownerName = ownerPr.toWrapper().tryGetSecureName().payload // for pdf export
        }

        json
    }

    RecordItemJsonMarshaller() {
        super(ReadOnlyRecordItem, marshalClosure)
    }
}
