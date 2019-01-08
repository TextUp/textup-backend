package org.textup.util.domain

import grails.compiler.GrailsTypeChecked

@GrailsTypeChecked
class Records {

    // TODO remove
    // // TODO hasPermissionsForRecord
    // static Result<Void> isAllowed(Long thisId) {
    //     AuthUtils.tryGetAuthId().then { Long authId ->
    //         AuthUtils.isAllowed(buildForAuth(thisId, authId).count() > 0)
    //     }
    // }

    static Result<Record> create() {
        Utils.trySave(new Record())
    }

    // // TODO remove
    // // Helpers
    // // -------

    // protected static DetachedCriteria<Record> buildForAuth(Long thisId, Long authId) {
    //     new DetachedCriteria(Record).build {
    //         idEq(thisId)
    //         "in"("id", PhoneRecords.buildActiveForStaffId(authId)
    //             .build(PhoneRecords.returnsRecordId()))
    //     }
    // }
}
