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

    String key = UUID.randomUUID().toString()
    Record record

    // In the job, when this message will not be executed again, we manually mark
    // the job as 'done'. We have to rely on this manual bookkeeping because we want
    // to keep our implementation of done-ness agnostic of the Quartz jobstore we
    // choose. If we choose the in-memory jobstore, there isn't a way that we can
    // incorporate the information in the scheduler about the done-ness in our db query
    // However, after retrieving this instance, we can double check to see if we
    // were correct by calling the getIsActuallyDone method
    @RestApiObjectField(
        description    = "Whether all scheduled firings of this message have completed",
        mandatory      = false,
        useForCreation = false,
        defaultValue   = "true")
    boolean isDone = false

    @RestApiObjectField(
        description    = "Date this future message was created",
        allowedType    = "DateTime",
        useForCreation = false)
	DateTime whenCreated = DateTime.now(DateTimeZone.UTC)

    @RestApiObjectField(
        description    = "Date of the first firing. If in the past, the scheduler \
            will fire this message off as soon as possible.",
        allowedType    = "DateTime",
        mandatory      = false,
        useForCreation = true)
	DateTime startDate = DateTime.now(DateTimeZone.UTC).plusDays(1)

    @RestApiObjectField(
        description    = "Date after which to stop repeating",
        allowedType    = "DateTime",
        mandatory      = false,
        useForCreation = true)
    DateTime endDate

    @RestApiObjectField(
        description    = "If the all users that have access to this contact should \
            receive a copy of the message via text when it is sent out.",
        allowedType    = "Boolean",
        defaultValue   = "false",
        mandatory      = false,
        useForCreation = true)
    boolean notifySelf = false

    @RestApiObjectField(
        description    = "How the message should be delivered. One of: CALL or TEXT",
        allowedType    = "String",
        defaultValue   = "TEXT",
        mandatory      = true,
        useForCreation = true)
    FutureMessageType type

    @RestApiObjectField(
        description    = "Contents of the message. Max 320 characters",
        allowedType    = "String",
        mandatory      = true,
        useForCreation = true)
	String message

    @RestApiObjectFields(params=[
        @RestApiObjectField(
            apiFieldName   = "nextFireDate",
            description    = "Next date the message is set to be fired if not done yet",
            allowedType    = "DateTime",
            useForCreation = false)
    ])
	static transients = ["trigger", "futureMessageService", "quartzScheduler"]
    static constraints = {
        record nullable: false
    	message blank:false, nullable:false, maxSize:(Constants.TEXT_LENGTH * 2)
        endDate nullable:true, validator:{ DateTime end, FutureMessage msg ->
            if (end && end.isBefore(msg.startDate)) {
                ["endBeforeStart"]
            }
        }
    }
    static mapping = {
        key column: 'future_message_key'
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

    protected TriggerKey getTriggerKey() {
        String recordId = this.record?.id?.toString()
        recordId ? TriggerKey.triggerKey(this.key, recordId) : null
    }
    protected ScheduleBuilder getScheduleBuilder() {
        null
    }
    protected boolean getShouldReschedule() {
    	["startDate"].any(this.&isDirty)
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
    boolean getIsRepeating() {
        false
    }
    boolean getIsReallyDone() {
    	!this.trigger || !this.trigger.mayFireAgain()
    }
}
