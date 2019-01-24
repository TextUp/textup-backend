package org.textup

import grails.compiler.GrailsTypeChecked
import groovy.transform.EqualsAndHashCode
import org.joda.time.DateTime
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*

// [NOTE] all properties in this parent class are DUPLICATED across sharing relationships

@GrailsTypeChecked // TODO
@EqualsAndHashCode
class PhoneRecord implements WithId, CanSave<PhoneRecord> {

    FutureMessageJobService futureMessageJobService

    DateTime lastTouched = JodaUtils.now()
    DateTime whenCreated = JodaUtils.now()
    Phone phone
    PhoneRecordStatus status = PhoneRecordStatus.ACTIVE
    Record record

    PhoneRecord shareSource
    DateTime dateExpired // active if dateExpired is null or in the future
    SharePermission permission // null implies ownership and therefore full permissions

    static transients = ["futureMessageJobService"]
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
        permission nullable: true
        shareSource nullable: true, cascadeValidation: true, validator: { PhoneRecord val, PhoneRecord obj ->
            if (val) {
                if (val.phone?.id == obj.phone?.id) { ["shareWithMyself"] }
                if (val.record?.id != obj.record?.id) { ["mismatchedRecord"] }
                if (!obj.permission) { ["mustSpecifySharingPermission"] }
            }
        }
    }

    static class ShareInfo {
        final DateTime whenCreated
        final Long phoneId
        final String permission
    }

    static Result<PhoneRecord> tryCreate(SharePermission perm, PhoneRecord toShare, Phone sWith) {
        PhoneRecord pr1 = new PhoneRecord(shareSource: toShare,
            record: toShare.record,
            phone: sWith,
            permission: perm)
        DomainUtils.trySave(pr1, ResultStatus.CREATED)
    }

    // Methods
    // -------

    PhoneRecord.ShareInfo toShareInfo() {
        new PhoneRecord.ShareInfo(whenCreated: whenCreated,
            phoneId: phone.id,
            permission: permission.toString())
    }

    boolean isActive() { toPermissions().isNotExpired() }

    PhoneRecordPermissions toPermissions() { new PhoneRecordPermissions(dateExpired, permission) }

    // In the superclass implementation, which is the sharing implementation, we don't use the
    // `sharingOverride` argument but we keep it to possibly override this signature in subclasses
    PhoneRecordWrapper toWrapper(PhoneRecord sharingOverride = null) {
        shareSource?.toWrapper(this) ?: new PhoneRecordWrapper(this, toPermissions())
    }

    Result<List<FutureMessage>> tryCancelFutureMessages() {
        List<FutureMessage> fMsgs = FutureMessages.buildForRecordIds([record.id]).list()
        futureMessageJobService.cancelAll(fMsgs)
            .logFail("tryCancelFutureMessages")
            .toResult(true)
    }

    Collection<Record> buildRecords() { [record] }

    // Properties
    // ----------

    String getSecureName() { "" }

    String getPublicName() { StringUtils.buildInitials(secureName) }
}
