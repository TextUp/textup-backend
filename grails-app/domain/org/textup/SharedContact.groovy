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

    ContactPhoneRecord context
    ContactStatus status
    DateTime lastTouched = DateTime.now(DateTimeZone.UTC)
    DateTime whenCreated = DateTime.now(DateTimeZone.UTC)
    SharePermission permission

    static mapping = {
        lastTouched type: PersistentDateTime
        whenCreated type: PersistentDateTime
        context fetch: "join", cascade: "save-update"
    }
    static constraints = {
    	context cascadeValidation: true
        contact validator: { Contact val, SharedContact obj ->
            if (val.phone?.id != obj.sharedBy?.id) { ["contactOwnership", val.name] }
        }
        sharedBy validator: { Phone sBy, SharedContact obj ->
            if (sBy?.id == obj.sharedWith?.id) { ["shareWithMyself"] }
        }
    }

    // if the shared contact's status is null and we are validating, copy over the contact's status
    def beforeValidate() {
        if (!status) {
            status = contact?.status
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
