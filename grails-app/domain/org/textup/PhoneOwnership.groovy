package org.textup

import grails.compiler.GrailsTypeChecked
import groovy.transform.EqualsAndHashCode
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*

@EqualsAndHashCode
@GrailsTypeChecked
class PhoneOwnership implements WithId, CanSave<PhoneOwnership> {

    boolean allowSharingWithOtherTeams = false
    Long ownerId
    Phone phone
    PhoneOwnershipType type

    static hasMany = [policies: OwnerPolicy]
    static mapping = {
        policies fetch: "join", cascade: "all-delete-orphan"
    }
    static constraints = {
    	ownerId validator: { Long val, PhoneOwnership obj ->
            if (val) {
                if (obj.type == PhoneOwnershipType.INDIVIDUAL && !Staff.exists(val)) {
                    ["nonexistentStaff"]
                }
                else if (obj.type == PhoneOwnershipType.GROUP && !Team.exists(val)) {
                    ["nonexistentTeam"]
                }
            }
    	}
    }

    static Result<PhoneOwnership> tryCreate(Phone p1, Long ownerId, PhoneOwnershipType type) {
        PhoneOwnership own1 = new PhoneOwnership(ownerId: ownerId, type: type, phone: p1)
        DomainUtils.trySave(own1, ResultStatus.CREATED)
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

    Collection<? extends ReadOnlyOwnerPolicy> buildActiveReadOnlyPoliciesForFrequency(NotificationFrequency freq1) {
        HashSet<Staff> allStaffs = new HashSet<>(buildAllStaff())
        if (freq1) {
            Collection<? extends ReadOnlyOwnerPolicy> founds = []
            policies?.each { OwnerPolicy op1 ->
                if (allStaffs.contains(op1.staff) && op1.frequency == freq1) {
                    founds << op1
                }
            }
            if (DefaultOwnerPolicy.shouldEnsureAll(freq1)) {
                Collection<Staff> missing = CollectionUtils.difference(allStaffs, founds*.readOnlyStaff)
                founds.addAll(DefaultOwnerPolicy.createAll(missing))
            }
            founds
        }
        else { [] }
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
