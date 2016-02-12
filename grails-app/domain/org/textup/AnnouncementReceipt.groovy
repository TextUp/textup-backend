package org.textup

import groovy.transform.EqualsAndHashCode
import org.jadira.usertype.dateandtime.joda.PersistentDateTime
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.textup.types.RecordItemType
import grails.compiler.GrailsCompileStatic
import groovy.transform.TypeCheckingMode

@GrailsCompileStatic
@EqualsAndHashCode
class AnnouncementReceipt {

	DateTime whenCreated = DateTime.now(DateTimeZone.UTC)
	FeaturedAnnouncement announcement
	IncomingSession session
	RecordItemType type

    static constraints = {
    	announcement validator: { FeaturedAnnouncement val, AnnouncementReceipt obj ->
    		if (val.owner != obj.session.phone) {
    			["differentPhones"]
    		}
    	}
    }
    static mapping = {
    	whenCreated type:PersistentDateTime
    }
}
