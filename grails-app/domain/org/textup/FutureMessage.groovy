package org.textup

import grails.compiler.GrailsTypeChecked
import grails.gorm.DetachedCriteria
import groovy.transform.EqualsAndHashCode
import groovy.transform.TypeCheckingMode
import java.util.concurrent.TimeUnit
import java.util.UUID
import org.jadira.usertype.dateandtime.joda.PersistentDateTime
import org.jadira.usertype.dateandtime.joda.PersistentDateTimeZoneAsString
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.quartz.ScheduleBuilder
import org.quartz.Scheduler
import org.quartz.SimpleScheduleBuilder
import org.quartz.Trigger
import org.quartz.TriggerKey
import org.restapidoc.annotation.*
import org.textup.type.FutureMessageType
import org.textup.type.VoiceLanguage
import org.textup.validator.*

@EqualsAndHashCode
@GrailsTypeChecked
class FutureMessage implements ReadOnlyFutureMessage, WithMedia {

    Trigger trigger
    FutureMessageService futureMessageService
    Scheduler quartzScheduler

    String keyName = UUID.randomUUID().toString()
    Record record

    // specify the datetime in UTC when this future message's start time should be adjusted
    // to account for daylight savings change. See `FutureMessageDaylightSavingsJob`
    // for more details about adjustment. If the job has already been adjusted this particular
    // job, then the `hasAdjustedDaylightSavings` will be set to true and this job will
    // not be adjusted again. However, any time the `whenAdjustDaylightSavings` entry is set
    // to a new value, this flag will be reset to false.
    DateTime whenAdjustDaylightSavings
    boolean hasAdjustedDaylightSavings = false
    DateTimeZone daylightSavingsZone

    // In the job, when this message will not be executed again, we manually mark
    // the job as 'done'. We have to rely on this manual bookkeeping because we want
    // to keep our implementation of done-ness agnostic of the Quartz jobstore we
    // choose. If we choose the in-memory jobstore, there isn't a way that we can
    // incorporate the information in the scheduler about the done-ness in our db query
    // However, after retrieving this instance, we can double check to see if we
    // were correct by calling the getIsReallyDone method
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
        description  = "Language to use when speaking during calls. Allowed: \
            CHINESE, ENGLISH, FRENCH, GERMAN, ITALIAN, JAPANESE, KOREAN, PORTUGUESE, RUSSIAN, SPANISH",
        mandatory    = false,
        allowedType  = "String",
        defaultValue = "ENGLISH")
    VoiceLanguage language = VoiceLanguage.ENGLISH

    @RestApiObjectField(
        description    = "Contents of the message. Max 320 characters",
        allowedType    = "String",
        mandatory      = true,
        useForCreation = true)
	String message

    @RestApiObjectField(
        apiFieldName   = "media",
        description    = "Media associated with this scheduled message",
        allowedType    = "MediaInfo",
        useForCreation = false)
    MediaInfo media

    @RestApiObjectFields(params=[
        @RestApiObjectField(
            apiFieldName   = "nextFireDate",
            description    = "Next date the message is set to be fired if not done yet",
            allowedType    = "DateTime",
            useForCreation = false),
        @RestApiObjectField(
            apiFieldName      = "doMediaActions",
            description       = "List of actions to perform related to media assets",
            allowedType       = "List<[mediaAction]>",
            useForCreation    = false,
            presentInResponse = false)
    ])
	static transients = ["trigger", "futureMessageService", "quartzScheduler"]
    static constraints = {
        // removed the constraint the prohibited message from being null because a future message
        // can now have media so outgoing message can have either text only, media only, or both.
        message blank: true, nullable: true, maxSize:(Constants.TEXT_LENGTH * 2)
        media nullable: true, validator: { MediaInfo mInfo, FutureMessage obj ->
            // message must have at least one of text and media
            if ((!mInfo || mInfo.isEmpty()) && !obj.message) { ["noInfo"] }
        }
        endDate nullable:true, validator:{ DateTime end, FutureMessage msg ->
            if (end && end.isBefore(msg.startDate)) {
                ["endBeforeStart"]
            }
        }
        whenAdjustDaylightSavings nullable:true
        daylightSavingsZone nullable:true
    }
    static mapping = {
        whenCreated type:PersistentDateTime
        startDate type:PersistentDateTime
        endDate type:PersistentDateTime
        whenAdjustDaylightSavings type:PersistentDateTime
        daylightSavingsZone type:PersistentDateTimeZoneAsString
    }

    // Events
    // ------

    def afterInsert() {
        refreshTrigger()
    }
    def afterUpdate() {
        refreshTrigger()
    }
    def afterLoad() {
    	refreshTrigger()
    }

    // Static finders
    // --------------

    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    static DetachedCriteria<FutureMessage> buildForRecords(Collection<Record> records) {
        new DetachedCriteria(FutureMessage)
            .build {
                if (records) { "in"("record", records) }
                else { eq("record", null) }
            }
    }

    // Methods
    // -------

    Result<Void> cancel() {
        this.isDone = true
        doUnschedule()
    }

    void checkScheduleDaylightSavingsAdjustment(DateTimeZone zone1) {
        if (this.whenCreated && this.startDate && zone1?.isFixed() == false) {
            DateTime prevChangeDate = null,
                changeDate = new DateTime(zone1.nextTransition(this.whenCreated.getMillis()))
            // stop iterating once we have the daylight savings change point BEFORE the start date
            // and the start time after the start date
            while (changeDate.isBefore(this.startDate)) {
                prevChangeDate = changeDate
                changeDate = new DateTime(zone1.nextTransition(changeDate.plusDays(1).getMillis()))
            }
            // if the prevChangeDate is null, then that means that the startDate is before the
            // next possible change point. That means there's no need to adjust this message
            // OTHERWISE, if the `prevChangeDate` has a non-null value, then we do need to adjust
            // because that means that this date is before the start date (see the while-loop check)
            if (prevChangeDate) {
                DateTime newChangeDate = prevChangeDate.withZone(DateTimeZone.UTC)
                // only update `whenAdjustDaylightSavings` and reset the flag if a different value
                if ( !this.whenAdjustDaylightSavings || newChangeDate != this.whenAdjustDaylightSavings) {
                    this.whenAdjustDaylightSavings = newChangeDate
                    this.hasAdjustedDaylightSavings = false
                    this.daylightSavingsZone = zone1
                }
            }
        }
    }

    // Helper Methods
    // --------------

    protected Result<Void> doUnschedule() {
        futureMessageService.unschedule(this).logFail("FutureMessage.cancel")
    }
    protected void refreshTrigger() {
        this.trigger = quartzScheduler.getTrigger(this.triggerKey)
    }
    protected TriggerKey getTriggerKey() {
        String recordId = this.record?.id?.toString()
        recordId ? TriggerKey.triggerKey(this.keyName, recordId) : null
    }
    protected ScheduleBuilder getScheduleBuilder() {
        SimpleScheduleBuilder.simpleSchedule() // no repeat
    }
    protected boolean getShouldReschedule() {
    	["startDate"].any(this.&isDirty)
    }

    // Property Access
    // ---------------

    OutgoingMessage toOutgoingMessage() {
        Helpers.<OutgoingMessage>doWithoutFlush({
            // step 1: initialize classes
            ContactRecipients cRecip = new ContactRecipients()
            ContactTagRecipients ctRecip = new ContactTagRecipients()
            OutgoingMessage msg = new OutgoingMessage(
                message:this.message,
                language:this.language,
                type:this.type?.toRecordItemType(),
                sharedContacts: new SharedContactRecipients(),
                contacts: cRecip,
                tags: ctRecip)
            // step 2: populate recipients
            ContactTag tag = ContactTag.findByRecord(this.record)
            if (tag) { ctRecip.recipients = [tag] }
            else {
                Contact contact = Contact.findByRecord(this.record)
                if (contact) { cRecip.recipients = [contact] }
            }
            msg
        })
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
