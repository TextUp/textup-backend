package org.textup

import grails.compiler.GrailsTypeChecked
import groovy.transform.EqualsAndHashCode
import org.jadira.usertype.dateandtime.joda.PersistentDateTime
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.textup.type.RecordItemType
import org.textup.util.*

@GrailsTypeChecked
@EqualsAndHashCode
class FeaturedAnnouncement implements WithId, Saveable<FeaturedAnnouncement> {

    DateTime expiresAt
    DateTime whenCreated = DateTimeUtils.now()
    Phone phone
    String message

    static mapping = {
        whenCreated type:PersistentDateTime
        expiresAt type:PersistentDateTime
    }
    static constraints = {
    	expiresAt validator:{ DateTime val, FeaturedAnnouncement obj ->
    		if (!val?.isAfter(obj.whenCreated)) { ["expiresBeforeCreation"] }
    	}
    }

    static Result<FeaturedAnnouncement> tryCreate(Phone p1, DateTime expires, String msg) {
        FeaturedAnnouncement fa1 = new FeaturedAnnouncement(phone: p1, expiresAt: expires, message: msg)
        DomainUtils.trySave(fa1, ResultStatus.CREATED)
    }

    // Methods
    // -------

    void expireNow() {
    	expiresAt = DateTimeUtils.now()
    }

    // Properties
    // ----------

    void setExpiresAt(DateTime exp) {
        expiresAt = exp?.withZone(DateTimeZone.UTC)
    }

    boolean getIsExpired() {
        expiresAt?.isBefore(DateTimeUtils.now())
    }
}
