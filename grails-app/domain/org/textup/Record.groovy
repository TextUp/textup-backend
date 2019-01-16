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

    Result<? extends RecordItem> storeOutgoing(RecordItemType type, Author a1 = null,
        String msg = null, MediaInfo mInfo = null) {

        tryCreateItem(type, msg)
            .then { RecordItem rItem1 ->
                rItem1.outgoing = true
                if (type == RecordItemType.CALL) {
                    rItem1.noteContents = msg
                }
                finishAdd(rItem1, a1, mInfo)
            }
    }

    Result<RecordText> storeIncomingText(IncomingText text, IncomingSession is1, MediaInfo mInfo = null) {
        tryCreateItem(RecordItemType.TEXT, text.message)
            .then { RecordText rText1 ->
                rText1.outgoing = false
                finishAdd(rText1, is1.toAuthor(), mInfo)
            }
            .then { RecordText rText1 ->
                RecordItemReceipt
                    .tryCreate(rText1, text.apiId, ReceiptStatus.SUCCESS, is1.number)
                    .curry(rText1)
            }
            .then { RecordText rText1, RecordItemReceipt rpt1 ->
                rpt1.numBillable = text.numSegments
                DomainUtils.trySave(rText1, ResultStatus.CREATED)
            }
    }

    Result<RecordCall> storeIncomingCall(String apiId, IncomingSession is1) {
        tryCreateItem(RecordItemType.CALL, null)
            .then { RecordCall rCall1 ->
                rCall1.outgoing = false
                finishAdd(rCall1, is1.toAuthor(), null)
            }
            .then { RecordCall rCall1 ->
                RecordItemReceipt
                    .tryCreate(rCall1, apiId, ReceiptStatus.SUCCESS, is1.number)
                    .curry(rCall1)
            }
            .then { RecordCall rCall1 -> DomainUtils.trySave(rCall1, ResultStatus.CREATED) }
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
