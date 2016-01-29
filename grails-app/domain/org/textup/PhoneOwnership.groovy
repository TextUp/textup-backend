package org.textup

import groovy.transform.EqualsAndHashCode
import org.hibernate.FlushMode

@EqualsAndHashCode
class PhoneOwnership {

	Phone phone
	Long ownerId
	PhoneOwnershipType type

    static constraints = {
    	ownerId validator: { val, obj ->
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
        PhoneOwnership.withNewSession { session ->
            session.flushMode = FlushMode.MANUAL
            try {
                Class clazz = (type == PhoneOwnershipType.INDIVIDUAL) ? Staff : Team
                validId = clazz.exists(ownerId)
            }
            finally { session.flushMode = FlushMode.AUTO }
        }
        validId
    }


    // Property access
    // ---------------

    List<Staff> getAll() {
        if (this.type === PhoneOwnershipType.INDIVIDUAL) {
            Staff s1 = Staff.get(this.ownerId)
            s1 ? [s1] : []
        }
        else { // group
            Team.get(this.ownerId)?.activeMembers ?: []
        }
    }

    String getName() {
        Class clazz = (this.type === PhoneOwnershipType.INDIVIDUAL) ? Staff : Team
        clazz.get(this.ownerId)?.name ?: ''
    }
}
