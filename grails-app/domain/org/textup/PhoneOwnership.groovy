package org.textup

import grails.compiler.GrailsTypeChecked
import groovy.transform.EqualsAndHashCode
import org.textup.rest.NotificationStatus
import org.textup.type.PhoneOwnershipType
import org.textup.util.*

@GrailsTypeChecked
@EqualsAndHashCode
class PhoneOwnership implements WithId, Saveable<PhoneOwnership> {

    boolean allowSharingWithOtherTeams = false // TODO add to marshaller
    Long ownerId
    Phone phone
    PhoneOwnershipType type

    static hasMany = [policies: OwnerPolicy]
    static mapping = {
        policies fetch: "join", cascade:"all-delete-orphan"
    }
    static constraints = {
    	ownerId validator: { Long val, PhoneOwnership obj ->
            if (obj.type && val && Utils.<Boolean>doWithoutFlush {
                Phones.buildForOwnerIdAndType(val, obj.type).count() <= 0
            }) {
                ["invalidId"]
            }
    	}
    }

    // Methods
    // -------

    Collection<Staff> buildAllStaff() {
        if (type == PhoneOwnershipType.INDIVIDUAL) {
            Staff s1 = Staff.get(ownerId)
            s1 ? [s1] : []
        }
        else { Team.get(ownerId)?.getActiveMembers() ?: [] }
    }

    Collection<OwnerPolicy> buildActivePoliciesForFrequency(NotificationFrequency freq1) {
        HashSet<Long> staffIds = new HashSet<>(buildAllStaff()*.id)
        policies?.findAll { OwnerPolicy op1 ->
            staffIds.contains(op1.staff?.id) && op1.frequency == freq1
        }
    }

    Organization buildOrganization() {
        if (type == PhoneOwnershipType.INDIVIDUAL) {
            Staff.get(ownerId)?.org
        }
        else { Team.get(ownerId)?.org }
    }

    String buildName() {
        if (type == PhoneOwnershipType.INDIVIDUAL) {
            Staff.get(ownerId)?.name ?: ""
        }
        else { Team.get(ownerId)?.name ?: "" }
    }
}
