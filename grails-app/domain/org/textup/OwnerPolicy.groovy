package org.textup

import grails.compiler.GrailsTypeChecked
import groovy.transform.EqualsAndHashCode
import org.textup.type.NotificationLevel
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*

@EqualsAndHashCode
@GrailsTypeChecked
class OwnerPolicy implements WithId, CanSave<OwnerPolicy>, ReadOnlyOwnerPolicy {

    // Need to declare id for it to be considered in equality operator
    // see: https://stokito.wordpress.com/2014/12/19/equalsandhashcode-on-grails-domains/
    Long id

    boolean shouldSendPreviewLink = DefaultOwnerPolicy.DEFAULT_SEND_PREVIEW_LINK
    NotificationFrequency frequency = DefaultOwnerPolicy.DEFAULT_FREQUENCY
    NotificationLevel level = DefaultOwnerPolicy.DEFAULT_LEVEL
    NotificationMethod method = DefaultOwnerPolicy.DEFAULT_METHOD
    Schedule schedule
    Staff staff

    static belongsTo = [owner: PhoneOwnership]
    static hasMany = [blacklist: Long, whitelist: Long] // of allowed/disallowed record ids
    static mapping = {
        schedule fetch: "join", cascade: "save-update"
    }
    static constraints = {
        schedule cascadeValidation: true
        frequency validator: { NotificationFrequency val, OwnerPolicy obj ->
            if (obj.method == NotificationMethod.EMAIL &&
                val == NotificationFrequency.IMMEDIATELY) {
                ["cannotSendEmailImmediately"]
            }
        }
    }

    static Result<OwnerPolicy> tryCreate(PhoneOwnership own1, Long staffId) {
        Staffs.mustFindForId(staffId)
            .then { Staff s1 -> Schedule.tryCreate().curry(s1) }
            .then { Staff s1, Schedule sched1 ->
                OwnerPolicy op1 = new OwnerPolicy(staff: s1, schedule: sched1)
                own1.addToPolicies(op1)
                DomainUtils.trySave(op1, ResultStatus.CREATED)
            }

    }

    // Methods
    // -------

    // can notify = schedule is available + recordId is allowed
    // [NOTE] the staff member returned may not have the required info to notify with
    // For example, if a staff member does not have a personal phone, we can't notify via text
    // We don't check personal phone number here because sometimes we don't notify using the
    // personal phone number and use the staff member's email instead
    @Override
    boolean canNotifyForAny(Collection<Long> recordIds) {
        schedule?.isAvailableNow() && recordIds?.any { Long rId -> isAllowed(rId) }
    }

    @Override
 	boolean isAllowed(Long recordId) {
 		if (level == NotificationLevel.ALL) {
            !blacklist?.contains(recordId)
 		}
 		else { whitelist?.contains(recordId) } // otherwise is NotificationLevel.NONE
 	}

    boolean enable(Long recordId) {
        if (recordId) {
            addToWhitelist(recordId) && removeFromBlacklist(recordId)
        }
    }

    boolean disable(Long recordId) {
        if (recordId) {
            addToBlacklist(recordId) && removeFromWhitelist(recordId)
        }
    }

    // Properties
    // ----------

    @Override
    ReadOnlySchedule getReadOnlySchedule() { schedule }

    @Override
    ReadOnlyStaff getReadOnlyStaff() { staff }
}
