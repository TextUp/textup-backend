package org.textup

import grails.compiler.GrailsTypeChecked
import groovy.transform.EqualsAndHashCode
import java.util.concurrent.TimeUnit
import org.jadira.usertype.dateandtime.joda.PersistentDateTime
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.quartz.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*

@EqualsAndHashCode
@GrailsTypeChecked
class SimpleFutureMessage extends FutureMessage implements ReadOnlySimpleFutureMessage {

	long repeatIntervalInMillis = TimeUnit.DAYS.toMillis(1)
	Integer repeatCount

	static transients = ["repeatIntervalInDays"]
    static constraints = {
        repeatIntervalInMillis min:TypeConversionUtils.to(Long, TimeUnit.DAYS.toMillis(1))
		repeatCount nullable:true, validator:{ Integer rNum, SimpleFutureMessage msg ->
			if (rNum == SimpleTrigger.REPEAT_INDEFINITELY && !msg.endDate) {
				["unboundedNeedsEndDate"]
			}
		}
    }

    static SimpleFutureMessage tryCreate(Record rec1, String message, MediaInfo mInfo) {
        SimpleFutureMessage sMsg = new SimpleFutureMessage(record: rec1,
            message: message,
            media: mInfo)
        DomainUtils.trySave(sMsg, ResultStatus.CREATED)
    }

    // Properties
    // ----------

    @Override
    boolean getIsRepeating() {
    	repeatCount || super.endDate
    }

    Integer getTimesTriggered() {
    	(trigger instanceof SimpleTrigger) ? trigger.getTimesTriggered() : null
    }

    long getRepeatIntervalInDays() {
        TimeUnit.MILLISECONDS.toDays(repeatIntervalInMillis)
    }

    void setRepeatIntervalInDays(long numDays) {
    	repeatIntervalInMillis = TimeUnit.DAYS.toMillis(numDays)
    }

    // Helpers
    // -------

    @Override
    protected ScheduleBuilder getScheduleBuilder() {
        SimpleScheduleBuilder builder = SimpleScheduleBuilder.simpleSchedule()
        if (!getIsRepeating()) {
            return builder
        }
        builder.withIntervalInMilliseconds(repeatIntervalInMillis)
        super.endDate ?
            builder.repeatForever() :
            builder.withRepeatCount(repeatCount)
    }

    @Override
    protected boolean getShouldReschedule() {
        super.getShouldReschedule() ||
            ["repeatIntervalInMillis", "repeatCount", "endDate"].any { String prop -> isDirty(prop) }
    }
}
