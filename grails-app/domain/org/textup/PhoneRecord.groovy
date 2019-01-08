package org.textup

import grails.compiler.GrailsTypeChecked
import groovy.transform.EqualsAndHashCode
import org.joda.time.DateTime

// [NOTE] all properties in this parent class are DUPLICATED across sharing relationships

@GrailsTypeChecked
@EqualsAndHashCode
class PhoneRecord implements WithId, Saveable {

    DateTime lastTouched = DateTime.now(DateTimeZone.UTC)
    DateTime whenCreated = DateTime.now(DateTimeZone.UTC)
    Phone phone
    PhoneRecordStatus status = PhoneRecordStatus.ACTIVE
    Record record

    DateTime dateExpired // active if dateExpired is null or in the future
    PhoneRecord shareSource
    SharePermission permission // null implies ownership and therefore full permissions

    static mapping = {
        dateExpired type: PersistentDateTime
        lastTouched type: PersistentDateTime
        record fetch: "join", cascade: "save-update"
        whenCreated type: PersistentDateTime
        shareSource fetch: "join", cascade: "save-update"
    }
    static constraints = { // default nullable: false
        record cascadeValidation: true
        dateExpired nullable: true
        permission nullable: true, validator: { SharePermission val, PhoneRecord obj ->
            if (obj.shareSource != null && val == null) { ["mustSpecifySharingPermission"] }
        }
        shareSource cascadeValidation: true, validator: { PhoneRecord val, PhoneRecord obj ->
            if (val.phone?.id == obj.phone?.id) { ["shareWithMyself"] }
        }
    }

    // Methods
    // -------

    PhoneRecordPermissions toPermissions() { new PhoneRecordPermissions(dateExpired, permission) }

    // In the superclass implementation, which is the sharing implementation, we don't use the
    // `sharingOverride` argument but we keep it to possibly override this signature in subclasses
    PhoneRecordWrapper toWrapper(PhoneRecord sharingOverride = null) {
        shareSource?.toWrapper(this) ?: new PhoneRecordWrapper(this, toPermissions())
    }

    // TODO how to integrate this?
    // @Override
    // List<NotificationStatus> getNotificationStatuses() {
    //     WITH THE NEW CHANGES THIS HAS CHANGED BECAUSE WE NOW STORE NOTIFICATION SETTINGS
    //     ON THE `PHONE` PROPERTY WHICH IS THE SHAREDWITH PHONE FOR SHARED RELATIONSHIPS
    //
    //
    //     // If we are modifying through a shared contact in contactService, we use the contact id
    //     // so it's indistinguishable whether we own the contact being updated or we are a collaborator
    //     // Therefore, any notification policies for collaborators will be stored in the sharedBy phone
    //     // and when we are getting notification statuses for the sharedWith collabors, we must
    //     // go to the sharedBy phone to retrieve the notification policies
    //     if (canView) {
    //         sharedBy.owner.getNotificationStatusesForStaffsAndRecords(sharedWith.owner.buildAllStaff(),
    //             [contact.context.record.id])
    //     }
    //     else { [] }
    // }

    // Properties
    // ----------

    String getSecureName() { "" }

    String getPublicName() { StringUtils.buildInitials(secureName) }
}
