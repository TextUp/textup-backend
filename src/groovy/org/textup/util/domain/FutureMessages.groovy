package org.textup.util.domain

import grails.compiler.GrailsTypeChecked

@GrailsTypeChecked
class FutureMessages {

    // TODO hasPermissionsForFutureMessage
    static Result<Void> isAllowed(Long thisId) {
        AuthUtils.tryGetAuthId().then { Long authId ->
            AuthUtils.isAllowed(buildForAuth(thisId, authId).count() > 0)
        }
    }

    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    static DetachedCriteria<FutureMessage> buildForRecords(Collection<Record> records) {
        new DetachedCriteria(FutureMessage)
            .build { CriteriaUtils.inList(delegate, "record", records) }
    }

    // Helpers
    // -------

    protected static DetachedCriteria<FutureMessage> buildForAuth(Long thisId, Long authId) {
        new DetachedCriteria(FutureMessage).build {
            idEq(thisId)
            "in"("record", PhoneRecords.buildActiveForStaffId(authId)
                .build(PhoneRecords.returnsRecord())
        }
    }
}
