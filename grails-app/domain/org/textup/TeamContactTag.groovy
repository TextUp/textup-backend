package org.textup

import grails.gorm.DetachedCriteria
import groovy.transform.EqualsAndHashCode
import org.jadira.usertype.dateandtime.joda.PersistentDateTime
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.springframework.context.i18n.LocaleContextHolder as LCH
import org.springframework.transaction.TransactionDefinition

@EqualsAndHashCode(callSuper=true)
class TeamContactTag extends ContactTag implements Contactable {

    def resultFactory
    def authService
    def messageSource

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
    Result<RecordResult> notifySubscribers(String message) {
        Result<RecordText> textRes
        //we have to add text to record in new transaction because we are going to 
        //store the id the RecordText. The id is only available after the RecordText
        //has been persisted to the database.
        TeamContactTag.withTransaction([propagationBehavior: TransactionDefinition.PROPAGATION_REQUIRES_NEW]) {
            textRes = addTextToRecord(params, auth)
        }
        if (textRes.success) {
            RecordText tagText = textRes.payload
            tagText.refresh() //refresh after commiting so we have an object with an id
            Result<RecordResult> res = text(teamContactTagId:this.id, tagText:tagText, 
                contents:formatTextAnnouncement(message))
            if (res.success) {
                Result<RecordResult> cRes = callNotify(tagText, this.id, tagText.id)
                if (cRes.success) { res = res.merge(cRes) }
                else { res = cRes }
            }
            res
        }
        else { textRes }
    }
    Result<RecordResult> callNotify(RecordText tagText, Long teamContactTagId, Long recordTextId) {
        this.callSubscribers.each { TagMembership membership ->
            Contact subscriber = membership.contact
            Result<RecordResult> subscriberRecordRes = subscriber.callNotify(teamContactTagId, recordTextId, this.author)
            if (subscriberRecordRes.success) {
                RecordCall subRec = subscriberRecordRes.payload.newItems[0]
                //then copy over all of the receipts in the subcribers to the tag's text's receipts
                subRec.receipts?.each { RecordItemReceipt r ->
                    RecordItemReceipt newR = new RecordItemReceipt(status:r.status, apiId:r.apiId)
                    newR.receivedByAsString = r.receivedBy.number
                    tagText.addToReceipts(newR)
                    newR.save()
                }
            }
            else {
                log.error('''TeamContactTag.callNotify: error sending call announcement to
                    subscriber $sub: ${subscriberRecordRes.payload}''')
            }
        }
        updateLastRecordActivity()
        if (tagText.save()) { resultFactory.successWithRecordResult(tagText) }
        else { resultFactory.failWithValidationErrors(tagText.errors) }
    }
	Result<RecordResult> call(Staff staffMakingCall, Map params) {
        call(staffMakingCall, params, this.author)
    }
    Result<RecordResult> call(Staff staffMakingCall, Map params, Author auth) {
        resultFactory.failWithMessage("teamContactTag.error.notSupported", [this.name])
    }
    Result<RecordResult> text(Map params) {
        text(params, this.author)
    }
    Result<RecordResult> text(Map params, Author auth) {
        RecordText tagText = params.tagText
        //then text each of the subscribers
        this.textSubscribers.each { TagMembership membership ->
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
        if (tagText.save()) { resultFactory.successWithRecordResult(tagText) }
        else { resultFactory.failWithValidationErrors(tagText.errors) }
    }
    Result<RecordText> addTextToRecord(Map params) {
        this.record.addText(params, this.author)
    }
    protected String formatTextAnnouncement(String contents) {
        String formatted = contents
        if (contents) {
            formatted = messageSource.getMessage("teamContactTag.addInstructionsToContents", [contents, Constants.ACTION_UNSUBSCRIBE_ONE, this.name, Constants.ACTION_UNSUBSCRIBE_ALL] as Object[], LCH.getLocale())
        }
        formatted
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

    Long getContactId() {
        this.id
    }

    void setRecord(Record r) {
        this.record = r
        this.record?.save()
    }

    int countTextSuscribers() {
        TagMembership.countByTagAndHasUnsubscribedAndSubscriptionType(this, false, Constants.SUBSCRIPTION_TEXT)
    }
    List<TagMembership> getTextSubscribers() {
        TagMembership.findAllByTagAndHasUnsubscribedAndSubscriptionType(this, false, Constants.SUBSCRIPTION_TEXT)
    }
    int countCallSuscribers() {
        TagMembership.countByTagAndHasUnsubscribedAndSubscriptionType(this, false, Constants.SUBSCRIPTION_CALL)
    }
    List<TagMembership> getCallSubscribers() {
        TagMembership.findAllByTagAndHasUnsubscribedAndSubscriptionType(this, false, Constants.SUBSCRIPTION_CALL)
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
