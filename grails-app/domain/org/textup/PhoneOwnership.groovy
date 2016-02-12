package org.textup

import groovy.transform.EqualsAndHashCode
import org.hibernate.FlushMode
import org.textup.types.PhoneOwnershipType
import grails.compiler.GrailsCompileStatic
import org.hibernate.Session

@GrailsCompileStatic
@EqualsAndHashCode
class PhoneOwnership {

	Phone phone
	Long ownerId
	PhoneOwnershipType type

    static constraints = {
    	ownerId validator: { Long val, PhoneOwnership obj ->
            if (!obj.isValidId(obj.type, val)) {
                ["invalidId"]
            }
    	}
    }

    // Validator
    // ---------

    protected boolean isValidId(PhoneOwnershipType type, Long ownerId) {
        if (!type || !ownerId) {
            return false
        }
        boolean validId = false
        PhoneOwnership.withNewSession { Session session ->
            session.flushMode = FlushMode.MANUAL
            try {
                if (type == PhoneOwnershipType.INDIVIDUAL) {
                    validId = Staff.exists(ownerId)
                }
                else {
                    validId = Team.exists(ownerId)
                }
            }
            finally { session.flushMode = FlushMode.AUTO }
        }
        validId
    }


    // Property access
    // ---------------

    Collection<Staff> getAll() {
        if (this.type == PhoneOwnershipType.INDIVIDUAL) {
            Staff s1 = Staff.get(this.ownerId)
            s1 ? [s1] : []
        }
        else { // group
            Team.get(this.ownerId)?.activeMembers ?: []
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
}
