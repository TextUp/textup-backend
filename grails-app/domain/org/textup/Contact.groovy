package org.textup

import grails.gorm.DetachedCriteria
import groovy.transform.EqualsAndHashCode
import org.jadira.usertype.dateandtime.joda.PersistentDateTime
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.restapidoc.annotation.*

@EqualsAndHashCode
@RestApiObject(name="Contact", description="A contact")
class Contact implements Contactable {

    def textService 
    def callService
    def resultFactory

    @RestApiObjectField(
        description    = "Date and time of the most recent communication with this contact", 
        allowedType    = "DateTime",
        useForCreation = false)
    DateTime lastRecordActivity = DateTime.now(DateTimeZone.UTC)

    Phone phone //phone that owns this contact
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
        description    = "Status of this contact. Allowed: active, unread, archived, blocked", 
        defaultValue   = "active",
        mandatory      = false,
        useForCreation = true)
    String status = Constants.CONTACT_ACTIVE
    Record record 
    @RestApiObjectField(
        apiFieldName   = "numbers",
        description    = "Numbers that pertain to this contact. Order in this list determines priorit",
        allowedType    = "List<String>", 
        useForCreation = false)
    List numbers

    @RestApiObjectFields(params=[
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
            description    = "Level of permissions you have with this contact. Allowed: delegate, view",
            allowedType    = "String",
            useForCreation = false),
        @RestApiObjectField(
            apiFieldName   = "subscribed",
            description    = "In the context of a Tag, tells whether this contact is a subscriber",
            allowedType    = "Boolean",
            useForCreation = false)
    ])
    static transients = []
    static constraints = {
    	name blank:true, nullable:true
    	note blank:true, nullable:true, size:1..1000
        status blank:false, nullable:false, inList:[Constants.CONTACT_UNREAD, 
            Constants.CONTACT_ACTIVE, Constants.CONTACT_ARCHIVED, Constants.CONTACT_BLOCKED]
    }
    static mapping = {
        numbers lazy:false, cascade:"all-delete-orphan", sort:"preference", order:"asc"
        lastRecordActivity type:PersistentDateTime
    }
    static hasMany = [numbers:ContactNumber]
    static namedQueries = {
        sharedWithMe { StaffPhone staffPhone ->
            DetachedCriteria sharedWithMeIds = new DetachedCriteria(SharedContact).build {
                projections { property("contact.id") }
                SharedContact.sharedWithMe(staffPhone)
            }
            "in"("id", sharedWithMeIds.list())
        }
        sharedByMe { StaffPhone staffPhone -> 
            DetachedCriteria sharedByMeIds = new DetachedCriteria(SharedContact).build {
                projections { property("contact.id") }
                SharedContact.sharedByMe(staffPhone)
            }
            "in"("id", sharedByMeIds.list())
        }
        recordIdsForPhoneId { Long phoneId ->
            phone { eq("id", phoneId) }
            projections { property("record.id") }
        }
        teamRecordIdsForStaffId { Long thisStaffId ->
            List<Long> scIds = Helpers.allToLong(Team.teamPhoneIdsForStaffId(thisStaffId).list())
            phone { "in"("id", scIds) }
            projections { property("record.id") }
        }
        sharedRecordIdsForSharedWithId { Long sWithPhoneId ->
            List<Long> scIds = Helpers.allToLong(SharedContact.contactIdsForSharedWithId(sWithPhoneId).list())
            "in"("id", scIds)
            projections { property("record.id") }
        }
        forPhoneAndStatuses { Phone thisPhone, List<String> statuses ->
            eq("phone", thisPhone)

            if (statuses) { "in"("status", statuses) }
            else { "in"("status", [Constants.CONTACT_ACTIVE, Constants.CONTACT_UNREAD]) }

            order("status", "desc") //unread first then active
            order("lastRecordActivity", "desc") //more recent first
            order("id", "desc") //by contact id
        }
        forStaffPhoneAndStatuses { StaffPhone sp, List<String> statuses ->
            or { 
                eq("phone", sp)
                "in"("id", SharedContact.sharedWithMeContactIds(sp).list())
            }
            if (statuses) { "in"("status", statuses) }
            else { "in"("status", [Constants.CONTACT_ACTIVE, Constants.CONTACT_UNREAD]) }

            order("status", "desc") //unread first then active
            order("lastRecordActivity", "desc") //more recent first
            order("id", "desc") //by contact id
        }
        forTagAndStatuses { ContactTag ct, List<String> statuses ->
            "in"("id", TagMembership.contactIdsForTag(ct).list())

            if (statuses) { "in"("status", statuses) }
            else { "in"("status", [Constants.CONTACT_ACTIVE, Constants.CONTACT_UNREAD]) }

            order("status", "desc") //unread first then active
            order("lastRecordActivity", "desc") //more recent first
            order("id", "desc") //by contact id
        }
    }
    
    /*
	Has many:
		TagMembership
	*/

    ////////////
    // Events //
    ////////////

    def beforeDelete() {
        Contact.withNewSession {
            this.numbers?.clear()
            ContactNumber.where { contact == this }.deleteAll()
            TagMembership.where { contact == this }.deleteAll()
            SharedContact.where { contact == this }.deleteAll()
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
        Contact.withNewSession {
            Record.where { id == record.id }.deleteAll()
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
    Modify status
     */
    
    Result<Contact> activate() {
        this.status = Constants.CONTACT_ACTIVE
        resultFactory.success(this)
    }
    Result<Contact> archive() {
        this.status = Constants.CONTACT_ARCHIVED
        resultFactory.success(this)
    }
    Result<Contact> block() {
        this.status = Constants.CONTACT_BLOCKED
        resultFactory.success(this)
    }
    Result<Contact> markRead() { activate() }
    Result<Contact> markUnread() {
        this.status = Constants.CONTACT_UNREAD
        resultFactory.success(this)
    }

    void updateLastRecordActivity() {
        this.lastRecordActivity = DateTime.now(DateTimeZone.UTC)
    }

    /*
    Related to the client's record
     */
    
    Result<RecordResult> call(Map params) { 
        call(params, this.author)
    }
    Result<RecordResult> call(Map params, Author auth) { 
        Result<RecordText> tRes = record.addCall(params, auth)
        if (tRes.success) {
            tRes = callService.call(this.phone, this, tRes.payload)
            if (tRes.success) { updateLastRecordActivity() }
        }
        resultFactory.convertToRecordResult(tRes)
    }
    
    Result<RecordResult> text(Map params) { 
        text(params, this.author)
    }
    Result<RecordResult> text(Map params, Author auth) { 
        Result<RecordText> tRes = record.addText(params, auth)
        if (tRes.success) {
            tRes = textService.text(this.phone, this, tRes.payload)
            if (tRes.success) { updateLastRecordActivity() }
        }
        resultFactory.convertToRecordResult(tRes)
    }

    Result<RecordResult> addNote(Map params) { addNote(params, this.author) }
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
        def owner = this.getOwner()
        owner ? new Author(name:owner.name, id:owner.id) : null
    }
    def getOwner() {
        def owner = Staff.where { phone.id == this.phone.id }.list(max:1)[0]
        if (!owner) { owner = Team.where { phone.id == this.phone.id }.list(max:1)[0] } 
        owner
    }

    /*
    Client's phone numbers
     */
    
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
        else { resultFactory.failWithMessage("contact.error.numberNotFound", [number]) }
    }

    /*
    Tag memberships
     */
    Result<TagMembership> addToTag(String tagName) {
        ContactTag tag = ContactTag.findByPhoneAndName(this.phone, tagName)
        if (tag) { addToTag(tag) }
        else { resultFactory.failWithMessage("contact.error.tagNotFound", [tagName]) }
    }
    Result<TagMembership> addToTag(ContactTag tag) {
        TagMembership membership = new TagMembership(contact:this, tag:tag)
        if (membership.save()) { resultFactory.success(membership) }
        else { resultFactory.failWithValidationErrors(membership.errors) }
    }
    Result<ContactTag> removeFromTag(String tagName) {
        ContactTag tag = ContactTag.findByPhoneAndName(this.phone, tagName)
        if (tag) { removeFromTag(tag) }
        else { resultFactory.failWithMessage("contact.error.tagNotFound", [tagName]) }
    }
    Result<ContactTag> removeFromTag(ContactTag tag) {
        TagMembership membership = TagMembership.findByContactAndTag(this, tag)
        if (membership) {
            membership.delete() 
            resultFactory.success(tag)
        }
        else { resultFactory.failWithMessage("contact.error.membershipNotFound", [this.name, tag.name]) }
    }
    Result<TagMembership> subscribeToTag(String tagName) {
        ContactTag tag = ContactTag.findByPhoneAndName(this.phone, tagName)
        if (tag) { subscribeToTag(tag) }
        else { resultFactory.failWithMessage("contact.error.tagNotFound", [tagName]) }
    }
    Result<TagMembership> subscribeToTag(ContactTag tag) {
        TagMembership membership = TagMembership.findByContactAndTag(this, tag)
        if (membership) {
            resultFactory.success(membership.subscribe())
        }
        else { resultFactory.failWithMessage("contact.error.membershipNotFound", [this.name, tag.name]) }
    }
    Result<TagMembership> unsubscribeFromTag(String tagName) {
        ContactTag tag = ContactTag.findByPhoneAndName(this.phone, tagName)
        if (tag) { unsubscribeFromTag(tag) }
        else { resultFactory.failWithMessage("contact.error.tagNotFound", [tagName]) }
    }
    Result<TagMembership> unsubscribeFromTag(ContactTag tag) {
        TagMembership membership = TagMembership.findByContactAndTag(this, tag)
        if (membership) {
            resultFactory.success(membership.unsubscribe())
        }
        else { resultFactory.failWithMessage("contact.error.membershipNotFound", [this.name, tag.name]) }
    }

    /////////////////////
    // Property Access //
    /////////////////////
    
    void setRecord(Record r) {
        this.record = r 
        this.record?.save()
    }

    /*
    Tag members
     */

    List<TagMembership> getTags(Map params=[:]) {
        TagMembership.findAllByContact(this, params)
    }

    /*
    Items for the contact's record
     */
    
    List<RecordItem> getItems(Map params=[:]) {
        record.getItems(params)
    }
    int countItems(){
        record.countItems()
    }
    List<RecordItem> getSince(DateTime since, Map params=[:]) {
        record.getSince(since, params)
    }
    int countSince(DateTime since) {
        record.countSince(since)
    }
    List<RecordItem> getBetween(DateTime start, DateTime end, Map params=[:]) {
        record.getBetween(start, end, params)
    }
    int countBetween(DateTime start, DateTime end) {
        record.countBetween(start, end)
    }
}
