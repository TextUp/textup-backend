package org.textup

import groovy.transform.EqualsAndHashCode
import org.jadira.usertype.dateandtime.joda.PersistentDateTime
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.restapidoc.annotation.*

@EqualsAndHashCode
@RestApiObject(
    name="SharedContact",
    description="Information on how you've shared a contact with another staff member")
class SharedContact implements Contactable {

    def authService
    def resultFactory

    Phone sharedBy
    // Should not access contact object directly
    Contact contact
    // SharedContact still active if dateExpired is null or in the future
    DateTime dateExpired
	DateTime dateCreated = DateTime.now(DateTimeZone.UTC)

    @RestApiObjectField(
        description = "Level of permissions you shared this contact with. \
            Allowed: DELEGATE, VIEW",
        allowedType = "String")
    SharePermission permission

    @RestApiObjectField(
        description    = "Name of the phone that contact is shared with",
        allowedType    = "String",
        useForCreation = false)
    Phone sharedWith

    @RestApiObjectFields(params=[
        @RestApiObjectField(
            apiFieldName   = "sharedWithId",
            description    = "Id of the phone that contact is shared with",
            allowedType    = "Number",
            useForCreation = true),
        @RestApiObjectField(
            apiFieldName   = "startedSharing",
            description    = "When you started sharing this contact",
            allowedType    = "DateTime",
            useForCreation = false)
    ])
    static transients = []
    static constraints = {
    	dateExpired nullable:true
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
        forContact { Contact c1 ->
            eq('contact', c1)
            eq('sharedBy', c1.phone)
        }
        forContactAndSharedWith { Contact c1, Phone sWith ->
            eq('sharedBy', c1.phone)
            eq('sharedWith', sWith)
        }
        forSharedByAndSharedWith { Phone sBy, Phone sWith ->
            eq('sharedBy', sBy)
            eq('sharedWith', sWith)
        }
        sharedWithMe { Phone sWith ->
            eq('sharedWith', sWith)
            activeAndSort()
        }
        sharedByMe { Phone sBy ->
            projections { distinct("contact") }
            eq('sharedBy', sBy)
            activeAndSort()
        }
        activeAndSort {
            or {
                isNull("dateExpired") //not expired if null
                le("dateExpired", DateTime.now())
            }
            createAlias("contact", "contactShared")
            order("contactShared.status", "desc") //unread first then active
            order("contactShared.lastRecordActivity", "desc") //more recent first
            order("contactShared.id", "desc") //by contact id
            "in"("contactShared.status", [ContactStatus.ACTIVE, ContactStatus.UNREAD])
        }
    }

    // Sharing
    // -------

    SharedContact startSharing(SharePermission perm) {
        this.permissions = perm
        this.dateExpired = null
        this
    }
    SharedContact stopSharing() {
        this.dateExpired = DateTime.now(DateTimeZone.UTC)
        this
    }

    // Status
    // ------

    boolean getIsActive() {
        this.canModify || this.canView
    }
    //Can modify if not yet expired, and with delegate permissions
    boolean getCanModify() {
        this.dateExpired == null && this.permission == SharePermission.DELEGATE
    }
    //Can view if not yet expired and with delegate or view persmissions
    boolean getCanView() {
        this.canModify || (this.dateExpired == null &&
            this.permission == SharePermission.VIEW)
    }

    // Property access
    // ---------------

    static SharedContact findByContactId(Long cId) {
        SharedContact.createCriteria().get {
            contact { idEq(cId) }
        }
    }
    static List<SharedContact> findByContactIds(Collection<Long> cIds) {
        SharedContact.createCriteria().list {
            contact {
                if (cIds) { "in"("id", cIds) }
                else { eq("id", null) }
            }
        }
    }

    // Contactable methods
    // -------------------

    Long getContactId() {
        this.canView ? this.contact.contactId : null
    }
    DateTime getLastRecordActivity() {
        this.canView ? this.contact.lastRecordActivity : null
    }
    String getName() {
        this.canView ? this.contact.name : null
    }
    String getNote() {
        this.canView ? this.contact.note : null
    }
    ContactStatus getStatus() {
        this.canView ? this.contact.status : null
    }
    List<ContactNumber> getNumbers() {
        this.canView ? this.contact.numbers : null
    }
    List<RecordItem> getItems(Map params=[:]) {
        this.canView ? this.contact.getItems(params) : null
    }
    int countItems() {
        this.canView ? this.contact.countItems() : 0
    }
    List<RecordItem> getSince(DateTime since, Map params=[:]) {
        this.canView ? this.contact.getSince(since, params) : null
    }
    int countSince(DateTime since) {
        this.canView ? this.contact.countSince(since) : 0
    }
    List<RecordItem> getBetween(DateTime start, DateTime end, Map params=[:]) {
        this.canView ? this.contact.getBetween(start, end, params) : null
    }
    int countBetween(DateTime start, DateTime end) {
        this.canView ? this.contact.countBetween(start, end) : 0
    }
}
