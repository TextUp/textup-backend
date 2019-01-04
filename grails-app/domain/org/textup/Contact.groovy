package org.textup

import grails.compiler.GrailsTypeChecked
import groovy.transform.EqualsAndHashCode
import groovy.transform.TypeCheckingMode
import org.jadira.usertype.dateandtime.joda.PersistentDateTime
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.textup.rest.NotificationStatus
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*

@EqualsAndHashCode
@GrailsTypeChecked
class Contact implements Contactable, WithId {

    String note
    boolean isDeleted = false
    ContactStatus status = ContactStatus.ACTIVE
    DateTime lastTouched = DateTime.now()
    DateTime whenCreated = DateTime.now(DateTimeZone.UTC)
    List<ContactNumber> numbers
    PhoneRecord context
    String name

    static hasMany = [numbers: ContactNumber]
    static mapping = {
        whenCreated type: PersistentDateTime
        lastTouched type: PersistentDateTime
        numbers lazy: false, cascade: "all-delete-orphan"
        context fetch: "join", cascade: "save-update"
    }
    static constraints = {
        name blank: true, nullable: true
        note blank: true, nullable: true, size: 1..1000
        context cascadeValidation: true
    }
    static namedQueries = {
        forPhoneAndStatuses { Phone thisPhone,
            Collection<ContactStatus> statuses = ContactStatus.ACTIVE_STATUSES ->
            // get both my contacts and contacts that have SharedContact with me
            or {
                // if my contact, check status directly on this contact
                and {
                    eq("phone", thisPhone)
                    CriteriaUtils.inList(delegate, "status", statuses)
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
                        CriteriaUtils.inList(delegate, "id", shareds*.contact*.id)
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
                    CriteriaUtils.inList(delegate, "id", shareds*.contact*.id)
                }
            }
            // search results should include all contacts EXCEPT blocked contacts
            CriteriaUtils.inList(delegate, "status", ContactStatus.VISIBLE_STATUSES)
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
                    numbers { ilike('number', StringUtils.toQuery(tempNum.number)) }
                }
            }
        }
    }

    // Static methods
    // --------------

    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    static DetachedCriteria<Contact> forPhoneIdWithOptions(Long phoneId, String query = null,
        Collection<ContactStatus> statuses = ContactStatus.VISIBLE_STATUSES) {

        new DetachedCriteria(Contact)
            .build { eq("context.phone.id", phoneId) }
            .build(Contact.buildForOptionalQuery(query))
            .build(Contact.buildForOptionalStatuses(statuses))
    }





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





    // TODO can't type check because use association in criteria?
    static Result<Map<PhoneNumber, List<Contact>>> findEveryByNumbers(Phone p1,
        List<? extends BasePhoneNumber> bNums, boolean createIfAbsent) {

        // step 1: find all contact numbers that match the ones passed ine
        List<ContactNumber> cNums = ContactNumber.createCriteria().list {
            eq("owner.context.phone", p1)
            eq("owner.isDeleted", false)
            ne("owner.status", ContactStatus.BLOCKED)
            CriteriaUtils.inList(delegate, "number", bNums)
        } as List<ContactNumber>
        // step 2: group contacts by the passed-in phone numbers
        Map<PhoneNumber, List<Contact>> numberToContacts = [:].withDefault { [] as List<Contact> }
        cNums.each { ContactNumber cNum -> numberToContacts[cNum] << cNum.owner }
        // step 3: if allowed, create new contacts for any phone numbers without any contacts
        if (createIfAbsent) {
            Contact.createContactIfNone(p1, numberToContacts)
        }
        else { IOCUtils.resultFactory.success(numberToContacts) }
    }

    protected static Result<Map<PhoneNumber, List<Contact>>> createContactIfNone(Phone p1,
        Map<PhoneNumber, List<Contact>> numberToContacts) {

        ResultGroup<Contact> resGroup = new ResultGroup<>()
        numberToContacts.each { PhoneNumber pNum, List<Contact> contacts ->
            if (contacts.isEmpty()) {
                resGroup << Contact.create(p1, [pNum]).then { Contact c1 ->
                    contacts << c1
                    IOCUtils.resultFactory.success(c1)
                }
            }
        }
        if (resGroup.anyFailures) {
            IOCUtils.failWithGroup(resGroup)
        }
        else { IOCUtils.resultFactory.success(numberToContacts) }
    }


    static Result<Contact> create(Phone p1, List<? extends BasePhoneNumber> bNums = []) {
        Contact c1 = new Contact()
        c1.context = new PhoneRecord(phone: p1)
        // need to save contact before adding numbers so that the contact domain is assigned an
        // ID to be associated with the ContactNumbers to avoid a TransientObjectException
        if (c1.save()) {
            ResultGroup<ContactNumber> resGroup = new ResultGroup<>()
            bNums.unique().eachWithIndex { BasePhoneNumber bNum, int preference ->
                resGroup << c1.mergeNumber(bNum, preference)
            }
            if (resGroup.anyFailures) {
                IOCUtils.resultFactory.failWithGroup(resGroup)
            }
            else { IOCUtils.resultFactory.success(c1, ResultStatus.CREATED) }
        }
        else { IOCUtils.resultFactory.failWithValidationErrors(c1.errors) }
    }

    // Events
    // ------

    @GrailsTypeChecked
    def beforeValidate() {
        tryReconcileNumberPreferences()
    }

    // Numbers
    // -------

    @GrailsTypeChecked
    Result<ContactNumber> mergeNumber(BasePhoneNumber bNum, int preference) {
        ContactNumber cNum = this.numbers?.find { it.number == bNum?.number }
        if (!cNum) {
            cNum = new ContactNumber()
            cNum.update(bNum)
            addToNumbers(cNum)
        }
        cNum.preference = preference
        if (cNum.save()) {
            IOCUtils.resultFactory.success(cNum)
        }
        else { IOCUtils.resultFactory.failWithValidationErrors(cNum.errors) }
    }

    @GrailsTypeChecked
    Result<Void> deleteNumber(BasePhoneNumber bNum) {
        ContactNumber cNum = this.numbers?.find { it.number == bNum?.number }
        if (cNum) {
            removeFromNumbers(cNum)
            cNum.delete()
            IOCUtils.resultFactory.success()
        }
        else {
            IOCUtils.resultFactory.failWithCodeAndStatus("contact.numberNotFound",
                ResultStatus.NOT_FOUND, [bNum?.prettyPhoneNumber])
        }
    }

    @GrailsTypeChecked
    protected void tryReconcileNumberPreferences() {
        // autoincrement numbers' preference for new numbers if blank
        Collection<ContactNumber> initialNums = this.numbers?.findAll { !it.id && !it.preference }
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

    // Methods
    // -------

    Result<Record> tryGetRecord() {
        IOCUtils.resultFactory.success(contex.record)
    }

    Result<ReadOnlyRecord> tryGetReadOnlyRecord() {
        IOCUtils.resultFactory.success(contex.record)
    }

    // Properties
    // ----------

    String getNameOrNumber() {
        if (name) {
            name
        }
        else { numbers ? (numbers[0] as ContactNumber)?.number : "" }
    }

    // false means NOT mutate
    List<ContactNumber> getSortedNumbers() { numbers?.sort(false) ?: [] }

    Long getContactId() { id }

    PhoneNumber getFromNum() { context.phone.number }

    String getCustomAccountId() { context.phone.customAccountId }

    ReadOnlyRecord getReadOnlyRecord() { contex.record }
}
