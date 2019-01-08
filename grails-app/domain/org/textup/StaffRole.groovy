package org.textup

import org.apache.commons.lang.builder.HashCodeBuilder
import org.textup.util.domain.*

class StaffRole implements Serializable {

	private static final long serialVersionUID = 1

	Staff staff
	Role role

	boolean equals(other) {
		if (!(other instanceof StaffRole)) {
			return false
		}

		other.staff?.id == staff?.id &&
		other.role?.id == role?.id
	}

	int hashCode() {
		def builder = new HashCodeBuilder()
		if (staff) builder.append(staff.id)
		if (role) builder.append(role.id)
		builder.toHashCode()
	}

	static constraints = {
		role validator: { Role r, StaffRole ur ->
			if (ur.staff == null) return
			boolean existing = false
			StaffRole.withNewSession {
				existing = StaffRoles.exists(ur.staff.id, r.id)
			}
			if (existing) {
				return 'userRole.exists'
			}
		}
	}

	static mapping = {
		id composite: ['role', 'staff']
		version false
	}
}
