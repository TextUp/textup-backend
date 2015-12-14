package org.textup

import grails.validation.Validateable
import groovy.transform.ToString
import org.joda.time.DateTime
import org.joda.time.DateTimeZone

@ToString
@Validateable
class ScheduleChange {
    String type
    DateTime when

    static constraints = {
    	type blank:false, nullable:false, inList:[Constants.SCHEDULE_AVAILABLE, Constants.SCHEDULE_UNAVAILABLE]
    	when nullable:false
    }

    void setWhen(DateTime w) {
        when = w?.withZone(DateTimeZone.UTC)
    }
}
