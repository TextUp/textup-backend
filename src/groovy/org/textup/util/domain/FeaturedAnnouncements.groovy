package org.textup.util.domain

import grails.compiler.GrailsTypeChecked

@GrailsTypeChecked
class FeaturedAnnouncements {

    static DetachedCriteria<FeaturedAnnouncement> forPhoneId(Long phoneId) {
        new DetachedCriteria(FeaturedAnnouncement).build {
            eq("owner.id", phoneId)
            gt(expiresAt, DateTime.now())
        }
    }

    static Closure buildForSort() {
        return { order("whenCreated", "desc") }
    }
}
