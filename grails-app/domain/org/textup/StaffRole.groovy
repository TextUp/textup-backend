package org.textup

import org.apache.commons.lang.builder.HashCodeBuilder

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

	static StaffRole get(long staffId, long roleId) {
		StaffRole.where {
			staff == Staff.load(staffId) &&
			role == Role.load(roleId)
		}.get()
	}

	static boolean exists(long staffId, long roleId) {
		StaffRole.where {
			staff == Staff.load(staffId) &&
			role == Role.load(roleId)
		}.count() > 0
	}

	static StaffRole create(Staff staff, Role role, boolean flush = false) {
		def instance = new StaffRole(staff: staff, role: role)
		instance.save(flush: flush, insert: true)
		instance
	}

	static boolean remove(Staff u, Role r, boolean flush = false) {
		if (u == null || r == null) return false

		int rowCount = StaffRole.where {
			staff == Staff.load(u.id) &&
			role == Role.load(r.id)
		}.deleteAll()

		if (flush) { StaffRole.withSession { it.flush() } }

		rowCount > 0
	}

	static void removeAll(Staff u, boolean flush = false) {
		if (u == null) return

		StaffRole.where {
			staff == Staff.load(u.id)
		}.deleteAll()

		if (flush) { StaffRole.withSession { it.flush() } }
	}

	static void removeAll(Role r, boolean flush = false) {
		if (r == null) return

		StaffRole.where {
			role == Role.load(r.id)
		}.deleteAll()

		if (flush) { StaffRole.withSession { it.flush() } }
	}

	static constraints = {
		role validator: { Role r, StaffRole ur ->
			if (ur.staff == null) return
			boolean existing = false
			StaffRole.withNewSession {
				existing = StaffRole.exists(ur.staff.id, r.id)
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
