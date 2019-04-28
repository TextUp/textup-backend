package org.textup

import grails.compiler.GrailsTypeChecked
import groovy.transform.EqualsAndHashCode
import org.joda.time.DateTime
import org.textup.type.*

@EqualsAndHashCode
@GrailsTypeChecked
class PhoneRecordPermissions {

    private final DateTime _dateExpired
    final SharePermission level

    PhoneRecordPermissions(DateTime dateExpired, SharePermission permission) {
        _dateExpired = dateExpired
        level = permission
    }

    // Methods
    // -------

    boolean isOwner() { level == null }

    boolean isNotExpired() {
        isOwner() || _dateExpired == null || _dateExpired?.isAfterNow()
    }

    boolean canModify() {
        isOwner() || (isNotExpired() && level == SharePermission.DELEGATE)
    }

    boolean canView() {
        canModify() || (isNotExpired() && level == SharePermission.VIEW)
    }
}
