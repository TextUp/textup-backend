package org.textup

import grails.compiler.GrailsCompileStatic
import groovy.transform.EqualsAndHashCode
import java.util.concurrent.TimeUnit
import java.util.UUID
import org.hibernate.FlushMode
import org.hibernate.Session
import org.jadira.usertype.dateandtime.joda.PersistentDateTime
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.quartz.ScheduleBuilder
import org.quartz.Scheduler
import org.quartz.SimpleScheduleBuilder
import org.quartz.SimpleTrigger
import org.quartz.Trigger
import org.quartz.TriggerKey
import org.restapidoc.annotation.*
import org.textup.types.FutureMessageType
import org.textup.validator.OutgoingMessage

@EqualsAndHashCode
@GrailsCompileStatic
@RestApiObject(name="Simple Future Message", description='''Message to be sent at \
    some point in the future. Can repeat at a regular interval.''')
class SimpleFutureMessage extends FutureMessage {

	long repeatIntervalInMillis = TimeUnit.DAYS.toMillis(1)

	@RestApiObjectField(
        description    = "Number of times to repeat",
        allowedType    = "Integer",
        mandatory      = false,
        useForCreation = true)
	Integer repeatCount

    @RestApiObjectFields(params=[
        @RestApiObjectField(
            apiFieldName   = "repeatIntervalInDays",
            description    = "Number of repeat days between firings",
            allowedType    = "Integer",
            defaultValue   = "1",
            mandatory      = true,
            useForCreation = true),
        @RestApiObjectField(
            apiFieldName   = "timesTriggered",
            description    = "Number of times the message has been fired in its lifetime",
            allowedType    = "Integer",
            defaultValue   = "0",
            useForCreation = false),
    ])
	static transients = ["repeatIntervalInDays"]
    static constraints = {
        repeatIntervalInMillis min:Helpers.toLong(TimeUnit.DAYS.toMillis(1))
		repeatCount nullable:true, validator:{ Integer rNum, SimpleFutureMessage msg ->
			if (rNum == SimpleTrigger.REPEAT_INDEFINITELY && !msg.endDate) {
				["unboundedNeedsEndDate"]
			}
		}
    }

    // Helper Methods
    // --------------

    @Override
    protected ScheduleBuilder getScheduleBuilder() {
    	if (!this.getIsRepeating()) {
    		return null
    	}
        SimpleScheduleBuilder builder = SimpleScheduleBuilder.simpleSchedule()
        builder.withIntervalInMilliseconds(this.repeatIntervalInMillis)
       	super.endDate ?
       		builder.repeatForever() :
            builder.withRepeatCount(this.repeatCount)
    }
    @Override
    protected boolean getShouldReschedule() {
    	super.getShouldReschedule() ||
    		["repeatIntervalInMillis", "repeatCount", "endDate"].any(this.&isDirty)
    }

    // Property Access
    // ---------------

    @Override
    boolean getIsRepeating() {
    	this.repeatCount || super.endDate
    }

    Integer getTimesTriggered() {
    	(this.trigger instanceof SimpleTrigger) ? this.trigger.getTimesTriggered() : null
    }

    long getRepeatIntervalInDays() {
        TimeUnit.MILLISECONDS.toDays(this.repeatIntervalInMillis)
    }
    void setRepeatIntervalInDays(long numDays) {
    	this.repeatIntervalInMillis = TimeUnit.DAYS.toMillis(numDays)
    }
}
