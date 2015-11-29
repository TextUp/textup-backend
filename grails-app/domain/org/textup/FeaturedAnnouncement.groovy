package org.textup

import groovy.transform.EqualsAndHashCode
import org.jadira.usertype.dateandtime.joda.PersistentDateTime
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.hibernate.FlushMode

@EqualsAndHashCode
class FeaturedAnnouncement {

	TeamPhone owner
	DateTime dateCreated = DateTime.now(DateTimeZone.UTC)
	DateTime expiresAt 
	RecordText featured

    static constraints = {
    	expiresAt validator:{ val, obj -> 
    		if (!val?.isAfter(obj.dateCreated)) { ["expiresBeforeCreation"] }
    	}
    	featured validator:{ val, obj ->
    		if (!belongsToOwner(val?.id, obj.owner?.id)) { ["notOwned"] }
    	}
    }
    static mapping = {
    	dateCreated type:PersistentDateTime
    	expiresAt type:PersistentDateTime
    }
    static namedQueries = {
    	notExpiredForTeamPhone { TeamPhone p1 ->
    		eq("owner", p1)
    		ge("expiresAt", DateTime.now(DateTimeZone.UTC)) //not expired
    		order("dateCreated", "desc")
    	}
    }

    ////////////////////
    // Helper methods //
    ////////////////////

    protected boolean belongsToOwner(Long textId, Long phoneId) {
    	if (textId == null || phoneId == null) { return false }
        boolean belongs = false
        FeaturedAnnouncement.withNewSession { session ->
            session.flushMode = FlushMode.MANUAL
            try {
                belongs = (RecordItem.forThisIdAndPhoneId(textId, phoneId).count() > 0)
            }
            finally { session.flushMode = FlushMode.AUTO }
        }
        belongs
    }

    void expireNow() {
    	this.expiresAt = DateTime.now(DateTimeZone.UTC)
    }

    /////////////////////
    // Property Access //
    /////////////////////

    void setExpiresAt(DateTime exp) {
    	this.expiresAt = exp?.withTimeZone(DateTimeZone.UTC)
    }
}
