package org.textup.util.domain

import grails.compiler.GrailsTypeChecked
import grails.gorm.DetachedCriteria
import org.joda.time.DateTime
import org.textup.*
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*

@GrailsTypeChecked
class FeaturedAnnouncements {

    static Result<Long> isAllowed(Long thisId) {
        AuthUtils.tryGetAuthId()
            .then { Long authId -> AuthUtils.isAllowed(buildForAuth(thisId, authId).count() > 0) }
            .then { IOCUtils.resultFactory.success(thisId) }
    }

    static Result<FeaturedAnnouncement> mustFindForId(Long aId) {
        FeaturedAnnouncement fa1 = aId ? FeaturedAnnouncement.get(aId) : null
        if (fa1) {
            IOCUtils.resultFactory.success(fa1)
        }
        else {
            IOCUtils.resultFactory.failWithCodeAndStatus("announcementService.update.notFound", // TODO
                ResultStatus.NOT_FOUND, [aId])
        }
    }

    static boolean anyForPhoneId(Long phoneId) {
        FeaturedAnnouncements.buildActiveForPhoneId(phoneId).count() > 0
    }

    static DetachedCriteria<FeaturedAnnouncement> buildActiveForPhoneId(Long phoneId) {
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
            "in"("phone", Phones.buildAllActivePhonesForStaffId(authId))
        }
    }
}
