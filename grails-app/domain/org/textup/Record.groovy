package org.textup

import grails.compiler.GrailsTypeChecked
import groovy.transform.EqualsAndHashCode
import org.jadira.usertype.dateandtime.joda.PersistentDateTime
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*

@GrailsTypeChecked
@EqualsAndHashCode
class Record implements ReadOnlyRecord, WithId, Saveable<Record> {

    DateTime lastRecordActivity = DateTimeUtils.now()
    VoiceLanguage language = VoiceLanguage.ENGLISH

    static mapping = {
        lastRecordActivity type: PersistentDateTime
    }

    static Result<Record> tryCreate() {
        DomainUtils.trySave(new Record(), ResultStatus.CREATED)
    }

    // Methods
    // ---------

    void updateLastRecordActivity() {
        lastRecordActivity = DateTimeUtils.now()
    }

    Result<? extends RecordItem> storeOutgoing(RecordItemType type, Author a1,
        String message = null, MediaInfo mInfo = null) {

        tryCreateItem(type, message)
            .then { RecordItem rItem1 ->
                rItem1.outgoing = true
                if (type == RecordItemType.CALL) {
                    rItem1.noteContents = message
                }
                finishAdd(rItem1, a1, mInfo)
            }
    }

    Result<? extends RecordItem> storeIncoming(RecordItemType type, Author a1, BasePhoneNumber bNum,
        String apiId, String message = null, Integer numBillable = null) {

        tryCreateItem(type, message)
            .then { RecordItem rItem1 ->
                rItem1.outgoing = false
                finishAdd(rItem1, a1, null)
            }
            .then { RecordItem rItem1 ->
                RecordItemReceipt
                    .tryCreate(rItem1, apiId, ReceiptStatus.SUCCESS, bNum)
                    .curry(rItem1)
            }
            .then { RecordItem rItem1, RecordItemReceipt rpt1 ->
                rpt1.numBillable = numBillable
                DomainUtils.trySave(rItem1, ResultStatus.CREATED)
            }
    }

    // Helpers
    // -------

    protected Result<? extends RecordItem> tryCreateItem(RecordItemType type, String msg) {
        type == RecordItemType.TEXT ? RecordText.tryCreate(this, msg) : RecordCall.tryCreate(this)
    }

    // don't handle note here because adding note should not update record timestamp
    protected Result<? extends RecordItem> finishAdd(RecordItem rItem1, Author a1, MediaInfo mInfo) {
        rItem1.author = a1
        rItem.media = mInfo
        updateLastRecordActivity()
        DomainUtils.trySave(rItem1, ResultStatus.CREATED)
    }
}
