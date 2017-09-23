package org.textup

import grails.compiler.GrailsCompileStatic
import groovy.transform.EqualsAndHashCode
import org.textup.rest.NotificationStatus
import org.textup.type.PhoneOwnershipType

@GrailsCompileStatic
@EqualsAndHashCode
class PhoneOwnership {

	Phone phone
	Long ownerId
	PhoneOwnershipType type

    static constraints = {
    	ownerId validator: { Long val, PhoneOwnership obj ->
            Closure<Boolean> doesOwnerExist = {
                (obj.type == PhoneOwnershipType.INDIVIDUAL) ? Staff.exists(val) : Team.exists(val)
            }
            if (obj.type && val && !Helpers.<Boolean>doWithoutFlush(doesOwnerExist)) {
                ["invalidId"]
            }
    	}
    }
    static hasMany = [policies:NotificationPolicy]
    static mapping = {
        policies lazy:false, cascade:"all-delete-orphan"
    }

    // Property access
    // ---------------

    // staff members can be notified if they are available right now and if they
    // have a notification policy that allows it
    List<Staff> getCanNotifyAndAvailable(Collection<Long> recordIds) {
        List<Staff> canNotify = []
        getNotificationStatusesForRecords(recordIds).each { NotificationStatus status ->
            if (status.canNotify && status.staff.isAvailableNow()) { canNotify << status.staff }
        }
        canNotify
    }
    List<NotificationStatus> getNotificationStatusesForRecords(Collection<Long> recordIds) {
        getNotificationStatusesForStaffsAndRecords(getAll(), recordIds)
    }
    List<NotificationStatus> getNotificationStatusesForStaffsAndRecords(Collection<Staff> staffs,
        Collection<Long> recordIds) {
        List<NotificationStatus> statuses = []
        staffs.each { Staff s1 ->
            NotificationPolicy np1 = getPolicyForStaff(s1.id)
            // can notify if doesn't have a notification policy OR
            // has a notification policy that permits notification
            statuses << new NotificationStatus(staff:s1,
                canNotify:!np1 || np1?.canNotifyForAny(recordIds))
        }
        statuses
    }
    List<Staff> getAll() {
        if (this.type == PhoneOwnershipType.INDIVIDUAL) {
            Staff s1 = Staff.get(this.ownerId)
            s1 ? [s1] : []
        }
        else { // group
            Team.get(this.ownerId)?.getActiveMembers() ?: []
        }
    }
    String getName() {
        if (this.type == PhoneOwnershipType.INDIVIDUAL) {
            Staff.get(this.ownerId)?.name ?: ''
        }
        else {
            Team.get(this.ownerId)?.name ?: ''
        }
    }
    // Notification Policies might not all correspond to staff members that åre owners on this phone
    // because we also include staff members that can access one of this phone's contacts
    // through a sharing arrangement. Also, if we transfer phones, then the new owners will not
    // correspond with the staff ids in this list of policies
    NotificationPolicy getOrCreatePolicyForStaff(Long staffId) {
        NotificationPolicy np1 = getPolicyForStaff(staffId)
        if (!np1) { // create a new notification policy if none currently exists for this staff id
            np1 = new NotificationPolicy(staffId:staffId)
            this.addToPolicies(np1)
        }
        np1
    }
    NotificationPolicy getPolicyForStaff(Long staffId) {
        this.policies?.find { NotificationPolicy np1 -> np1.staffId == staffId }
    }
}
