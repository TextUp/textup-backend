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

@GrailsTypeChecked
@EqualsAndHashCode(callSuper = true)
class RecordCall extends RecordItem implements ReadOnlyRecordCall {

    int voicemailInSeconds = 0

    static constraints = { // default nullable: false
        voicemailInSeconds minSize:0
    }

    static Result<RecordCall> tryCreate(Record rec1) {
        DomainUtils.trySave(new RecordCall(record: rec1), ResultStatus.CREATED)
    }
    static Result<RecordCall> tryUpdateVoicemail(RecordCall rCall1, int duration,
        List<MediaElement> elements) {

        rCall1.record.updateLastActivity()
        DomainUtils.trySave(rCall1.record)
            .then { MediaInfo.tryCreate(rCall1.media) }
            .then { MediaInfo mInfo ->
                elements.each { MediaElement el1 -> mInfo.addToMediaElements(el1) }
                rCall1.with {
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

    // Properties
    // ----------

    int getIsVoicemail() {
        voicemailInSeconds != 0
    }

    int getDurationInSeconds() {
        int duration = 0
        receipts?.each { RecordItemReceipt rpt1 ->
            if (rpt1.numBillable && rpt1.numBillable > duration) {
                duration = rpt1.numBillable
            }
        }
        duration
    }

    // Helpers
    // -------

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
