package org.textup.util.domain

import grails.compiler.GrailsTypeChecked

@GrailsTypeChecked
class FeaturedAnnouncements {

    // TODO hasPermissionsForAnnouncement
    static Result<Void> isAllowed(Long thisId) {
        AuthUtils.tryGetAuthId().then { Long authId ->
            AuthUtils.isAllowed(buildForAuth(thisId, authId).count() > 0)
        }
    }

    static DetachedCriteria<FeaturedAnnouncement> buildForPhoneId(Long phoneId) {
        new DetachedCriteria(FeaturedAnnouncement).build {
            eq("phone.id", phoneId)
            gt(expiresAt, DateTime.now())
        }
    }

    static Closure forSort() {
        return { order("whenCreated", "desc") }
    }

    // Helpers
    // -------

    protected static DetachedCriteria<FeaturedAnnouncement> buildForAuth(Long thisId, Long authId) {
        new DetachedCriteria(FeaturedAnnouncement).build {
            idEq(thisId)
            "in"("phone", Phones.buildAllPhonesForStaffId(authId))
        }
    }
}
