package org.textup

import groovy.transform.EqualsAndHashCode
import org.jadira.usertype.dateandtime.joda.PersistentDateTime
import org.joda.time.DateTime
import org.joda.time.DateTimeZone

@EqualsAndHashCode
class AnnouncementReceipt {

	DateTime dateCreated = DateTime.now(DateTimeZone.UTC)
	FeaturedAnnouncement announcement
	IncomingSession session
	RecordItemType type

    static constraints = {
    	announcement validator: { val, obj ->
    		if (val.owner != obj.session.phone) {
    			["differentPhones"]
    		}
    	}
    }
    static mapping = {
        autoTimestamp false
    	dateCreated type:PersistentDateTime
    }
}
