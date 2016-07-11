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
class FutureMessage {

    Trigger trigger
    FutureMessageService futureMessageService
    Scheduler quartzScheduler

    // In the job, when this message will not be executed again, we manually mark
    // the job as 'done'. We have to rely on this manual bookkeeping because we want
    // to keep our implementation of done-ness agnostic of the Quartz jobstore we
    // choose. If we choose the in-memory jobstore, there isn't a way that we can
    // incorporate the information in the scheduler about the done-ness in our db query
    // However, after retrieving this instance, we can double check to see if we
    // were correct by calling the getIsActuallyDone method
    boolean isDone = false

	DateTime whenCreated = DateTime.now(DateTimeZone.UTC)
	String key = UUID.randomUUID().toString()
	Record record

	DateTime startDate = DateTime.now(DateTimeZone.UTC).plusMinutes(1)
    boolean notifySelf = false
    FutureMessageType type
    long repeatIntervalInMillis = TimeUnit.DAYS.toMillis(1)
	String message

	Integer repeatCount
	DateTime endDate

	static transients = ["trigger", "futureMessageService", "quartzScheduler",
        "repeatIntervalInDays"]
    static constraints = {
        repeatIntervalInMillis min:Helpers.toLong(TimeUnit.DAYS.toMillis(1))
        record nullable: false
    	message blank:false, nullable:false, maxSize:(Constants.TEXT_LENGTH * 2)
		repeatCount nullable:true, validator:{ Integer rNum, FutureMessage msg ->
			if (rNum == SimpleTrigger.REPEAT_INDEFINITELY && !msg.endDate) {
				["unboundedNeedsEndDate"]
			}
		}
		endDate nullable:true, validator:{ DateTime end, FutureMessage msg ->
			if (end && end.isBefore(msg.startDate)) {
				["endBeforeStart"]
			}
		}
    }
    static mapping = {
    	whenCreated type:PersistentDateTime
    	startDate type:PersistentDateTime
		endDate type:PersistentDateTime
    }

    // Events
    // ------

    def afterLoad() {
    	this.trigger = quartzScheduler.getTrigger(this.triggerKey)
    }

    // Methods
    // -------

    Result cancel() {
        this.isDone = true
        futureMessageService.unschedule(this)
            .logFail("FutureMessage.cancel")
    }

    // Helper Methods
    // --------------

    protected SimpleScheduleBuilder getScheduleBuilder() {
        SimpleScheduleBuilder builder = SimpleScheduleBuilder.simpleSchedule()
        builder.withIntervalInMilliseconds(this.repeatIntervalInMillis)
        !this.getIsRepeating() ? builder :
            (this.getWillEndOnDate() ? builder.repeatForever() :
                builder.withRepeatCount(this.repeatCount))
    }
    protected TriggerKey getTriggerKey() {
        String recordId = this.record?.id?.toString()
        recordId ? TriggerKey.triggerKey(this.key, recordId) : null
    }
    protected boolean getShouldReschedule() {
    	["repeatIntervalInMillis", "repeatCount", "endDate"].any(this.&isDirty)
    }

    // Property Access
    // ---------------

    OutgoingMessage toOutgoingMessage() {
        OutgoingMessage msg = new OutgoingMessage(message:this.message)
        // set type
        msg.type = this.type?.toRecordItemType()
        // set recipients (manual flush)
        FutureMessage.withSession { Session session ->
            session.flushMode = FlushMode.MANUAL
            try {
                ContactTag tag = ContactTag.findByRecord(this.record)
                if (tag) { msg.tags << tag  }
                else {
                    Contact contact = Contact.findByRecord(this.record)
                    if (contact) { msg.contacts << contact }
                }
            }
            finally { session.flushMode = FlushMode.AUTO }
        }
        msg
    }

    DateTime getNextFireDate() {
    	this.trigger ? new DateTime(this.trigger.nextFireTime) : null
    }
    Integer getTimesTriggered() {
    	(this.trigger instanceof SimpleTrigger) ? this.trigger.getTimesTriggered() : null
    }
    boolean getWillEndOnDate() {
    	!!this.endDate
    }
    boolean getIsRepeating() {
    	this.repeatCount || this.endDate
    }
    boolean getIsReallyDone() {
    	!this.trigger || !this.trigger.mayFireAgain()
    }

    long getRepeatIntervalInDays() {
        TimeUnit.MILLISECONDS.toDays(this.repeatIntervalInMillis)
    }
    void setRepeatIntervalInDays(long numDays) {
    	this.repeatIntervalInMillis = TimeUnit.DAYS.toMillis(numDays)
    }
}
