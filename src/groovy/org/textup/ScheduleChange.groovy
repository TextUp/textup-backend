package org.textup

import grails.validation.Validateable
import groovy.transform.ToString
import org.joda.time.DateTime

@ToString
@Validateable
class ScheduleChange {
    String type 
    DateTime when

    static constraints = {
    	type blank:false, nullable:false, inList:[Constants.SCHEDULE_AVAILABLE, Constants.SCHEDULE_UNAVAILABLE]
    	when nullable:false
    }
}