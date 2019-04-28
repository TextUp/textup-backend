package org.textup

import grails.compiler.GrailsTypeChecked
import groovy.transform.EqualsAndHashCode
import groovy.transform.TypeCheckingMode
import org.joda.time.DateTime
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*

@EqualsAndHashCode(callSuper = true)
@GrailsTypeChecked
class RecordCall extends RecordItem implements ReadOnlyRecordCall {

    int voicemailInSeconds = 0

    static constraints = { // default nullable: false
        voicemailInSeconds min: 0
    }

    static Result<RecordCall> tryCreate(Record rec1) {
        DomainUtils.trySave(new RecordCall(record: rec1), ResultStatus.CREATED)
    }
    static Result<RecordCall> tryUpdateVoicemail(RecordCall rCall1, int duration,
        List<MediaElement> elements) {

        rCall1.record.updateLastActivity()
        DomainUtils.trySave(rCall1.record)
            .then { MediaInfo.tryCreate(rCall1.media) }
            .then { MediaInfo mInfo -> mInfo.tryAddAllElements(elements) }
            .then { MediaInfo mInfo ->
                rCall1.with {
                    media = mInfo
                    hasAwayMessage = true
                    voicemailInSeconds = duration
                }
                DomainUtils.trySave(rCall1)
            }
    }

    // Methods
    // -------

    @Override
    RecordItemReceiptInfo groupReceiptsByStatus() {
        // for outgoing calls, exclude the receipt with the longest duration because this is the
        // parent bridge call from TextUp to the staff member’s personal phone.
        // Also, do not exclude max numBillable for not doing this for outgoing scheduled calls
        // because in this case, these are  direct message calls and not a bridge calls so
        // we don’t have to call the staff member first
        if (outgoing && !wasScheduled) {
            new RecordItemReceiptInfo(showOnlyContactReceipts())
        }
        else { new RecordItemReceiptInfo(receipts) }
    }

    // duration in seconds only present when the call has been completed
    // see `CallDuration` in https://www.twilio.com/docs/voice/api/call#statuscallbackevent
    boolean isStillOngoing() {
        tryGetParentReceipt()?.numBillable == null // can be zero if call was ended immediately
    }

    String buildParentCallApiId() {
        tryGetParentReceipt()?.apiId
    }

    // Properties
    // ----------

    boolean getIsVoicemail() {
        voicemailInSeconds != 0
    }

    int getDurationInSeconds() {
        tryGetParentReceipt()?.numBillable ?: 0
    }

    // Helpers
    // -------

    // parent receipt is the one with the longest duration
    protected RecordItemReceipt tryGetParentReceipt() {
        receipts?.max { RecordItemReceipt rpt1 -> rpt1?.numBillable }
    }

    protected Collection<RecordItemReceipt> showOnlyContactReceipts() {
        List<RecordItemReceipt> rpts = []
        Integer longestReceiptIndex
        int longestDurationSoFar = 0, numSkipped = 0
        receipts?.eachWithIndex { RecordItemReceipt rpt1, int thisIndex ->
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
