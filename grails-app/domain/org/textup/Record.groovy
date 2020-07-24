package org.textup

import grails.compiler.GrailsTypeChecked
import groovy.transform.EqualsAndHashCode
import org.jadira.usertype.dateandtime.joda.PersistentDateTime
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*

@EqualsAndHashCode
@GrailsTypeChecked
class Record implements ReadOnlyRecord, WithId, CanSave<Record> {

    // Need to declare id for it to be considered in equality operator
    // see: https://stokito.wordpress.com/2014/12/19/equalsandhashcode-on-grails-domains/
    Long id

    DateTime lastRecordActivity = JodaUtils.utcNow()
    VoiceLanguage language = VoiceLanguage.ENGLISH

    static mapping = {
        lastRecordActivity type: PersistentDateTime
        // turn off optimistic locking because expect high concurrent writes
        version false
    }

    static Result<Record> tryCreate(VoiceLanguage lang1 = VoiceLanguage.ENGLISH) {
        Record rec1 = new Record(language: lang1)
        DomainUtils.trySave(rec1, ResultStatus.CREATED)
    }

    // Methods
    // ---------

    void updateLastActivity() {
        lastRecordActivity = JodaUtils.utcNow()
    }

    Result<? extends RecordItem> storeOutgoing(RecordItemType type, Author a1,
        String message = null, MediaInfo mInfo = null) {

        tryCreateItem(type, message)
            .then { RecordItem rItem1 ->
                rItem1.outgoing = true
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
        switch (type) {
            case RecordItemType.TEXT:
                RecordText.tryCreate(this, msg)
                break
            case RecordItemType.CALL:
                RecordCall.tryCreate(this).then { RecordCall rCall1 ->
                    rCall1.noteContents = msg
                    IOCUtils.resultFactory.success(rCall1)
                }
                break
            default:
                IOCUtils.resultFactory.failWithCodeAndStatus("record.cannotAddNoteHere",
                    ResultStatus.INTERNAL_SERVER_ERROR)
        }
    }

    // don't handle note here because adding note should not update record timestamp
    protected Result<? extends RecordItem> finishAdd(RecordItem rItem1, Author a1, MediaInfo mInfo) {
        rItem1.with {
            author = a1
            media = mInfo
        }
        updateLastActivity()
        DomainUtils.trySave(rItem1, ResultStatus.CREATED)
    }
}
