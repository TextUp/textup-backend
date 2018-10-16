package org.textup

import grails.compiler.GrailsTypeChecked
import groovy.transform.EqualsAndHashCode
import groovy.transform.TypeCheckingMode
import groovy.util.logging.Log4j
import org.jadira.usertype.dateandtime.joda.PersistentDateTime
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.restapidoc.annotation.*
import org.textup.rest.NotificationStatus
import org.textup.type.AuthorType
import org.textup.type.ContactStatus
import org.textup.type.VoiceLanguage
import org.textup.validator.Author
import org.textup.validator.IncomingText
import org.textup.validator.PhoneNumber
import org.textup.validator.TempRecordReceipt

@Log4j
@EqualsAndHashCode
@RestApiObject(name="Contact", description="A contact")
class Contact implements Contactable {

    DateTime whenCreated = DateTime.now(DateTimeZone.UTC)

    Phone phone //phone that owns this contact
    Record record
    boolean isDeleted = false

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

    DateTime lastTouched = DateTime.now()

    @RestApiObjectField(
        apiFieldName   = "numbers",
        description    = "Numbers that pertain to this contact. Order in this \
            list determines priority",
        allowedType    = "List<ContactNumber>",
        useForCreation = false)
    List<ContactNumber> numbers

    @RestApiObjectFields(params=[
        @RestApiObjectField(
            apiFieldName = "language",
            description  = "Language to use when speaking during calls. Allowed: \
                CHINESE, ENGLISH, FRENCH, GERMAN, ITALIAN, JAPANESE, KOREAN, PORTUGUESE, RUSSIAN, SPANISH",
            mandatory    = false,
            allowedType  = "String",
            defaultValue = "ENGLISH"),
        @RestApiObjectField(
            apiFieldName   = "lastRecordActivity",
            description    = "Date and time of the most recent communication with this contact",
            allowedType    = "DateTime",
            useForCreation = false),
        @RestApiObjectField(
            apiFieldName      = "doShareActions",
            description       = "List of actions that modify sharing permissions",
            allowedType       = "List<[shareAction]>",
            useForCreation    = false,
            presentInResponse = false),
        @RestApiObjectField(
            apiFieldName      = "doNumberActions",
            description       = "List of actions to add, remove or update contact's numbers",
            allowedType       = "List<[numberAction]>",
            useForCreation    = true,
            presentInResponse = false),
        @RestApiObjectField(
            apiFieldName      = "doNotificationActions",
            description       = "List of actions that customize notification settings for specific staff members",
            allowedType       = "List<[notificationAction]>",
            useForCreation    = false,
            presentInResponse = false),
        @RestApiObjectField(
            apiFieldName   = "notificationStatuses",
            description    = "Whether or not a specified staff member will be notified of updates for this specific contact",
            allowedType    = "List<NotificationStatus>",
            useForCreation = false),
        @RestApiObjectField(
            apiFieldName   = "sharedWith",
            description    = "A list of other staff you'ved shared this contact with. Will always be empty if contact is BLOCKED.",
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
            useForCreation = false),
        @RestApiObjectField(
            apiFieldName   = "unreadInfo",
            description    = "If this contact is unread and has specific record items that have not \
                been viewed yet, this object provides counts by record item type.",
            allowedType    = "UnreadInfo",
            useForCreation = false)
    ])
    static transients = ["language"]
    static constraints = {
    	name blank:true, nullable:true
    	note blank:true, nullable:true, size:1..1000
    }
    static hasMany = [numbers:ContactNumber]
    static mapping = {
        whenCreated type:PersistentDateTime
        lastTouched type:PersistentDateTime
        numbers lazy:false, cascade:"all-delete-orphan"
    }
    static namedQueries = {
        forPhoneAndStatuses { Phone thisPhone,
            Collection<ContactStatus> statuses = ContactStatus.ACTIVE_STATUSES ->
            // get both my contacts and contacts that have SharedContact with me
            or {
                // if my contact, check status directly on this contact
                and {
                    eq("phone", thisPhone)
                    if (statuses) {
                        "in"("status", statuses)
                    }
                }
                // if not my contact (shared with me), check the status on the shared contact
                // NOTE: by default, this finder will NOT show shared contacts for contacts
                // blocked by the original owner. See `ContactStatus.VISIBLE_STATUSES`
                Collection<SharedContact> shareds = SharedContact
                    .sharedWithMe(thisPhone, statuses)
                    .list()
                if (shareds) {
                    // critical that this `and` criteria builder clause is nested INSIDE of
                    // this `if` statement. Otherwise, an empty `and` clause inside of an
                    // `or` clause will be interpreted as permitting ALL contacts to be displayed
                    and {
                        "in"("id", shareds*.contact*.id)
                    }
                }
            }
            // must not be deleted
            eq("isDeleted", false)
            // sort appropriately
            createAlias("record", "r1")
            order("status", "desc") //unread first then active
            order("r1.lastRecordActivity", "desc") //more recent first
            order("id", "desc") //by contact id
        }
        forPhoneAndSearch { Phone thisPhone, String query ->
            // get both my contacts and contacts that have SharedContact with me
            or {
                eq("phone", thisPhone)
                List<SharedContact> shareds = SharedContact
                    .sharedWithMe(thisPhone, ContactStatus.VISIBLE_STATUSES)
                    .list()
                if (shareds) {
                    "in"("id", shareds*.contact*.id)
                }
            }
            // search results should include all contacts EXCEPT blocked contacts
            "in"("status", ContactStatus.VISIBLE_STATUSES)
            // must not be deleted
            eq("isDeleted", false)
            // conduct search in contact name and associated numbers
            PhoneNumber tempNum = new PhoneNumber(number:query) //to clean query
            or {
                ilike('name', query)
                // don't include the numbers constraint if the cleaned query
                // is not a number because the cleaning process will return
                // an empty string and transforming this to a query string will
                // yield '%' which matches all results
                if (tempNum.number) {
                    numbers { ilike('number', Helpers.toQuery(tempNum.number)) }
                }
            }
        }
    }

    // Static finders
    // --------------

    static int countForPhoneAndSearch(Phone thisPhone, String query) {
        if (!query) {
            return 0
        }
        forPhoneAndSearch(thisPhone, query) {
            projections {
                countDistinct("id")
            }
        }[0]
    }
    // return contacts, some of which are mine and other of which
    // are NOT mine have SharedContacts with me
    static List<Contact> listForPhoneAndSearch(Phone thisPhone,
        String query, Map params=[:]) {
        if (!query) {
            return []
        }
        forPhoneAndSearch(thisPhone, query).listDistinct(params)
    }

    static int countForPhoneAndStatuses(Phone thisPhone, Collection<ContactStatus> statuses = []) {
        if (statuses) {
            forPhoneAndStatuses(thisPhone, statuses).count()
        }
        else { // allow static finder default
            forPhoneAndStatuses(thisPhone).count()
        }
    }
    // return contacts, some of which are mine and other of which
    // are NOT mine have SharedContacts with me
    static List<Contact> listForPhoneAndStatuses(Phone thisPhone,
        Collection<ContactStatus> statuses = [], Map params=[:]) {
        if (statuses) {
            forPhoneAndStatuses(thisPhone, statuses).list(params)
        }
        else { // allow static finder default
            forPhoneAndStatuses(thisPhone).list(params)
        }
    }
    // purposefully also allow blocked contacts to show up here. We want ALL contacts EXCEPT deleted ones
    static List<Contact> listForPhoneAndNum(Phone thisPhone, PhoneNumber num) {
        Contact.createCriteria().list {
            eq("phone", thisPhone)
            numbers { eq("number", num?.number) }
            // must not be deleted
            eq("isDeleted", false)
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
                Helpers.resultFactory.success(thisNum)
            }
            else { Helpers.resultFactory.failWithValidationErrors(thisNum.errors) }
        }
        else {
            thisNum = new ContactNumber()
            thisNum.properties = params
            thisNum.number = num
            this.addToNumbers(thisNum)
            handleNumberPreferences()
            if (thisNum.save()) {
                Helpers.resultFactory.success(thisNum)
            }
            else { Helpers.resultFactory.failWithValidationErrors(thisNum.errors) }
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
    Result<Void> deleteNumber(String num) {
        PhoneNumber temp = new PhoneNumber(number:num)
        ContactNumber number = this.numbers?.find { it.number == temp.number }
        if (number) {
            this.removeFromNumbers(number)
            number.delete()
            Helpers.resultFactory.success()
        }
        else {
            Helpers.resultFactory.failWithCodeAndStatus("contact.numberNotFound",
                ResultStatus.NOT_FOUND, [number])
        }
    }

    // Additional Contactable methods
    // ------------------------------

    @GrailsTypeChecked
    ReadOnlyRecord getReadOnlyRecord() {
        this.record
    }
    @GrailsTypeChecked
    Long getContactId() {
        this.id
    }
    @GrailsTypeChecked
    PhoneNumber getFromNum() {
        this.phone.number
    }
    @GrailsTypeChecked
    List<NotificationStatus> getNotificationStatuses() {
        this.phone.owner.getNotificationStatusesForRecords([this.record.id])
    }
    @GrailsTypeChecked
    Result<Record> tryGetRecord() {
        Helpers.resultFactory.success(this.record)
    }
    @GrailsTypeChecked
    Result<ReadOnlyRecord> tryGetReadOnlyRecord() {
        Helpers.resultFactory.success(this.record)
    }

    // Property Access
    // ---------------

    @GrailsTypeChecked
    void setRecord(Record r) {
        this.record = r
        this.record?.save()
    }
    @GrailsTypeChecked
    String getNameOrNumber() {
        if (name) {
            name
        }
        else { numbers ? (numbers[0] as ContactNumber)?.number : "" }
    }
    List<ContactTag> getTags(Map params=[:]) {
        ContactTag.createCriteria().list(params) {
            members { idEq(this.id) }
            eq("isDeleted", false)
        } as List
    }
    List<SharedContact> getSharedContacts() {
        SharedContact.createCriteria().list {
            eq("contact", this)
            eq("sharedBy", this.phone)
            or {
                isNull("dateExpired") //not expired if null
                gt("dateExpired", DateTime.now())
            }
            contact {
                "in"("status", [ContactStatus.ACTIVE, ContactStatus.UNREAD, ContactStatus.ARCHIVED])
                // must not be deleted
                eq("isDeleted", false)
            }
        }
    }
    List<IncomingSession> getSessions(Map params=[:]) {
        this.numbers ? IncomingSession.findAllByPhoneAndNumberAsStringInList(this.phone,
            this.numbers*.number) : []
    }
    List<ContactNumber> getSortedNumbers() {
        this.numbers ? this.numbers.sort(false) { ContactNumber n1, ContactNumber n2 ->
            n1.preference <=> n2.preference
        } : []
    }
}
