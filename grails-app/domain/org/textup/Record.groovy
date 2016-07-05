package org.textup

import grails.compiler.GrailsTypeChecked
import groovy.transform.EqualsAndHashCode
import groovy.transform.TypeCheckingMode
import org.jadira.usertype.dateandtime.joda.PersistentDateTime
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.textup.validator.Author

@GrailsTypeChecked
@EqualsAndHashCode
class Record {

    ResultFactory resultFactory

    DateTime lastRecordActivity = DateTime.now(DateTimeZone.UTC)

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
        else { resultFactory.failWithMessage("record.noRecordItem") }
    }

    // Property Access
    // ---------------

    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    List<RecordItem> getItems(Map params=[:]) {
        RecordItem.forRecord(this).list(params)
    }
    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    int countItems() {
        RecordItem.forRecord(this).count()
    }
    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    List<RecordItem> getSince(DateTime since, Map params=[:]) {
        if (!since) { return [] }
        RecordItem.forRecordDateSince(this, since).list(params) ?: []
    }
    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    int countSince(DateTime since) {
        if (!since) { return 0 }
        RecordItem.forRecordDateSince(this, since).count()
    }
    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    List<RecordItem> getBetween(DateTime start, DateTime end, Map params=[:]) {
        if (!start || !end) { return [] }
        if (end.isBefore(start)) {
            DateTime exchange = start
            start = end
            end = exchange
        }
        RecordItem.forRecordDateBetween(this, start, end).list(params) ?: []
    }
    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    int countBetween(DateTime start, DateTime end) {
        if (!start || !end) { return 0 }
        if (end.isBefore(start)) {
            DateTime exchange = start
            start = end
            end = exchange
        }
        RecordItem.forRecordDateBetween(this, start, end).count()
    }

    List<FutureMessage> getFutureMessages(Map params=[:]) {

    }
    int countFutureMessages() {

    }
}
