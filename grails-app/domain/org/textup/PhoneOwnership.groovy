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

    // Need to declare id for it to be considered in equality operator
    // see: https://stokito.wordpress.com/2014/12/19/equalsandhashcode-on-grails-domains/
    Long id

    boolean allowSharingWithOtherTeams = false
    Long ownerId
    Phone phone
    PhoneOwnershipType type

    static hasMany = [policies: OwnerPolicy]
    static mapping = {
        policies fetch: "join", cascade: "all-delete-orphan"
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

    // [NOTE] If passed-in frequency is null, we find active read-only policies for ALL FREQUENCIES
    Collection<? extends ReadOnlyOwnerPolicy> buildActiveReadOnlyPolicies(NotificationFrequency freq1 = null) {
        HashSet<Staff> allCurrentStaffs = new HashSet<>(buildAllStaff())
        Collection<Staff> staffsWithDifferentFrequency = []
        Collection<? extends ReadOnlyOwnerPolicy> foundPolicies = []
        policies?.each { OwnerPolicy op1 ->
            if (allCurrentStaffs.contains(op1.staff)) {
                if (!freq1 || op1.frequency == freq1) {
                    foundPolicies << op1
                }
                else { staffsWithDifferentFrequency << op1.staff }
            }
        }
        // should only fill in missing when we are trying to build for the default frequency
        // and ONLY FOR STAFFS that don't have a policy at all. Staffs with non-default frequencies
        // should not show up if we are requesting only the staff that have the default frequency
        if (!freq1 || freq1 == DefaultOwnerPolicy.DEFAULT_FREQUENCY) {
            Collection<Staff> staffsThatShouldHavePolicy = CollectionUtils.difference(allCurrentStaffs,
                staffsWithDifferentFrequency)
            Collection<Staff> missing = CollectionUtils.difference(staffsThatShouldHavePolicy,
                foundPolicies*.readOnlyStaff)
            foundPolicies.addAll(DefaultOwnerPolicy.createAll(missing))
        }
        foundPolicies
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
