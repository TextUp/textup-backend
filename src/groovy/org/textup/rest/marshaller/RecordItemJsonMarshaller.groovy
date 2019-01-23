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

            if (item instanceof ReadOnlyRecordCall) {
                durationInSeconds  = rItem1.durationInSeconds
                type               = RecordItemType.CALL.toString()
                voicemailInSeconds = rItem1.voicemailInSeconds
            }
            else if (item instanceof ReadOnlyRecordText) {
                contents = rItem1.contents
                type     = RecordItemType.TEXT.toString()
            }
            else if (item instanceof ReadOnlyRecordNote) {
                isDeleted   = rItem1.isDeleted
                isReadOnly  = rItem1.isReadOnly
                location    = rItem1.readOnlyLocation
                revisions   = rItem1.revisions
                type        = RecordItemType.NOTE.toString()
                whenChanged = rItem1.whenChanged
            }
        }

        RequestUtils.tryGetFromRequest(RequestUtils.TIMEZONE)
            .thenEnd { String tz ->
                json.whenCreated = DateTimeUtils.toDateTimeWithZone(json.whenCreated, tz)
                json.whenChanged = DateTimeUtils.toDateTimeWithZone(json.whenChanged, tz)
            }

        RequestUtils.tryGetFromRequest(RequestUtils.PHONE_ID)
            .then { Long pId ->
                PhoneRecord pr1 = PhoneRecords.buildActiveForRecordIds([rItem1.id])
                    .build(PhoneRecords.forPhoneIds([pId]))
                    .list(max: 1)[0]
                if (pr1) {
                    if (pr1 instanceof GroupPhoneRecord) {
                        json.tag = pr1.id
                    }
                    else { json.contact = pr1.id }
                }
            }

        json
    }

    RecordItemJsonMarshaller() {
        super(ReadOnlyRecordItem, marshalClosure)
    }
}
