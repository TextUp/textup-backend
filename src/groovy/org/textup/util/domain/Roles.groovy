package org.textup.util.domain

import grails.compiler.GrailsTypeChecked
import grails.gorm.DetachedCriteria
import groovy.transform.TypeCheckingMode
import org.joda.time.DateTime
import org.textup.*
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*

@GrailsTypeChecked
class Roles {

    static final String PUBLIC = "permitAll"
    static final String USER = "ROLE_USER"
    static final String ADMIN = "ROLE_ADMIN"

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
