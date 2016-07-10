package org.textup

import grails.compiler.GrailsCompileStatic
import groovy.transform.EqualsAndHashCode
import org.jadira.usertype.dateandtime.joda.PersistentDateTime
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.restapidoc.annotation.*
import org.textup.types.ContactStatus
import org.textup.types.SharePermission
import org.textup.validator.TempRecordReceipt
import static org.springframework.http.HttpStatus.*

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
    static transients = ["resultFactory"]
    static constraints = {
    	dateExpired nullable:true
        contact validator:{ Contact val, SharedContact obj ->
            if (val.phone != obj.sharedBy) { ["contactOwnership", val.name] }
        }
        sharedBy validator:{ Phone sBy, SharedContact obj ->
            if (sBy == obj.sharedWith) { ["shareWithMyself"] }
        }
    }
    static mapping = {
        whenCreated type:PersistentDateTime
        dateExpired type:PersistentDateTime
    }
    static namedQueries = {
        forContact { Contact c1 ->
            sharedBy { isNotNull("numberAsString") }
            eq('contact', c1)
            eq('sharedBy', c1.phone)
        }
        forContactAndSharedWith { Contact c1, Phone sWith ->
            sharedBy { isNotNull("numberAsString") }
            eq('contact', c1)
            eq('sharedWith', sWith)
        }
        forSharedByAndSharedWith { Phone sBy, Phone sWith ->
            sharedBy { isNotNull("numberAsString") }
            eq('sharedBy', sBy)
            eq('sharedWith', sWith)
        }
        sharedWithMe { Phone sWith ->
            sharedBy { isNotNull("numberAsString") }
            eq('sharedWith', sWith)
            or {
                isNull("dateExpired") //not expired if null
                ge("dateExpired", DateTime.now())
            }
            contact {
                "in"("status", [ContactStatus.ACTIVE, ContactStatus.UNREAD])
            }
            order("whenCreated", "desc")
        }
        sharedByMe { Phone sBy ->
            sharedBy { isNotNull("numberAsString") }
            projections { distinct("contact") }
            eq('sharedBy', sBy)
            or {
                isNull("dateExpired") //not expired if null
                ge("dateExpired", DateTime.now())
            }
            contact {
                "in"("status", [ContactStatus.ACTIVE, ContactStatus.UNREAD])
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
    static SharedContact findByContactIdAndSharedWith(Long cId, Phone sWith) {
        findByContactIdsAndSharedWith([cId], sWith)[0]
    }
    static List<SharedContact> findByContactIdsAndSharedWith(Collection<Long> cIds,
        Phone sWith) {
        SharedContact.createCriteria().list {
            sharedBy { isNotNull("numberAsString") }
            eq("sharedWith", sWith)
            or {
                isNull("dateExpired") //not expired if null
                ge("dateExpired", DateTime.now())
            }
            contact {
                "in"("status", [ContactStatus.ACTIVE, ContactStatus.UNREAD])
                if (cIds) { "in"("id", cIds) }
                else { eq("id", null) }
            }
            order("whenCreated", "desc")
        }
    }
    static SharedContact findByContactIdAndSharedBy(Long cId, Phone sBy) {
        findByContactIdsAndSharedBy([cId], sBy)[0]
    }
    static List<SharedContact> findByContactIdsAndSharedBy(Collection<Long> cIds,
        Phone sBy) {
        SharedContact.createCriteria().list {
            sharedBy { isNotNull("numberAsString") }
            eq("sharedBy", sBy)
            or {
                isNull("dateExpired") //not expired if null
                ge("dateExpired", DateTime.now())
            }
            contact {
                if (cIds) { "in"("id", cIds) }
                else { eq("id", null) }
            }
            order("whenCreated", "desc")
        }
    }

    // Sharing
    // -------

    @GrailsCompileStatic
    SharedContact startSharing(SharePermission perm) {
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
        this.dateExpired == null && this.permission == SharePermission.DELEGATE
    }
    //Can view if not yet expired and with delegate or view persmissions
    @GrailsCompileStatic
    boolean getCanView() {
        this.canModify || (this.dateExpired == null &&
            this.permission == SharePermission.VIEW)
    }

    // Contactable methods
    // -------------------

    @GrailsCompileStatic
    Long getContactId() {
        this.canView ? this.contact.contactId : null
    }
    @GrailsCompileStatic
    DateTime getLastRecordActivity() {
        this.canView ? this.contact.lastRecordActivity : null
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
    ContactStatus getStatus() {
        this.canView ? this.contact.status : null
    }
    @GrailsCompileStatic
    List<ContactNumber> getNumbers() {
        this.canView ? this.contact.numbers : null
    }
    @GrailsCompileStatic
    List<RecordItem> getItems(Map params=[:]) {
        this.canView ? this.contact.getItems(params) : null
    }
    @GrailsCompileStatic
    int countItems() {
        this.canView ? this.contact.countItems() : 0
    }
    @GrailsCompileStatic
    List<RecordItem> getSince(DateTime since, Map params=[:]) {
        this.canView ? this.contact.getSince(since, params) : null
    }
    @GrailsCompileStatic
    int countSince(DateTime since) {
        this.canView ? this.contact.countSince(since) : 0
    }
    @GrailsCompileStatic
    List<RecordItem> getBetween(DateTime start, DateTime end, Map params=[:]) {
        this.canView ? this.contact.getBetween(start, end, params) : null
    }
    @GrailsCompileStatic
    int countBetween(DateTime start, DateTime end) {
        this.canView ? this.contact.countBetween(start, end) : 0
    }

    @GrailsCompileStatic
    List<FutureMessage> getFutureMessages(Map params=[:]) {
        this.canView ? this.contact.getFutureMessages(params) : null
    }
    @GrailsCompileStatic
    int countFutureMessages() {
        this.canView ? this.contact.countFutureMessages() : 0
    }

    @GrailsCompileStatic
    Record getRecord() {
        this.canModify ? this.contact.record : null
    }
    @GrailsCompileStatic
    Result<RecordText> storeOutgoingText(String message, TempRecordReceipt receipt, Staff staff) {
        this.canModify ? this.contact.storeOutgoingText(message, receipt, staff) :
            resultFactory.failWithMessageAndStatus(FORBIDDEN,
                "sharedContact.insufficientPermission")
    }
    @GrailsCompileStatic
    Result<RecordCall> storeOutgoingCall(TempRecordReceipt receipt, Staff staff) {
        this.canModify ? this.contact.storeOutgoingCall(receipt, staff) :
            resultFactory.failWithMessageAndStatus(FORBIDDEN,
                "sharedContact.insufficientPermission")
    }
}
