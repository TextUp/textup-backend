package org.textup.util.domain

import grails.compiler.GrailsTypeChecked

@GrailsTypeChecked
class StaffRoles {

    // TODO not used
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

    // TODO not used
    static boolean remove(Staff u, Role r, boolean flush = false) {
        if (u == null || r == null) return false

        int rowCount = StaffRole.where {
            staff == Staff.load(u.id) &&
            role == Role.load(r.id)
        }.deleteAll()

        if (flush) { StaffRole.withSession { it.flush() } }

        rowCount > 0
    }

    // TODO not used
    static void removeAll(Staff u, boolean flush = false) {
        if (u == null) return

        StaffRole.where {
            staff == Staff.load(u.id)
        }.deleteAll()

        if (flush) { StaffRole.withSession { it.flush() } }
    }

    // TODO not used
    static void removeAll(Role r, boolean flush = false) {
        if (r == null) return

        StaffRole.where {
            role == Role.load(r.id)
        }.deleteAll()

        if (flush) { StaffRole.withSession { it.flush() } }
    }
}
