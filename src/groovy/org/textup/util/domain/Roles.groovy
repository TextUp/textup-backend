package org.textup.util.domain

import grails.compiler.GrailsTypeChecked

@GrailsTypeChecked
class Roles {

    static final String PUBLIC = "permitAll"
    static final String USER = "ROLE_USER"
    static final String ADMIN = "ROLE_ADMIN"
    static final Collection<String> USER_ROLES = Collections.unmodifiableCollection([USER, ADMIN])
    static final Collection<String> SUPER_ROLES = Collections.unmodifiableCollection([ADMIN])

    static Result<Role> tryGetUserRole() {
        Role r1 = Role.findByAuthority(USER, [cache: true])
        if (r1) {
            IOCUtils.resultFactory.success(r1)
        }
        else { Role.tryCreate(USER) }
    }

    static Result<Role> tryGetAdminRole() {
        Role r1 = Role.findByAuthority(ADMIN, [cache: true])
        if (r1) {
            IOCUtils.resultFactory.success(r1)
        }
        else { Role.tryCreate(ADMIN) }
    }
}
