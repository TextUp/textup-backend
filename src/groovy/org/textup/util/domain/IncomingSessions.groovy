package org.textup.util.domain

import grails.compiler.GrailsTypeChecked

@GrailsTypeChecked
class IncomingSessions {

    // TODO hasPermissionsForSession
    static Result<Void> isAllowed(Long thisId) {
        AuthUtils.tryGetAuthId().then { Long authId ->
            AuthUtils.isAllowed(buildForAuth(thisId, authId).count() > 0)
        }
    }

    static DetachedCriteria<IncomingSession> buildForPhoneIdWithOptions(Long phoneId,
        Boolean hasCall = null, Boolean hasText = null) {

        new DetachedCriteria(IncomingSession).build {
            eq("phone.id", phoneId)
            if (hasCall != null) {
                eq("isSubscribedToCall", hasCall)
            }
            if (hasText != null) {
                eq("isSubscribedToText", hasText)
            }
        }
    }

    // Helpers
    // -------

    protected static DetachedCriteria<IncomingSession> buildForAuth(Long thisId, Long authId) {
        new DetachedCriteria(IncomingSession).build {
            idEq(thisId)
            "in"("phone", Phones.buildAllPhonesForStaffId(authId))
        }
    }
}
