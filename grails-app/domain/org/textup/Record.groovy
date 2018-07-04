package org.textup

import grails.compiler.GrailsTypeChecked
import groovy.transform.EqualsAndHashCode
import groovy.transform.TypeCheckingMode
import org.jadira.usertype.dateandtime.joda.PersistentDateTime
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.textup.type.VoiceLanguage
import org.textup.validator.Author

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

    // We manually associate note (in TempRecordNote) with record and author
    // We don't use the addAny methods because adding a note should not
    // trigger a record activity update like adding a text or a call should
    Result<RecordText> addText(Map params, Author auth = null) {
        addAny(RecordText, params, auth)
    }
    Result<RecordCall> addCall(Map params, Author auth = null) {
        addAny(RecordCall, params, auth)
    }
    protected Result addAny(Class<? extends RecordItem> clazz, Map params,
        Author auth = null) {
        this.add(clazz.newInstance(params), auth)
    }
    // No method to addNote because we handle adding in TempRecordNote
    // we don't do add notes here because we adding a note should not
    // trigger a record activity update like adding a text or a call should
    Result<RecordItem> add(RecordItem item, Author auth = null) {
        if (item) {
            item.author = auth
            item.record = this
            if (item.save()) {
                this.updateLastRecordActivity()
                resultFactory.success(item)
            }
            else { resultFactory.failWithValidationErrors(item.errors) }
        }
        else {
            resultFactory.failWithCodeAndStatus("record.noRecordItem",
                ResultStatus.BAD_REQUEST)
        }
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
