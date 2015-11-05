package org.textup

import grails.gorm.DetachedCriteria
import groovy.transform.EqualsAndHashCode
import org.jadira.usertype.dateandtime.joda.PersistentDateTime
import org.joda.time.DateTime
import org.joda.time.DateTimeZone

@EqualsAndHashCode(callSuper=true)
class TeamContactTag extends ContactTag implements Contactable {

    def resultFactory
    def authService

    DateTime lastRecordActivity = DateTime.now(DateTimeZone.UTC)
	Record record

    static constraints = {
    }
    static mapping = {
        lastRecordActivity type:PersistentDateTime
    }

    /*
	Has many:
        TagMembership (from superclass)
	*/

    ////////////
    // Events //
    ////////////

    def beforeDelete() {
        TeamContactTag.withNewSession {
            TagMembership.where { tag == this }.deleteAll()
            //delete all receipts before deleting items
            def items = RecordItem.where { record == this.record }
            new DetachedCriteria(RecordItemReceipt).build {
                "in"("item", items.list())
            }.deleteAll()
            //delete all record items before deleting record
            items.deleteAll()
        }
    }
    def afterDelete() {
        TeamContactTag.withNewSession {
            Record.where { id == this.record.id }.deleteAll()
        }
    }
    def beforeValidate() {
        if (!this.record) {
            this.record = new Record([:])
            this.record.save()
        }
    }
    
    ////////////////////
    // Helper methods //
    ////////////////////

    /*
    Activity 
     */
    
    void updateLastRecordActivity() {
        this.lastRecordActivity = DateTime.now(DateTimeZone.UTC)
    }

    /*
    Manage record items
     */
	Result<RecordResult> call(Map params) {
        resultFactory.failWithMessage("teamContactTag.error.notSupported", [this.name])
    }
    Result<RecordResult> call(Map params, Author auth) {
        resultFactory.failWithMessage("teamContactTag.error.notSupported", [this.name])
    }

    Result<RecordResult> text(Map params) { 
        text(params, this.author)
    }
    Result<RecordResult> text(Map params, Author auth) { 
        //first add to this tag's record
        Result<RecordText> tagRes = addTextToRecord(params, auth)
        if (!tagRes.success) return tagRes
        RecordText tagText = tagRes.payload
        //then text each of the subscribers
        this.subscribers.each { TagMembership membership ->
            Contact subscriber = membership.contact 
            Result<RecordResult> subscriberRecordRes = subscriber.text(params, auth)
            if (subscriberRecordRes.success) {
                RecordText subRec = subscriberRecordRes.payload.newItems[0]
                //then copy over all of the receipts in the subcribers
                //to the tag's text's receipts
                subRec.receipts?.each { RecordItemReceipt r ->
                    RecordItemReceipt newR = new RecordItemReceipt(status:r.status, apiId:r.apiId)
                    newR.receivedByAsString = r.receivedBy.number
                    tagText.addToReceipts(newR)
                    newR.save()
                }
            }
            else { 
                log.error('''TeamContactTag.text: error sending text to 
                    subscriber $sub: ${subscriberRecordRes.payload}''')
            }
        }
        updateLastRecordActivity()
        if (tagText.save()) {
            resultFactory.successWithRecordResult(tagText)
        }
        else { resultFactory.failWithValidationErrors(tagText.errors) }
    }
    Result<RecordText> addTextToRecord(Map params, Author auth) {
        this.record.addText(params, auth)
    }
    Result<RecordText> addTextToRecord(Map params) {
        this.record.addText(params, this.author)
    }

    Result<RecordResult> addNote(Map params) { 
        addNote(params, this.author)
    }
    Result<RecordResult> addNote(Map params, Author auth) { 
        resultFactory.convertToRecordResult(record.addNote(params, auth))
    }

    Result<RecordResult> editNote(long noteId, Map params) { 
        editNote(noteId, params, this.author)
    }
    Result<RecordResult> editNote(long noteId, Map params, Author auth) { 
        resultFactory.convertToRecordResult(record.editNote(noteId, params, auth))
    }
    
    Author getAuthor() {
        Staff s1 = authService.getLoggedIn() 
        s1 ? new Author(name:s1.name, id:s1.id) : null
    }

    /*
    Numbers
     */
    Result<PhoneNumber> mergeNumber(String number, Map params) {
        resultFactory.failWithMessage("teamContactTag.error.notSupported", [this.name])
    }
    List<PhoneNumber> getNumbers() { [phone.number] }
    Result deleteNumber(String number) {
        resultFactory.failWithMessage("teamContactTag.error.notSupported", [this.name])
    }

    /////////////////////
    // Property Access //
    /////////////////////

    void setRecord(Record r) {
        this.record = r 
        this.record?.save()
    }

    List<RecordItem> getItems(Map params=[:]) { record.getItems(params) }
    int countItems() { record.countItems() }
    List<RecordItem> getSince(DateTime since, Map params=[:]) { record.getSince(since, params) }
    int countSince(DateTime since) { record.countSince(since) }
    List<RecordItem> getBetween(DateTime start, DateTime end, Map params=[:]) {
        record.getBetween(start, end, params)
    }
    int countBetween(DateTime start, DateTime end) {
        record.countBetween(start, end)
    }
}
