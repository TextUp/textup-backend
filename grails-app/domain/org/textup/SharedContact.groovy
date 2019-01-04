package org.textup

import grails.compiler.GrailsTypeChecked
import grails.gorm.DetachedCriteria
import groovy.transform.EqualsAndHashCode
import org.jadira.usertype.dateandtime.joda.PersistentDateTime
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.textup.rest.NotificationStatus
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*

@GrailsTypeChecked
@EqualsAndHashCode
class SharedContact implements Contactable, WithId {

    Contact contact // Should not access contact object directly
    ContactStatus status
    DateTime dateExpired // active if dateExpired is null or in the future
    DateTime lastTouched = DateTime.now(DateTimeZone.UTC)
    DateTime whenCreated = DateTime.now(DateTimeZone.UTC)
    Phone sharedBy
    Phone sharedWith
    SharePermission permission

    static mapping = {
        dateExpired type: PersistentDateTime
        lastTouched type: PersistentDateTime
        whenCreated type: PersistentDateTime
    }
    static constraints = {
    	dateExpired nullable:true
        contact validator:{ Contact val, SharedContact obj ->
            if (val.phone?.id != obj.sharedBy?.id) { ["contactOwnership", val.name] }
        }
        sharedBy validator:{ Phone sBy, SharedContact obj ->
            if (sBy?.id == obj.sharedWith?.id) { ["shareWithMyself"] }
        }
    }

    // if the shared contact's status is null and we are validating, copy over the contact's status
    def beforeValidate() {
        if (!status) {
            status = contact?.status
        }
    }

    static DetachedCriteria<SharedContact> forContactsNoJoins(Collection<Contact> contacts) {
        // do not exclude deleted contacts here becuase this detached criteria is used for
        // bulk operations. For bulk operations, joins are not allowed, so we cannot join the
        // SharedContact table with the Contact table to screen out deleted contacts
        new DetachedCriteria(SharedContact)
            .build { CriteriaUtils.inList(delegate, "contact", contacts) }
    }

    static DetachedCriteria<SharedContact> forOptions(Collection<Long> contactIds = [],
        Collection<ContactStatus> statuses = ContactStatus.VISIBLE_STATUSES,
        Long sharedById = null, Long sharedWithId = null) {

        new DetachedCriteria(SharedContact)
            .build {
                projections {
                    distinct("contact")
                }
                or {
                    isNull("dateExpired") //not expired if null
                    gt("dateExpired", DateTime.now(DateTimeZone.UTC))
                }
                contact {
                    CriteriaUtils.inList(delegate, "id", contactIds, true)
                    CriteriaUtils.inList(delegate, "status", ContactStatus.VISIBLE_STATUSES)
                    eq("isDeleted", false)
                }
                CriteriaUtils.inList(delegate, "status", statuses)
                sharedBy {
                    isNotNull("numberAsString")
                    if (sharedById) {
                        idEq(sharedById)
                    }
                }
                if (sharedWithId) {
                    sharedWith {
                        idEq(sharedWithId)
                    }
                }
            }
    }

    // Methods
    // -------

    SharedContact startSharing(ContactStatus cStatus1, SharePermission perm) {
        status = cStatus1
        permission = perm
        dateExpired = null
        this
    }

    SharedContact stopSharing() {
        dateExpired = DateTime.now(DateTimeZone.UTC)
        this
    }

    // Properties
    // ----------

    boolean getIsActive() { canModify || canView }

    boolean getCanModify() {
        (dateExpired == null || dateExpired?.isAfterNow()) &&
            permission == SharePermission.DELEGATE
    }

    boolean getCanView() {
        canModify || ((dateExpired == null || dateExpired?.isAfterNow()) &&
            permission == SharePermission.VIEW)
    }

    @Override
    Long getContactId() { canView ? contact.contactId : null }

    @Override
    PhoneNumber getFromNum() { canView ? sharedBy.number : null }

    @Override
    String getCustomAccountId() { canView ? sharedBy.customAccountId : null }

    @Override
    String getName() { canView ? contact.nameOrNumber : null }

    @Override
    String getNote() { canView ? contact.note : null }

    @Override
    List<ContactNumber> getNumbers() { canView ? contact.numbers : null }

    @Override
    List<ContactNumber> getSortedNumbers() { canView ? contact.sortedNumbers : null }

    @Override
    List<NotificationStatus> getNotificationStatuses() {
        // If we are modifying through a shared contact in contactService, we use the contact id
        // so it's indistinguishable whether we own the contact being updated or we are a collaborator
        // Therefore, any notification policies for collaborators will be stored in the sharedBy phone
        // and when we are getting notification statuses for the sharedWith collabors, we must
        // go to the sharedBy phone to retrieve the notification policies
        if (canView) {
            sharedBy.owner.getNotificationStatusesForStaffsAndRecords(sharedWith.owner.buildAllStaff(),
                [contact.context.record.id])
        }
        else { [] }
    }

    @Override
    Result<Record> tryGetRecord() {
        canModify ?
            IOCUtils.resultFactory.success(contact.context.record) :
            IOCUtils.resultFactory.failWithCodeAndStatus("sharedContact.insufficientPermission",
                ResultStatus.FORBIDDEN)
    }

    @Override
    Result<ReadOnlyRecord> tryGetReadOnlyRecord() {
        isActive ?
            IOCUtils.resultFactory.success(contact.context.record) :
            IOCUtils.resultFactory.failWithCodeAndStatus("sharedContact.insufficientPermission",
                ResultStatus.FORBIDDEN)
    }
}
