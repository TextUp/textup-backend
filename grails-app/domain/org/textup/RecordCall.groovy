package org.textup

import grails.compiler.GrailsTypeChecked
import groovy.transform.EqualsAndHashCode
import groovy.transform.TypeCheckingMode
import org.joda.time.DateTime
import org.restapidoc.annotation.*
import org.textup.type.ReceiptStatus
import org.textup.validator.TempRecordReceipt

@GrailsTypeChecked
@EqualsAndHashCode(callSuper = true)
@RestApiObject(name = "RecordCall", description = "A phone call entry in a contact's record.")
class RecordCall extends RecordItem implements ReadOnlyRecordCall {

    @RestApiObjectField(
        description    = "Duration of the voicemail",
        allowedType    = "Number",
        useForCreation = false)
    int voicemailInSeconds = 0

    @RestApiObjectFields(params = [
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
            useForCreation = false)
    ])
    static constraints = { // default nullable: false
        voicemailInSeconds minSize:0
    }

    // Methods
    // -------

    // duration in seconds only present when the call has been completed
    // see `CallDuration` in https://www.twilio.com/docs/voice/api/call#statuscallbackevent
    boolean isStillOngoing() {
        tryGetParentReceipt()?.numBillable == null // can be zero if call was ended immediately
    }

    String buildParentCallApiId() {
        tryGetParentReceipt()?.apiId
    }

    // Property Access
    // ---------------

    int getDurationInSeconds() {
        tryGetParentReceipt()?.numBillable ?: 0
    }

    @Override
    RecordItemStatus groupReceiptsByStatus() {
        // for outgoing calls, exclude the receipt with the longest duration because this is the
        // parent bridge call from TextUp to the staff member’s personal phone.
        // Also, do not exclude max numBillable for not doing this for outgoing scheduled calls
        // because in this case, these are  direct message calls and not a bridge calls so
        // we don’t have to call the staff member first
        if (this.outgoing && !this.wasScheduled) {
            new RecordItemStatus(showOnlyContactReceipts())
        }
        else { new RecordItemStatus(this.receipts) }
    }

    // Helpers
    // -------

    // parent receipt is the one with the longest duration
    protected RecordItemReceipt tryGetParentReceipt() {
        this.receipts?.max { RecordItemReceipt rpt1 -> rpt1?.numBillable }
    }

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
