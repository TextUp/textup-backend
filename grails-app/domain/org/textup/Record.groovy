package org.textup

import grails.compiler.GrailsTypeChecked
import groovy.transform.EqualsAndHashCode
import groovy.transform.TypeCheckingMode
import org.jadira.usertype.dateandtime.joda.PersistentDateTime
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*

@GrailsTypeChecked
@EqualsAndHashCode
class Record implements ReadOnlyRecord, WithId {

    DateTime lastRecordActivity = DateTime.now(DateTimeZone.UTC)
    VoiceLanguage language = VoiceLanguage.ENGLISH

    static constraints = {
    }
    static mapping = {
        lastRecordActivity type:PersistentDateTime
    }

    /*
	Has many:
		RecordItem
	*/

    // Timestamp
    // ---------

    void updateLastRecordActivity() {
        this.lastRecordActivity = DateTime.now(DateTimeZone.UTC)
    }

    // Add to record
    // -------------

    Result<RecordText> storeOutgoingText(String message, Author author = null, MediaInfo mInfo = null) {
        add(new RecordText(outgoing: true, contents: message, media: mInfo), author)
    }
    Result<RecordCall> storeOutgoingCall(Author author = null, String message = null, MediaInfo mInfo = null) {
        add(new RecordCall(outgoing: true, noteContents: message, media: mInfo), author)
    }

    Result<RecordText> storeIncomingText(IncomingText text, IncomingSession session1, MediaInfo mInfo = null) {
        RecordText rText1 = new RecordText(outgoing: false, contents: text.message, media: mInfo)
        add(rText1, session1.toAuthor()).then {
            RecordItemReceipt receipt = new RecordItemReceipt(status: ReceiptStatus.SUCCESS,
                apiId:text.apiId,
                numBillable: text.numSegments)
            receipt.contactNumber = session1.number
            rText1.addToReceipts(receipt)
            rText1.save() ? IOCUtils.resultFactory.success(rText1) :
                IOCUtils.resultFactory.failWithValidationErrors(rText1.errors)
        }
    }
    Result<RecordCall> storeIncomingCall(String apiId, IncomingSession session1) {
        RecordCall rCall1 = new RecordCall(outgoing: false)
        add(rCall1, session1.toAuthor()).then {
            RecordItemReceipt receipt = new RecordItemReceipt(status: ReceiptStatus.SUCCESS,
                apiId: apiId)
            receipt.contactNumber = session1.number
            rCall1.addToReceipts(receipt)
            rCall1.save() ? IOCUtils.resultFactory.success(rCall1) :
                IOCUtils.resultFactory.failWithValidationErrors(rCall1.errors)
        }
    }

    // No method to addNote because we handle adding in TempRecordNote
    // we don't do add notes here because we adding a note should not
    // trigger a record activity update like adding a text or a call should
    protected Result<RecordItem> add(RecordItem item, Author author = null) {
        if (item) {
            item.author = author
            item.record = this
            if (item.save()) {
                this.updateLastRecordActivity()
                IOCUtils.resultFactory.success(item, ResultStatus.CREATED)
            }
            else { IOCUtils.resultFactory.failWithValidationErrors(item.errors) }
        }
        else { IOCUtils.resultFactory.failWithCodeAndStatus("record.noRecordItem", ResultStatus.BAD_REQUEST) }
    }

    // Property Access
    // ---------------

    boolean hasUnreadInfo(DateTime lastTouched) {
        RecordItem.forRecordIdsWithOptions([this.id], lastTouched).count() > 0
    }

    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    UnreadInfo getUnreadInfo(DateTime lastTouched) {
        Closure notOutgoing = { eq("outgoing", false) }
        UnreadInfo uInfo = new UnreadInfo()
        uInfo.with {
            numTexts = RecordItem
                .forRecordIdsWithOptions([this.id], lastTouched, null, [RecordText])
                .build(notOutgoing)
                .count()
            numCalls = RecordItem
                .forRecordIdsWithOptions([this.id], lastTouched, null, [RecordCall])
                .build(notOutgoing)
                .build { eq("voicemailInSeconds", 0) }
                .count()
            numVoicemails = RecordItem
                .forRecordIdsWithOptions([this.id], lastTouched, null, [RecordCall])
                .build(notOutgoing)
                .build { gt("voicemailInSeconds", 0) }
                .count()
        }
        uInfo
    }

    List<FutureMessage> getFutureMessages(Map params=[:]) {
        FutureMessage.findAllByRecordAndIsDone(this, false, params)
    }
    int countFutureMessages() {
        FutureMessage.countByRecordAndIsDone(this, false)
    }
}
