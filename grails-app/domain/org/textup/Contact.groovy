package org.textup

import grails.compiler.GrailsTypeChecked
import grails.gorm.DetachedCriteria
import groovy.transform.EqualsAndHashCode
import groovy.transform.TypeCheckingMode
import groovy.util.logging.Log4j
import org.joda.time.DateTime
import org.restapidoc.annotation.*
import org.textup.types.AuthorType
import org.textup.types.ContactStatus
import org.textup.validator.Author
import org.textup.validator.IncomingText
import org.textup.validator.PhoneNumber
import org.textup.validator.TempRecordReceipt

@Log4j
@EqualsAndHashCode(includes=["phone", "record", "id"])
@RestApiObject(name="Contact", description="A contact")
class Contact implements Contactable {

    ResultFactory resultFactory

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
    List<ContactNumber> numbers

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
    static transients = ["resultFactory"]
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
                List<SharedContact> shareds = SharedContact.sharedWithMe(thisPhone).list()
                if (shareds) { "in"("id", shareds*.contact*.id) }
                else { eq("id", null) }
            }
            if (statuses) { "in"("status", statuses) }
            else { "in"("status", [ContactStatus.ACTIVE, ContactStatus.UNREAD]) }
            createAlias("record", "r1")
            order("status", "desc") //unread first then active
            order("r1.lastRecordActivity", "desc") //more recent first
            order("id", "desc") //by contact id
        }
    }

    // Static finders
    // --------------

    static int countForPhoneAndStatuses(Phone thisPhone, Collection<ContactStatus> statuses) {
        forPhoneAndStatuses(thisPhone, statuses).count()
    }
    static List<Contact> listForPhoneAndStatuses(Phone thisPhone,
        Collection<ContactStatus> statuses, Map params=[:]) {
        forPhoneAndStatuses(thisPhone, statuses).list(params)
    }
    static List<Contact> listForPhoneAndNum(Phone thisPhone, PhoneNumber num) {
        Contact.createCriteria().list {
            eq("phone", thisPhone)
            numbers { eq("number", num?.number) }
        }
    }

    // Events
    // ------

    @GrailsTypeChecked
    def beforeValidate() {
        if (!this.record) {
            this.record = new Record([:])
        }
        handleNumberPreferences()
    }

    // Numbers
    // -------

    @GrailsTypeChecked
    Result<ContactNumber> mergeNumber(String num, Map params=[:]) {
        PhoneNumber temp = new PhoneNumber(number:num)
        ContactNumber thisNum = this.numbers?.find { it.number == temp.number }
        if (thisNum) {
            thisNum.properties = params
            thisNum.number = num
            if (thisNum.save()) {
                resultFactory.success(thisNum)
            }
            else { resultFactory.failWithValidationErrors(thisNum.errors) }
        }
        else {
            thisNum = new ContactNumber()
            thisNum.properties = params
            thisNum.number = num
            this.addToNumbers(thisNum)
            handleNumberPreferences()
            if (thisNum.save()) {
                resultFactory.success(thisNum)
            }
            else { resultFactory.failWithValidationErrors(thisNum.errors) }
        }
    }
    @GrailsTypeChecked
    protected void handleNumberPreferences() {
        // autoincrement numbers' preference for new numbers if blank'
        Collection<ContactNumber> initialNums = this.numbers.findAll {
            it.id == null && it.preference == null
        }
        if (initialNums) {
            Collection<ContactNumber> existingNums = this.numbers - initialNums
            int greatestPref = 0
            if (existingNums) {
                ContactNumber greatestPrefNum = existingNums.max { it.preference }
                greatestPref = greatestPrefNum.preference
            }
            initialNums.eachWithIndex { ContactNumber cn, int i ->
                cn.preference = greatestPref + i + 1 // zero-indexed
            }
        }
    }
    @GrailsTypeChecked
    Result deleteNumber(String num) {
        PhoneNumber temp = new PhoneNumber(number:num)
        ContactNumber number = this.numbers.find { it.number == temp.number }
        if (number) {
            this.removeFromNumbers(number)
            number.delete()
            resultFactory.success()
        }
        else { resultFactory.failWithMessage("contact.numberNotFound", [number]) }
    }

    // Additional Contactable methods
    // ------------------------------

    @GrailsTypeChecked
    Long getContactId() {
        this.id
    }
    @GrailsTypeChecked
    DateTime getLastRecordActivity() {
        this.record.lastRecordActivity
    }
    @GrailsTypeChecked
    List<RecordItem> getItems(Map params=[:]) {
        this.record.getItems(params)
    }
    @GrailsTypeChecked
    int countItems() {
        this.record.countItems()
    }
    @GrailsTypeChecked
    List<RecordItem> getSince(DateTime since, Map params=[:]) {
        this.record.getSince(since, params)
    }
    @GrailsTypeChecked
    int countSince(DateTime since) {
        this.record.countSince(since)
    }
    @GrailsTypeChecked
    List<RecordItem> getBetween(DateTime start, DateTime end, Map params=[:]) {
        this.record.getBetween(start, end, params)
    }
    @GrailsTypeChecked
    int countBetween(DateTime start, DateTime end) {
        this.record.countBetween(start, end)
    }

    // Property Access
    // ---------------

    @GrailsTypeChecked
    void setRecord(Record r) {
        this.record = r
        this.record?.save()
    }
    @GrailsTypeChecked
    String getNameOrNumber(boolean formatNumber=false) {
        String num = (this.numbers[0] as ContactNumber)?.number
        this.name ?: (formatNumber ? Helpers.formatNumberForSay(num) : num)
    }
    List<ContactTag> getTags(Map params=[:]) {
        ContactTag.createCriteria().list(params) {
            members { idEq(this.id) }
            eq("isDeleted", false)
        } as List
    }
    List<SharedContact> getSharedContacts() {
        SharedContact.createCriteria().list {
            eq('contact', this)
            eq('sharedBy', this.phone)
            or {
                isNull("dateExpired") //not expired if null
                ge("dateExpired", DateTime.now())
            }
            contact {
                "in"("status", [ContactStatus.ACTIVE, ContactStatus.UNREAD])
            }
        }
    }
    List<IncomingSession> getSessions(Map params=[:]) {
        this.numbers ? IncomingSession.findAllByPhoneAndNumberAsStringInList(this.phone,
            this.numbers*.number) : []
    }

    // Outgoing
    // --------

    @GrailsTypeChecked
    Result<RecordText> storeOutgoingText(String message, TempRecordReceipt receipt, Staff staff) {
        Author author = new Author(id:staff.id, type:AuthorType.STAFF, name: staff.name)
        record.addText([outgoing:true, contents:message], author).then({ RecordText rText ->
            rText.addReceipt(receipt)
            if (rText.save()) {
                resultFactory.success(rText)
            }
            else { resultFactory.failWithValidationErrors(rText.errors) }
        }) as Result
    }
    @GrailsTypeChecked
    Result<RecordCall> storeOutgoingCall(TempRecordReceipt receipt, Staff staff) {
        Author author = new Author(id:staff.id, type:AuthorType.STAFF, name: staff.name)
        record.addCall([outgoing:true], author).then({ RecordCall rCall ->
            rCall.addReceipt(receipt)
            if (rCall.save()) {
                resultFactory.success(rCall)
            }
            else { resultFactory.failWithValidationErrors(rCall.errors) }
        }) as Result
    }

    // Incoming
    // --------

    @GrailsTypeChecked
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
        }) as Result
    }
    @GrailsTypeChecked
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
        }) as Result
    }
}
