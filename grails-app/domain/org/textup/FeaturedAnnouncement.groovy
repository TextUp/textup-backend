package org.textup

import grails.compiler.GrailsTypeChecked
import groovy.transform.EqualsAndHashCode
import org.jadira.usertype.dateandtime.joda.PersistentDateTime
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*

@EqualsAndHashCode
@GrailsTypeChecked
class FeaturedAnnouncement implements WithId, CanSave<FeaturedAnnouncement> {

    DateTime expiresAt
    DateTime whenCreated = JodaUtils.utcNow()
    Phone phone
    String message

    static mapping = {
        whenCreated type: PersistentDateTime
        expiresAt type: PersistentDateTime
    }
    static constraints = {
        // TODO add character limit
        // message maxSize: (ValidationUtils.TEXT_BODY_LENGTH * 2)
    	expiresAt validator: { DateTime val, FeaturedAnnouncement obj ->
    		if (!val?.isAfter(obj.whenCreated)) { ["expiresBeforeCreation"] }
    	}
    }

    static Result<FeaturedAnnouncement> tryCreate(Phone p1, DateTime expires, String msg) {
        FeaturedAnnouncement fa1 = new FeaturedAnnouncement(phone: p1, expiresAt: expires, message: msg)
        DomainUtils.trySave(fa1, ResultStatus.CREATED)
    }

    // Properties
    // ----------

    void setExpiresAt(DateTime exp) {
        expiresAt = exp?.withZone(DateTimeZone.UTC)
    }

    boolean getIsExpired() {
        expiresAt?.isBefore(JodaUtils.utcNow())
    }
}
