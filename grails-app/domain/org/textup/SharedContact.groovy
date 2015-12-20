package org.textup

import groovy.transform.EqualsAndHashCode
import org.jadira.usertype.dateandtime.joda.PersistentDateTime
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.restapidoc.annotation.*

@EqualsAndHashCode
@RestApiObject(name="SharedContact", description="Information on how you've shared a contact with another staff member")
class SharedContact implements Contactable {

    def resultFactory

    @RestApiObjectField(
        description    = "When you started sharing this contact",
        allowedType    = "DateTime",
        useForCreation = false)
	DateTime dateCreated = DateTime.now(DateTimeZone.UTC)
	DateTime dateExpired //dateExpired = null -> SharedContact still active
    @RestApiObjectField(description="Level of permissions you shared this contact with. Allowed: delegate, view")
    String permission

	Contact contact
	StaffPhone sharedBy

    @RestApiObjectField(
        description    = "Name of the staff member you have shared this contact with",
        allowedType    = "String",
        useForCreation = false)
	StaffPhone sharedWith

    @RestApiObjectFields(params=[
        @RestApiObjectField(
            apiFieldName   = "sharedWithId",
            description    = "Staff member that this contact is shared with",
            allowedType    = "Number",
            useForCreation = true)
    ])
    static transients = []
    static constraints = {
    	dateExpired nullable:true
        permission blank:false, nullable:false, inList:[Constants.SHARED_DELEGATE, Constants.SHARED_VIEW]
        contact validator:{ val, obj ->
            if (val.phone != obj.sharedBy) { ["contactOwnership", val.name] }
        }
        sharedBy validator:{ sBy, obj ->
            if (sBy == obj.sharedWith) { ["shareWithMyself"] }
        }
    }
    static mapping = {
        autoTimestamp false
        dateCreated type:PersistentDateTime
        dateExpired type:PersistentDateTime
    }
    static namedQueries = {
        notExpired {
            isNull("dateExpired") //not expired if null
            createAlias("contact", "c1")
            order("c1.status", "desc") //unread first then active
            order("c1.lastRecordActivity", "desc") //more recent first
            order("c1.id", "desc") //by contact id
            "in"("c1.status", [Constants.CONTACT_ACTIVE, Constants.CONTACT_UNREAD])
        }
        sharedContactsForSameTeamAsSharedWith { StaffPhone sWith ->
            eq("sharedWith.id", sWith.id)
            sharedBy {
                def res = TeamMembership.staffIdsOnSameTeamAs(sWith.ownerId).list()
                if (res) { "in"("ownerId", res) }
            }
        }
        sharedContactsForSameTeamAsSharedBy { StaffPhone sBy ->
            eq("sharedBy.id", sBy.id)
            sharedWith {
                def res = TeamMembership.staffIdsOnSameTeamAs(sBy.ownerId).list()
                if (res) { "in"("ownerId", res) }
            }
        }
        sharedContactsForSameTeam { StaffPhone sBy, StaffPhone sWith ->
            sharedBy {
                def res = TeamMembership.staffIdsOnSameTeamAs(sWith.ownerId).list()
                if (res) { "in"("ownerId", res) }
            }
            sharedWith {
                def res = TeamMembership.staffIdsOnSameTeamAs(sBy.ownerId).list()
                if (res) { "in"("ownerId", res) }
            }
        }

        sharedWithMe { StaffPhone sWith ->
            eq("sharedWith", sWith)
            notExpired()
            sharedContactsForSameTeamAsSharedWith(sWith)
        }
        sharedWithMeIds { StaffPhone sWith ->
            projections { property("id") }
            sharedWithMe(sWith)
        }
        sharedWithForContactIds { StaffPhone sWith, Collection<Long> contactIds ->
            sharedWithMe(sWith)
            if (contactIds) { "in"("c1.id", contactIds) } //alias from notExpired()
        }
        sharedWithMeContactIds { StaffPhone sWith ->
            projections { property("contact.id") }
            eq("sharedWith", sWith)
            notExpired()
            sharedContactsForSameTeamAsSharedWith(sWith)
        }
        anyTeamSharedByMeIds { StaffPhone sBy ->
            projections { property("id") }
            eq("sharedBy", sBy)
            notExpired()
        }
        sharedByMe { StaffPhone sBy ->
            eq("sharedBy", sBy)
            notExpired()
            sharedContactsForSameTeamAsSharedBy(sBy)
        }
        sharedByMeIds { StaffPhone sBy ->
            projections { property("id") }
            sharedByMe(sBy)
        }
        allNonexpiredFor { Contact contact, StaffPhone sBy ->
            sharedByMe(sBy)
            eq("contact", contact)
        }
        allNonexpiredBetween { StaffPhone sBy, StaffPhone sWith ->
            eq("sharedBy", sBy)
            eq("sharedWith", sWith)
            notExpired()
        }
        nonexpiredFor { Contact contact, StaffPhone sBy, StaffPhone sWith ->
            eq("contact", contact)
            eq("sharedBy", sBy)
            eq("sharedWith", sWith)
            notExpired()
            sharedContactsForSameTeam(sBy, sWith)
        }
        nonexpiredForContact { Contact thisContact ->
            nonexpiredForContactId(thisContact?.id)
        }
        nonexpiredForContactId { Long thisContactId ->
            eq("c1.id", thisContactId)
            notExpired()
        }
        contactIdsForSharedWithId { Long sWithId ->
            sharedWith { eq("id", sWithId) }
            isNull("dateExpired") //not expired if null
            projections { property("contact.id") }
        }
    }

    /*
	Has many:
	*/

    ////////////////////
    // Helper methods //
    ////////////////////

    /*
    Permissions
     */
    void stopSharing() { this.dateExpired = DateTime.now(DateTimeZone.UTC) }

    //Can modify if not yet expired, and with delegate permissions
    private boolean canModify() {
        isSameTeam() && this.dateExpired == null && this.permission == Constants.SHARED_DELEGATE
    }
    //Can modify if not yet expired and with either delegate or
    //view persmissions
    private boolean canView() {
        isSameTeam() && (canModify() ||
            (this.dateExpired == null && this.permission == Constants.SHARED_VIEW))
    }
    //check that sharedBy and sharedWith Staff are still on the same Team
    private boolean isSameTeam() {
        SharedContact.sharedContactsForSameTeam(sharedBy, sharedWith).count() > 0
    }

    /*
    Activity
     */

    DateTime getLastRecordActivity() {
        canView() ? this.contact.lastRecordActivity : null
    }
    void updateLastRecordActivity() {
        if (canModify()) this.contact.updateLastRecordActivity()
    }

    /*
    Related to the client's record
     */

    Result<RecordResult> call(Staff staffMakingCall, Map params) {
        if (canModify()) { contact.call(staffMakingCall, params, this.author) }
        else { resultFactory.failWithMessage("sharedContact.error.denied", [contact.name]) }
    }
    Result<RecordResult> call(Staff staffMakingCall, Map params, Author author) {
        call(staffMakingCall, params)
    }

    Result<RecordResult> text(Map params) {
        if (canModify()) { contact.text(params, this.author) }
        else { resultFactory.failWithMessage("sharedContact.error.denied", [contact.name]) }
    }
    Result<RecordResult> text(Map params, Author author) {
        text(params)
    }

    Result<RecordResult> addNote(Map params) {
        if (canModify()) { contact.addNote(params, this.author) }
        else { resultFactory.failWithMessage("sharedContact.error.denied", [contact.name]) }
    }
    Result<RecordResult> addNote(Map params, Author author) {
        addNote(params)
    }

    Result<RecordResult> editNote(long noteId, Map params) {
        if (canModify()) { contact.editNote(noteId, params, this.author) }
        else { resultFactory.failWithMessage("sharedContact.error.denied", [contact.name]) }
    }
    Result<RecordResult> editNote(long noteId, Map params, Author author) {
        editNote(noteId, params)
    }

    Author getAuthor() {
        Staff s = Staff.get(sharedWith.ownerId)
        if (s) { new Author(name:s.name, id:sharedWith.ownerId) }
        else { new Author(id:sharedWith.ownerId) }
    }

    /*
    Client's phone numbers
     */
    Result<PhoneNumber> mergeNumber(String number, Map params) {
        if (canModify()) { contact.mergeNumber(number, params) }
        else { resultFactory.failWithMessage("sharedContact.error.denied", [contact.name]) }
    }
    Result deleteNumber(String number) {
        if (canModify()) { contact.deleteNumber(number) }
        else { resultFactory.failWithMessage("sharedContact.error.denied", [contact.name]) }
    }

    /////////////////////
    // Property Access //
    /////////////////////

    Long getContactId() {
        this.contact?.id
    }

    List<PhoneNumber> getNumbers() {
        canView() ? contact.numbers : []
    }

    /*
    Retrieving items for the client's record
     */
    List<RecordItem> getItems(Map params=[:]) {
        canView() ? contact.getItems(params) : []
    }
    List<RecordItem> getSince(DateTime since, Map params=[:]) {
        canView() ? contact.getSince(since, params) : []
    }
    List<RecordItem> getBetween(DateTime start, DateTime end, Map params=[:]) {
        canView() ? contact.getBetween(start, end, params) : []
    }
    int countItems() {
        canView() ? contact.countItems() : -1
    }
    int countSince(DateTime since) {
        canView() ? contact.countSince(since) : -1
    }
    int countBetween(DateTime start, DateTime end) {
        canView() ? contact.countBetween(start, end) : -1
    }
}
