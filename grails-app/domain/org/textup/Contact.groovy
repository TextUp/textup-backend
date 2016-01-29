package org.textup

import grails.gorm.DetachedCriteria
import groovy.transform.EqualsAndHashCode
import org.restapidoc.annotation.*
import groovy.util.logging.Log4j

@Log4j
@EqualsAndHashCode
@RestApiObject(name="Contact", description="A contact")
class Contact implements Contactable {

    def resultFactory

    Phone phone //phone that owns this contact
    Record record

    @RestApiObjectField(
        description    = "Name of this contact",
        mandatory      = false,
        defaultValue   = "",
        useForCreation = true)
    String name

    @RestApiObjectField(
        description = "Notes on this contact",
        mandatory      = false,
        defaultValue   = "",
        useForCreation = true)
	String note

    @RestApiObjectField(
        description    = "Status of this contact. Allowed: ACTIVE, UNREAD, ARCHIVED, BLOCKED",
        defaultValue   = "ACTIVE",
        mandatory      = false,
        useForCreation = true)
    ContactStatus status = ContactStatus.ACTIVE

    @RestApiObjectField(
        apiFieldName   = "numbers",
        description    = "Numbers that pertain to this contact. Order in this \
            list determines priority",
        allowedType    = "List<ContactNumber>",
        useForCreation = false)
    List numbers

    @RestApiObjectFields(params=[
        @RestApiObjectField(
            apiFieldName   = "lastRecordActivity",
            description    = "Date and time of the most recent communication with this contact",
            allowedType    = "DateTime",
            useForCreation = false),
        @RestApiObjectField(
            apiFieldName      = "doShareActions",
            description       = "List of some share or unshare actions",
            allowedType       = "List<[shareAction]>",
            useForCreation    = false,
            presentInResponse = false),
        @RestApiObjectField(
            apiFieldName      = "doNumberActions",
            description       = "List of some share or unshare actions",
            allowedType       = "List<[numberAction]>",
            useForCreation    = true,
            presentInResponse = false),
        @RestApiObjectField(
            apiFieldName   = "sharedWith",
            description    = "A list of other staff you'ved shared this contact with.",
            allowedType    = "List<SharedContact>",
            useForCreation = false),
        @RestApiObjectField(
            apiFieldName   = "sharedBy",
            description    = "Name of the staff member who shared this contact with you",
            allowedType    = "String",
            useForCreation = false),
        @RestApiObjectField(
            apiFieldName   = "startedSharing",
            description    = "Date and time this contact was shared with you",
            allowedType    = "DateTime",
            useForCreation = false),
        @RestApiObjectField(
            apiFieldName   = "permissions",
            description    = "Level of permissions you have with this contact. \
                Allowed: delegate, view",
            allowedType    = "String",
            useForCreation = false),
        @RestApiObjectField(
            apiFieldName   = "subscribed",
            description    = "In the context of a Tag, tells whether this \
            contact is a subscriber",
            allowedType    = "Boolean",
            useForCreation = false),
        @RestApiObjectField(
            apiFieldName   = "tags",
            description    = "List of tags this contact belongs to, if any. \
                Note that this will be empty for a shared contact.",
            allowedType    = "List<Tag>",
            useForCreation = false)
    ])
    static transients = []
    static constraints = {
    	name blank:true, nullable:true
    	note blank:true, nullable:true, size:1..1000
    }
    static hasMany = [numbers:ContactNumber]
    static mapping = {
        numbers lazy:false, cascade:"all-delete-orphan", sort:"preference", order:"asc"
    }
    static namedQueries = {
        forPhoneAndStatuses { Phone thisPhone, Collection<ContactStatus> statuses ->
            or {
                eq("phone", thisPhone)
                def shareds = SharedContact.sharedWithMe(thisPhone).list {
                    projections { property('contact.id') }
                }
                if (shareds) { "in"("id", shareds) }
                else { eq("id", null) }
            }
            if (statuses) { "in"("status", statuses) }
            else { "in"("status", [ContactStatus.ACTIVE, ContactStatus.UNREAD]) }
            order("status", "desc") //unread first then active
            order("lastRecordActivity", "desc") //more recent first
            order("id", "desc") //by contact id
        }
        notBlockedForPhoneAndNum { Phone thisPhone, PhoneNumber num ->
            eq("phone", thisPhone)
            numbers { eq("number", num?.number) }
            not { eq("status", ContactStatus.BLOCKED) }
        }
        forRecord { Record thisRecord ->
            eq("record", thisRecord)
        }
    }

    // Events
    // ------

    def beforeValidate() {
        if (!this.record) {
            this.record = new Record([:])
            this.record.save()
        }
    }

    // Numbers
    // -------

    Result<PhoneNumber> mergeNumber(String num, Map params=[:]) {
        PhoneNumber temp = new PhoneNumber(number:num)
        ContactNumber thisNum = ContactNumber.findByOwnerIdAndNumber(this.id, temp.number)
        temp.discard()
        if (thisNum) {
            thisNum.properties = params
            thisNum.number = num
            if (thisNum.save()) { resultFactory.success(thisNum) }
            else { resultFactory.failWithValidationErrors(thisNum.errors) }
        }
        else {
            thisNum = new ContactNumber()
            thisNum.properties = params
            thisNum.number = num
            this.addToNumbers(thisNum)
            if (thisNum.save()) { resultFactory.success(thisNum) }
            else { resultFactory.failWithValidationErrors(thisNum.errors) }
        }
    }
    Result deleteNumber(String num) {
        PhoneNumber temp = new PhoneNumber(number:num)
        ContactNumber number = ContactNumber.findByContactAndNumber(this, temp.number)
        temp.discard()
        if (number) {
            this.removeFromNumbers(number)
            number.delete()
            resultFactory.success()
        }
        else { resultFactory.failWithMessage("contact.numberNotFound", [number]) }
    }

    // Additional Contactable methods
    // ------------------------------

    Long getContactId() {
        this.id
    }
    DateTime getLastRecordActivity() {
        this.record.lastRecordActivity
    }
    List<RecordItem> getItems(Map params=[:]) {
        this.record.getItems(params)
    }
    int countItems() {
        this.record.countItems()
    }
    List<RecordItem> getSince(DateTime since, Map params=[:]) {
        this.record.getSince(since, params)
    }
    int countSince(DateTime since) {
        this.record.countSince(since)
    }
    List<RecordItem> getBetween(DateTime start, DateTime end, Map params=[:]) {
        this.record.getBetween(start, end, params)
    }
    int countBetween(DateTime start, DateTime end) {
        this.record.countBetween(start, end)
    }

    // Property Access
    // ---------------

    void setRecord(Record r) {
        this.record = r
        this.record?.save()
    }
    String getNameOrNumber(boolean formatNumber=false) {
        String num = this.numbers[0]?.number
        this.name ?: (formatNumber ? Helpers.formatNumberForSay(num) : num)
    }
    List<ContactTag> getTags(Map params=[:]) {
        ContactTag.forContact(this).list(params)
    }
    List<SharedContact> getSharedContacts() {
        SharedContact.forContact(c1).activeAndSort.list()
    }

    // Outgoing
    // --------

    Result<RecordText> storeOutgoingText(String message, RecordItemReceipt receipt, Staff staff) {
        Author author = new Author(id:staff.id, type:AuthorType.STAFF, name: staff.name)
        record.addText([outgoing:true, contents:message], author).then({ RecordText rText ->
            rText.addToReceipts(receipt)
            if (rText.save()) {
                resultFactory.success(rText)
            }
            else { resultFactory.failWithValidationErrors(rText.errors) }
        })
    }
    Result<RecordCall> storeOutgoingCall(RecordItemReceipt receipt, Staff staff) {
        Author author = new Author(id:staff.id, type:AuthorType.STAFF, name: staff.name)
        record.addCall([outgoing:true], author).then({ RecordCall rCall ->
            rCall.addToReceipts(receipt)
            if (rCall.save()) {
                resultFactory.success(rCall)
            }
            else { resultFactory.failWithValidationErrors(rCall.errors) }
        })
    }

    // Incoming
    // --------

    Result<RecordText> storeIncomingText(IncomingText text, IncomingSession session) {
        Author author = new Author(id:session.id, type:AuthorType.SESSION,
            name: session.numberAsString)
        record.addText([outgoing:false, contents:text.message], author).then({ RecordText rText ->
            RecordItemReceipt receipt = new RecordItemReceipt(apiId:text.apiId)
            receipt.receivedBy = this.phone.number
            rText.addToReceipts(receipt)
            if (rText.save()) {
                resultFactory.success(rText)
            }
            else { resultFactory.failWithValidationErrors(rText.errors) }
        })
    }
    Result<RecordCall> storeIncomingCall(String apiId, IncomingSession session) {
        Author author = new Author(id:session.id, type:AuthorType.SESSION,
            name: session.numberAsString)
        record.addCall([outgoing:false], author).then({ RecordCall rCall ->
            RecordItemReceipt receipt = new RecordItemReceipt(apiId:apiId)
            receipt.receivedBy = this.phone.number
            rCall.addToReceipts(receipt)
            if (rCall.save()) {
                resultFactory.success(rCall)
            }
            else { resultFactory.failWithValidationErrors(rCall.errors) }
        })
    }
}
