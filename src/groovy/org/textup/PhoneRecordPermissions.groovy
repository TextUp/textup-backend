package org.textup

import grails.compiler.GrailsTypeChecked
import org.joda.time.DateTime

@GrailsTypeChecked
class PhoneRecordPermissions {

    private final DateTime _dateExpired
    private final SharePermission _permission

    PhoneRecordPermissions(DateTime dateExpired, SharePermission permission) {
        _dateExpired = dateExpired
        _permission = permission
    }

    // Methods
    // -------

    boolean isOwner() { _permission == null }

    boolean isNotExpired() {
        _dateExpired == null || _dateExpired?.isAfterNow()
    }

    boolean canModify() {
        isOwner() || (isNotExpired() && _permission == SharePermission.DELEGATE)
    }

    boolean canView() {
        canModify() || (isNotExpired() && _permission == SharePermission.VIEW)
    }
}
