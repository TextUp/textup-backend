package org.textup

import grails.validation.Validateable
import groovy.transform.ToString
import org.joda.time.DateTime
import org.joda.time.DateTimeZone

@ToString
@Validateable
class ScheduleChange {
    String type
    DateTime when //assume UTC timezone
    String timezone
    private DateTimeZone tz

    static constraints = {
    	type blank:false, nullable:false, inList:[Constants.SCHEDULE_AVAILABLE, Constants.SCHEDULE_UNAVAILABLE]
    	when nullable:false
    	timezone blank:true, nullable:true
    	tz nullable:true
    }

    void setTimezone(String tzId) {
    	if (tzId) {
    		try {
				tz = DateTimeZone.forId(tzId)
				timezone = tzId
			}
			catch(e) {}
    	}
    }

    void setWhen(DateTime w) {
        when = w?.withZone(DateTimeZone.UTC)
    }

    DateTime getWhen() { tz ? when?.withZone(tz) : when }
}
