package org.textup

import groovy.transform.EqualsAndHashCode
import org.joda.time.DateTime

@EqualsAndHashCode
class Record {

    ResultFactory resultFactory

    static transients = ["resultFactory"]
    static constraints = {
    }

    /*
	Has many:
		RecordItem
	*/

    ////////////
    // Events //
    ////////////
    
    def beforeDelete() {
        Record.withNewSession {
            RecordItem.where { record == this }.deleteAll()
        }
    }

    ////////////////////
    // Helper methods //
    ////////////////////

    Result<RecordText> addText(Map params, Author auth) { addAny(RecordText, params, auth) }
    Result<RecordNote> addNote(Map params, Author auth) { addAny(RecordNote, params, auth) }
    Result<RecordCall> addCall(Map params, Author auth) { addAny(RecordCall, params, auth) }
    private Result addAny(Class<RecordItem> clazz, Map params, Author auth) {
        this.add(clazz.newInstance(params), auth)
    }
    Result<RecordItem> add(RecordItem item, Author auth) {
        if (item) {
            item.author = auth
            item.record = this 
            if (item.save()) { resultFactory.success(item) }
            else { resultFactory.failWithValidationErrors(item.errors) }   
        }
        else { resultFactory.failWithMessage("record.error.noRecordItem") }
    }
    Result<List<RecordItem>> addAll(List<RecordItem> items, Author auth) {
        List<RecordItem> added = []
        items.each { RecordItem item ->
            Result<RecordItem> result = add(item, auth)
            if (result.success) {
                added << result.payload
            }
        }
        resultFactory.success(added)
    }
    Result<RecordNote> editNote(long noteId, Map params, Author auth) {
        RecordNote n1 = RecordNote.get(noteId)
        if (n1) {
            if (n1.editable) {
                n1.note = params.note
                n1.author = auth 
                if (n1.save()) { resultFactory.success(n1) }
                else { resultFactory.failWithValidationErrors(n1.errors) }
            }
            else { resultFactory.failWithMessage("record.error.noteUneditable", [noteId]) }
        }
        else { resultFactory.failWithMessage("record.error.noteNotFound", [noteId]) }
    }

    /////////////////////
    // Property Access //
    /////////////////////
    
    List<RecordItem> getItems(Map params) {
        RecordItem.forRecord(this).list(params)
    }
    int countItems() {
        RecordItem.forRecord(this).count()
    }

    List<RecordItem> getSince(DateTime since, Map params=[:]) {
        if (!since) { return [] }
        RecordItem.forRecordDateSince(this, since).list(params) ?: []
    }
    int countSince(DateTime since) {
        if (!since) { return 0 }
        RecordItem.forRecordDateSince(this, since).count()
    }

    List<RecordItem> getBetween(DateTime start, DateTime end, Map params=[:]) {
        if (!start || !end) { return [] }
        if (end.isBefore(start)) { 
            DateTime exchange = start 
            start = end 
            end = exchange
        }
        RecordItem.forRecordDateBetween(this, start, end).list(params) ?: []
    }
    int countBetween(DateTime start, DateTime end) {
        if (!start || !end) { return 0 }
        if (end.isBefore(start)) { 
            DateTime exchange = start 
            start = end 
            end = exchange
        }
        RecordItem.forRecordDateBetween(this, start, end).count()
    }
}
