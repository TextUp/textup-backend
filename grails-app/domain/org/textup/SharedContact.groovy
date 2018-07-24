package org.textup

import grails.compiler.GrailsCompileStatic
import grails.gorm.DetachedCriteria
import groovy.transform.EqualsAndHashCode
import org.jadira.usertype.dateandtime.joda.PersistentDateTime
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.restapidoc.annotation.*
import org.textup.rest.NotificationStatus
import org.textup.type.ContactStatus
import org.textup.type.SharePermission
import org.textup.type.VoiceLanguage
import org.textup.validator.PhoneNumber
import org.textup.validator.TempRecordReceipt

@EqualsAndHashCode
@RestApiObject(
    name="SharedContact",
    description="Information on how you've shared a contact with another staff member")
class SharedContact implements Contactable {

    ResultFactory resultFactory

    Phone sharedBy
    // Should not access contact object directly
    Contact contact
    // SharedContact still active if dateExpired is null or in the future
    DateTime dateExpired
	DateTime whenCreated = DateTime.now(DateTimeZone.UTC)

    @RestApiObjectField(
        description = "Status of this shared contact. Allowed: ACTIVE, UNREAD, ARCHIVED, BLOCKED",
        defaultValue   = "the contact's status at the time of sharing",
        mandatory      = false,
        allowedType = "String")
    ContactStatus status

    DateTime lastTouched = DateTime.now()

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
    static transients = ["resultFactory", "language"]
    static constraints = {
    	dateExpired nullable:true
        contact validator:{ Contact val, SharedContact obj ->
            if (val.phone?.id != obj.sharedBy?.id) { ["contactOwnership", val.name] }
        }
        sharedBy validator:{ Phone sBy, SharedContact obj ->
            if (sBy?.id == obj.sharedWith?.id) { ["shareWithMyself"] }
        }
    }
    static mapping = {
        whenCreated type:PersistentDateTime
        dateExpired type:PersistentDateTime
        lastTouched type:PersistentDateTime
    }
    static namedQueries = {
        forContact { Contact c1 ->
            sharedBy { isNotNull("numberAsString") }
            eq('contact', c1)
            eq('sharedBy', c1.phone)
            contact {
                // must not be deleted
                eq("isDeleted", false)
            }
        }
        forContactAndSharedWith { Contact c1, Phone sWith ->
            sharedBy { isNotNull("numberAsString") }
            eq('contact', c1)
            eq('sharedWith', sWith)
            contact {
                // must not be deleted
                eq("isDeleted", false)
            }
        }
        forSharedByAndSharedWith { Phone sBy, Phone sWith ->
            sharedBy { isNotNull("numberAsString") }
            eq('sharedBy', sBy)
            eq('sharedWith', sWith)
            contact {
                // must not be deleted
                eq("isDeleted", false)
            }
        }
        sharedWithMe { Phone sWith,
            Collection<ContactStatus> sharedStatuses = [] ->
            sharedBy { isNotNull("numberAsString") }
            eq("sharedWith", sWith)
            or {
                isNull("dateExpired") //not expired if null
                gt("dateExpired", DateTime.now(DateTimeZone.UTC))
            }
            if (sharedStatuses) {
                "in"("status", sharedStatuses)
            }
            contact {
                "in"("status", Constants.CONTACT_VISIBLE_STATUSES)
                // must not be deleted
                eq("isDeleted", false)
            }
            order("whenCreated", "desc")
        }
        sharedByMe { Phone sBy ->
            sharedBy { isNotNull("numberAsString") }
            projections { distinct("contact") }
            eq("sharedBy", sBy)
            or {
                isNull("dateExpired") //not expired if null
                gt("dateExpired", DateTime.now(DateTimeZone.UTC))
            }
            contact {
                "in"("status", Constants.CONTACT_VISIBLE_STATUSES)
                // must not be deleted
                eq("isDeleted", false)
            }
        }
    }

    // Static finders
    // --------------

    static List<SharedContact> listForContact(Contact c1, Map params=[:]) {
        SharedContact.forContact(c1).list(params)
    }
    static List<SharedContact> listForContactAndSharedWith(Contact c1, Phone sWith,
        Map params=[:]) {
        SharedContact.forContactAndSharedWith(c1, sWith).list(params)
    }
    static List<SharedContact> listForSharedByAndSharedWith(Phone sBy, Phone sWith,
        Map params=[:]) {
        SharedContact.forSharedByAndSharedWith(sBy, sWith).list(params)
    }
    static int countSharedWithMe(Phone sWith) {
        SharedContact.sharedWithMe(sWith).count()
    }
    static List<SharedContact> listSharedWithMe(Phone sWith, Map params=[:]) {
        SharedContact.sharedWithMe(sWith).list(params)
    }
    static int countSharedByMe(Phone sBy) {
        SharedContact.sharedByMe(sBy).count()
    }
    static List<Contact> listSharedByMe(Phone sBy, Map params=[:]) {
        SharedContact.sharedByMe(sBy).list(params)
    }
    static List<SharedContact> findEveryByContactIdsAndSharedWith(Collection<Long> cIds,
        Phone sWith) {
        if (!cIds || cIds.isEmpty() || !sWith) {
            return []
        }
        SharedContact.createCriteria().list {
            sharedBy { isNotNull("numberAsString") }
            eq("sharedWith", sWith)
            or {
                isNull("dateExpired") //not expired if null
                gt("dateExpired", DateTime.now())
            }
            contact {
                "in"("status", [ContactStatus.ACTIVE, ContactStatus.UNREAD, ContactStatus.ARCHIVED])
                if (cIds) { "in"("id", cIds) }
                else { eq("id", null) }
                // must not be deleted
                eq("isDeleted", false)
            }
            order("whenCreated", "desc")
        }
    }
    static SharedContact findByContactIdAndSharedBy(Long cId, Phone sBy) {
        findEveryByContactIdsAndSharedBy([cId], sBy)[0]
    }
    static List<SharedContact> findEveryByContactIdsAndSharedBy(Collection<Long> cIds,
        Phone sBy) {
        if (!cIds || cIds.isEmpty() || !sBy) {
            return []
        }
        SharedContact.createCriteria().list {
            sharedBy { isNotNull("numberAsString") }
            eq("sharedBy", sBy)
            or {
                isNull("dateExpired") //not expired if null
                gt("dateExpired", DateTime.now())
            }
            contact {
                if (cIds) { "in"("id", cIds) }
                else { eq("id", null) }
                // must not be deleted
                eq("isDeleted", false)
            }
            order("whenCreated", "desc")
        }
    }
    static DetachedCriteria<SharedContact> buildForContacts(Collection<Contact> contacts) {
        // do not exclude deleted contacts here becuase this detached criteria is used for
        // bulk operations. For bulk operations, joins are not allowed, so we cannot join the
        // SharedContact table with the Contact table to screen out deleted contacts
        new DetachedCriteria(SharedContact)
            .build {
                if (contacts) { "in"("contact", contacts) }
                else { eq("contact", null) }
            }
    }

    // Events
    // ------

    // if the shared contact's status is null and we are validating, copy over the contact's status
    def beforeValidate() {
        if (!this.status) {
            this.status = this.contact?.status
        }
    }

    // Sharing
    // -------

    @GrailsCompileStatic
    SharedContact startSharing(ContactStatus cStatus1, SharePermission perm) {
        this.status = cStatus1
        this.permission = perm
        this.dateExpired = null
        this
    }
    @GrailsCompileStatic
    SharedContact stopSharing() {
        this.dateExpired = DateTime.now(DateTimeZone.UTC)
        this
    }

    // Status
    // ------

    @GrailsCompileStatic
    boolean getIsActive() {
        this.canModify || this.canView
    }
    //Can modify if not yet expired, and with delegate permissions
    @GrailsCompileStatic
    boolean getCanModify() {
        (this.dateExpired == null || this.dateExpired?.isAfterNow()) &&
            this.permission == SharePermission.DELEGATE
    }
    //Can view if not yet expired and with delegate or view persmissions
    @GrailsCompileStatic
    boolean getCanView() {
        this.canModify || ((this.dateExpired == null || this.dateExpired?.isAfterNow()) &&
            this.permission == SharePermission.VIEW)
    }

    // Contactable methods
    // -------------------

    @GrailsCompileStatic
    Long getContactId() {
        this.canView ? this.contact.contactId : null
    }
    @GrailsCompileStatic
    PhoneNumber getFromNum() {
        this.canView ? this.sharedBy.number : null
    }
    @GrailsCompileStatic
    String getName() {
        this.canView ? this.contact.name : null
    }
    @GrailsCompileStatic
    String getNote() {
        this.canView ? this.contact.note : null
    }
    @GrailsCompileStatic
    List<ContactNumber> getNumbers() {
        this.canView ? this.contact.numbers : null
    }
    @GrailsCompileStatic
    List<ContactNumber> getSortedNumbers() {
        this.canView ? this.contact.sortedNumbers : null
    }
    @GrailsCompileStatic
    List<NotificationStatus> getNotificationStatuses() {
        // If we are modifying through a shared contact in contactService, we use the contact id
        // so it's indistinguishable whether we own the contact being updated or we are a collaborator
        // Therefore, any notification policies for collaborators will be stored in the sharedBy phone
        // and when we are getting notification statuses for the sharedWith collabors, we must
        // go to the sharedBy phone to retrieve the notification policies
        if (this.canView) {
            sharedBy.owner.getNotificationStatusesForStaffsAndRecords(this.sharedWith.owner.all,
                [this.contact.record.id])
        }
        else { [] }
    }

    @GrailsCompileStatic
    Result<Record> tryGetRecord() {
        this.canModify ?
            resultFactory.success(this.contact.record) :
            resultFactory.failWithCodeAndStatus("sharedContact.insufficientPermission",
                ResultStatus.FORBIDDEN)
    }
    @GrailsCompileStatic
    Result<ReadOnlyRecord> tryGetReadOnlyRecord() {
        this.isActive ?
            resultFactory.success(this.contact.record) :
            resultFactory.failWithCodeAndStatus("sharedContact.insufficientPermission",
                ResultStatus.FORBIDDEN)
    }
}
