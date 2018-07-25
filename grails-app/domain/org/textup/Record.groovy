package org.textup

import grails.compiler.GrailsTypeChecked
import groovy.transform.EqualsAndHashCode
import groovy.transform.TypeCheckingMode
import org.jadira.usertype.dateandtime.joda.PersistentDateTime
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.textup.type.VoiceLanguage
import org.textup.validator.Author
import org.textup.validator.IncomingText

@GrailsTypeChecked
@EqualsAndHashCode
class Record implements ReadOnlyRecord {

    ResultFactory resultFactory

    DateTime lastRecordActivity = DateTime.now(DateTimeZone.UTC)
    VoiceLanguage language = VoiceLanguage.ENGLISH

    static transients = ["resultFactory"]
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

    Result<RecordText> storeOutgoingText(String message, Author author, MediaInfo mInfo = null) {
        add(new RecordText(outgoing: true, contents: message, media: mInfo), author)
    }
    Result<RecordCall> storeOutgoingCall(Author author, String message = null, MediaInfo mInfo = null) {
        add(new RecordCall(outgoing: true, noteContents: message, media: mInfo), author)
    }

    Result<RecordText> storeIncomingText(IncomingText text, IncomingSession session1, MediaInfo mInfo = null) {
        RecordText rText1 = new RecordText(outgoing: false, contents: text.message, media: mInfo)
        add(rText1, session1.toAuthor()).then {
            RecordItemReceipt receipt = new RecordItemReceipt(apiId:text.apiId)
            receipt.contactNumber = session1.number
            rText1.addToReceipts(receipt)
            rText1.save() ? resultFactory.success(rText1) :
                resultFactory.failWithValidationErrors(rText1.errors)
        }
    }
    Result<RecordCall> storeIncomingCall(String apiId, IncomingSession session1) {
        RecordCall rCall1 = new RecordCall(outgoing: false)
        add(rCall1, session1.toAuthor()).then {
            RecordItemReceipt receipt = new RecordItemReceipt(apiId: apiId)
            receipt.contactNumber = session1.number
            rCall1.addToReceipts(receipt)
            rCall1.save() ? resultFactory.success(rCall1) :
                resultFactory.failWithValidationErrors(rCall1.errors)
        }
    }

    // No method to addNote because we handle adding in TempRecordNote
    // we don't do add notes here because we adding a note should not
    // trigger a record activity update like adding a text or a call should
    protected Result<RecordItem> add(RecordItem item, Author author) {
        if (item) {
            item.author = author
            item.record = this
            if (item.save()) {
                this.updateLastRecordActivity()
                resultFactory.success(item, ResultStatus.CREATED)
            }
            else { resultFactory.failWithValidationErrors(item.errors) }
        }
        else { resultFactory.failWithCodeAndStatus("record.noRecordItem", ResultStatus.BAD_REQUEST) }
    }

    // Property Access
    // ---------------

    List<RecordItem> getItems(Map params=[:]) {
        getItems([], params)
    }
    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    List<RecordItem> getItems(Collection<Class<? extends RecordItem>> types, Map params=[:]) {
        RecordItem.forRecord(this, types).list(params)
    }
    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    int countItems(Collection<Class<? extends RecordItem>> types = []) {
        RecordItem.forRecord(this, types).count()
    }

    List<RecordItem> getSince(DateTime since, Map params=[:]) {
        getSince(since, [], params)
    }
    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    List<RecordItem> getSince(DateTime since, Collection<Class<? extends RecordItem>> types,
        Map params=[:]) {

        if (!since) { return [] }
        RecordItem.forRecordDateSince(this, since, types).list(params) ?: []
    }
    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    int countSince(DateTime since, Collection<Class<? extends RecordItem>> types = []) {
        if (!since) { return 0 }
        RecordItem.forRecordDateSince(this, since, types).count()
    }
    // specialized version of countSince that distinguishes between calls that have a voicemail
    // and calls that do not have a voicemail
    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    int countCallsSince(DateTime since, boolean hasVoicemail = false) {
        if (!since) { return 0 }
        RecordItem.forRecordDateSince(this, since, [RecordCall]).count {
            if (hasVoicemail) {
                gt("voicemailInSeconds", 0)
            }
            else { eq("voicemailInSeconds", 0) }
        }
    }


    List<RecordItem> getBetween(DateTime start, DateTime end, Map params=[:]) {
        getBetween(start, end, [], params)
    }
    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    List<RecordItem> getBetween(DateTime start, DateTime end, Collection<Class<? extends RecordItem>> types,
        Map params=[:]) {

        if (!start || !end) { return [] }
        if (end.isBefore(start)) {
            DateTime exchange = start
            start = end
            end = exchange
        }
        RecordItem.forRecordDateBetween(this, start, end, types).list(params) ?: []
    }
    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    int countBetween(DateTime start, DateTime end, Collection<Class<? extends RecordItem>> types = []) {
        if (!start || !end) { return 0 }
        if (end.isBefore(start)) {
            DateTime exchange = start
            start = end
            end = exchange
        }
        RecordItem.forRecordDateBetween(this, start, end, types).count()
    }

    List<FutureMessage> getFutureMessages(Map params=[:]) {
        FutureMessage.findAllByRecordAndIsDone(this, false, params)
    }
    int countFutureMessages() {
        FutureMessage.countByRecordAndIsDone(this, false)
    }
}
