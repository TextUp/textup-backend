package org.textup

import grails.compiler.GrailsTypeChecked
import groovy.transform.EqualsAndHashCode
import java.util.concurrent.TimeUnit
import java.util.UUID
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
@GrailsTypeChecked
class FutureMessage {

	Trigger trigger
    FutureMessageService futureMessageService
    Scheduler quartzScheduler

    boolean isDeleted = false
	DateTime whenCreated = DateTime.now()
	String key = UUID.randomUUID().toString()
	Record record

	DateTime startDate = DateTime.now()
    boolean notifySelf = false
    FutureMessageType type
    long repeatIntervalInMillis = TimeUnit.DAYS.toMillis(1)
	String message

	Integer repeatCount
	DateTime endDate

	static transients = ["trigger", "futureMessageService", "quartzScheduler",
        "repeatIntervalInDays"]
    static constraints = {
    	message blank:false, nullable:false, maxSize:(Constants.TEXT_LENGTH * 2)
    	repeatIntervalInMillis minSize:TimeUnit.DAYS.toMillis(1)
		repeatCount nullable:true, validator:{ Integer rNum, FutureMessage msg ->
			if (rNum == SimpleTrigger.REPEAT_INDEFINITELY && msg.endDate) {
				["unboundedNeedsEndDate"]
			}
		}
		endDate nullable:true, validator:{ DateTime end, FutureMessage msg ->
			if (end && end.isBeforeNow()) {
				["notInFuture"]
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

    def beforeInsert() {
    	futureMessageService.schedule(this)
            .logFail("FutureMessage.beforeInsert")
            .success // return boolean false to cancel if error

        // TODO: what happens if we return boolean false and cancels? an exception? or silent?
    }
    def beforeUpdate() {
    	if (['repeatIntervalInMillis', 'repeatCount', 'endDate'].any(this.&isDirty)) {
    		futureMessageService.schedule(this)
                .logFail("FutureMessage.beforeUpdate")
                .success // return boolean false to cancel if error
    	}
    }
    def afterLoad() {
    	this.trigger = quartzScheduler.getTrigger(this.triggerKey)
    }

    // Static Finders
    // --------------



    // Methods
    // -------

    Result cancel() {
        this.isDeleted = true
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

    // Property Access
    // ---------------

    OutgoingMessage toOutgoingMessage() {
        OutgoingMessage msg = new OutgoingMessage(message:this.message)
        // set type
        msg.type == this.type?.toRecordItemType()
        // set recipients
        ContactTag tag = ContactTag.findByRecord(this.record)
        if (tag) { msg.tags << tag  }
        else {
            Contact contact = Contact.findByRecord(this.record)
            if (contact) { msg.contacts << contact }
        }
        msg
    }

    DateTime getNextFireDate() {
    	this.trigger ? new DateTime(this.trigger.nextFireTime) : null
    }
    Integer getTimesTriggered() {
    	(this.trigger instanceof SimpleTrigger) ? this.trigger.timesTriggered : null
    }
    boolean getWillEndOnDate() {
    	!!this.endDate
    }
    boolean getIsRepeating() {
    	this.repeatCount || this.endDate
    }
    boolean getIsDone() {
    	!this.trigger || !this.trigger.mayFireAgain()
    }

    long getRepeatIntervalInDays() {
        TimeUnit.MILLISECONDS.toDays(this.repeatIntervalInMillis)
    }
    void setRepeatIntervalInDays(long numDays) {
    	this.repeatIntervalInMillis = TimeUnit.DAYS.toMillis(numDays)
    }
    void setTargetIfAllowed(Phone p1, Long contactId, Long tagId) {
        if (contactId) {
            Contact c1 = Contact.get(contactId)
            if (c1.phone == p1) {

            }
        }
        else if (tagId) {

        }
    }
}
