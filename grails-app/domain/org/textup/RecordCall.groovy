package org.textup

import grails.compiler.GrailsTypeChecked
import groovy.transform.EqualsAndHashCode
import groovy.transform.TypeCheckingMode
import org.joda.time.DateTime
import org.restapidoc.annotation.*
import org.textup.type.ReceiptStatus
import org.textup.validator.TempRecordReceipt

@GrailsTypeChecked
@EqualsAndHashCode(callSuper=true)
@RestApiObject(name="RecordCall", description="A phone call entry in a contact's record.")
class RecordCall extends RecordItem implements ReadOnlyRecordCall {

    VoicemailService voicemailService

    // explicitly store the value of the key the voicemail is stored under
    String voicemailKey

    @RestApiObjectField(
        description    = "Duration of the voicemail",
        allowedType    = "Number",
        useForCreation = false)
    int voicemailInSeconds = 0

    @RestApiObjectFields(params=[
        @RestApiObjectField(
            apiFieldName      = "callContact",
            description       = "Id of a contact to call",
            allowedType       = "Number",
            mandatory         = false,
            useForCreation    = true,
            presentInResponse = false),
        @RestApiObjectField(
            apiFieldName      = "callSharedContact",
            description       = "Id of a contact shared with us to call",
            allowedType       = "Number",
            mandatory         = false,
            useForCreation    = true,
            presentInResponse = false),
        @RestApiObjectField(
            apiFieldName   = "durationInSeconds",
            description    = "Duration of the call",
            allowedType    = "Number",
            useForCreation = false),
        @RestApiObjectField(
            apiFieldName   = "hasVoicemail",
            description    = "Whether or not this call has a voicemail",
            allowedType    = "Boolean",
            useForCreation = false),
        @RestApiObjectField(
            apiFieldName      = "voicemailUrl",
            description       = "Url to access the voicemail at",
            allowedType       = "String",
            mandatory         = false,
            useForCreation    = false,
            presentInResponse = true)
    ])
    static transients = ["voicemailService"]
    static constraints = {
        voicemailKey nullable: true, blank: true
        voicemailInSeconds minSize:0
    }

    // Property Access
    // ---------------

    int getDurationInSeconds() {
        int duration = 0
        this.receipts?.each { RecordItemReceipt rpt1 ->
            if (rpt1.numBillable && rpt1.numBillable > duration) {
                duration = rpt1.numBillable
            }
        }
        duration
    }

    @Override
    RecordItemStatus groupReceiptsByStatus() {
        // for outgoing calls, exclude the receipt with the longest duration because this is the
        // parent bridge call from TextUp to the staff member’s personal phone
        new RecordItemStatus(this.outgoing ? showOnlyContactReceipts() : this.receipts)
    }

    boolean getHasVoicemail() {
        this.hasAwayMessage && this.voicemailInSeconds > 0 && this.voicemailKey
    }
    String getVoicemailUrl() {
        if (!this.hasVoicemail) {
            return ""
        }
        voicemailService.getVoicemailUrl(this.voicemailKey)
    }

    // Helpers
    // -------

    protected Collection<RecordItemReceipt> showOnlyContactReceipts() {
        List<RecordItemReceipt> rpts = []
        Integer longestReceiptIndex
        int longestDurationSoFar = 0, numSkipped = 0
        this.receipts?.eachWithIndex { RecordItemReceipt rpt1, int thisIndex ->
            if (rpt1.numBillable) {
                if (rpt1.numBillable > longestDurationSoFar) {
                    longestReceiptIndex = thisIndex
                    longestDurationSoFar = rpt1.numBillable
                }
                rpts << rpt1
            }
            // exclude receipts with null durations
            else { numSkipped++ }
        }
        // remove longest duration, if possible
        if (rpts.size() > 0 && longestReceiptIndex != null &&
            longestReceiptIndex - numSkipped >= 0) {
            rpts.remove(longestReceiptIndex - numSkipped)
        }
        rpts
    }
}
