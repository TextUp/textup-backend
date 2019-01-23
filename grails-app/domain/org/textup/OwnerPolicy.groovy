package org.textup

import grails.compiler.GrailsTypeChecked
import groovy.transform.EqualsAndHashCode
import org.textup.type.NotificationLevel
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*

@GrailsTypeChecked
@EqualsAndHashCode
class OwnerPolicy implements WithId, CanSave<OwnerPolicy> {

    boolean shouldSendPreviewLink = true
    NotificationFrequency frequency = NotificationFrequency.IMMEDIATELY
    NotificationLevel level = NotificationLevel.ALL
    NotificationMethod method = NotificationMethod.TEXT
    Schedule schedule
    Staff staff

    static belongsTo = [owner: PhoneOwnership]
    static hasMany = [blacklist: Long, whitelist: Long] // of allowed/disallowed record ids
    static mapping = {
        schedule fetch: "join", cascade: "save-update"
    }
    static constraints = {
        schedule cascadeValidation: true
        frequency validation: { NotificationFrequency val, OwnerPolicy obj ->
            if (obj.method == NotificationMethod.EMAIL &&
                val == NotificationFrequency.IMMEDIATELY) {
                ["cannotSendEmailImmediately"]
            }
        }
    }

    static Result<OwnerPolicy> tryCreate(PhoneOwnership owner, Long staffId) {
        Staffs.mustFindForId(staffId)
            .then { Staff s1 -> Schedule.tryCreate().curry(s1) }
            .then { Staff s1, Schedule sched1 ->
                OwnerPolicy np1 = new OwnerPolicy(staff: s1, schedule: sched1)
                owner.addToPolicies(np1)
                DomainUtils.trySave(np1, ResultStatus.CREATED)
            }

    }

    // Methods
    // -------

    // can notify = schedule is available + recordId is allowed
    // [NOTE] the staff member returned may not have the required info to notify with
    // For example, if a staff member does not have a personal phone, we can't notify via text
    boolean canNotifyForAny(Collection<Long> recordIds) {
        schedule?.isAvailableNow() && recordIds?.any { Long rId -> isAllowed(rId) }
    }

 	boolean isAllowed(Long recordId) {
 		if (level == NotificationLevel.ALL) {
            blacklist?.contains(recordId)
 		}
 		else { whitelist?.contains(recordId) } // otherwise is NotificationLevel.NONE
 	}

    boolean enable(Long recordId) {
        addToWhitelist(recordId) && removeFromBlacklist(recordId)
    }

    boolean disable(Long recordId) {
        addToBlacklist(recordId) && removeFromWhitelist(recordId)
    }
}
