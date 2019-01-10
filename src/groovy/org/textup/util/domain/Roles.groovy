package org.textup.util.domain

import grails.compiler.GrailsTypeChecked

@GrailsTypeChecked
class Roles {

    static final String USER = "ROLE_USER"
    static final String ADMIN = "ROLE_ADMIN"

    static Result<Role> tryGetUserRole() {
        Role r1 = Role.findByAuthority(USER, [cache: true])
        if (r1) {
            IOCUtils.resultFactory.success(r1)
        }
        else { Role.create(USER) }
    }

    static Result<Role> tryGetAdminRole() {
        Role r1 = Role.findByAuthority(ADMIN, [cache: true])
        if (r1) {
            IOCUtils.resultFactory.success(r1)
        }
        else { Role.create(ADMIN) }
    }
}
