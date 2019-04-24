package org.textup

import grails.compiler.GrailsTypeChecked
import grails.gorm.DetachedCriteria
import groovy.transform.EqualsAndHashCode
import groovy.transform.TypeCheckingMode
import java.util.concurrent.TimeUnit
import org.jadira.usertype.dateandtime.joda.PersistentDateTime
import org.jadira.usertype.dateandtime.joda.PersistentDateTimeZoneAsString
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
class FutureMessage implements ReadOnlyFutureMessage, WithMedia, WithId, CanSave<FutureMessage> {

    // Need to declare id for it to be considered in equality operator
    // see: https://stokito.wordpress.com/2014/12/19/equalsandhashcode-on-grails-domains/
    Long id

    Trigger trigger

    boolean notifySelfOnSend = false
    DateTime endDate
    DateTime startDate = JodaUtils.utcNow().plusDays(1)
    DateTime whenCreated = JodaUtils.utcNow()
    FutureMessageType type
    MediaInfo media
    Record record
    String keyName = UUID.randomUUID().toString()
    String message
    VoiceLanguage language = VoiceLanguage.ENGLISH

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
    boolean isDone = false

	static transients = ["trigger"]
    static mapping = {
        whenCreated type: PersistentDateTime
        startDate type: PersistentDateTime
        endDate type: PersistentDateTime
        whenAdjustDaylightSavings type: PersistentDateTime
        daylightSavingsZone type: PersistentDateTimeZoneAsString
        media fetch: "join", cascade: "save-update"
    }
    static constraints = {
        // removed the constraint the prohibited message from being null because a future message
        // can now have media so outgoing message can have either text only, media only, or both.
        message blank: true, nullable: true, maxSize: (ValidationUtils.TEXT_BODY_LENGTH * 2)
        media nullable: true, validator: { MediaInfo mInfo, FutureMessage obj ->
            // message must have at least one of text and media
            if ((!mInfo || mInfo.isEmpty()) && !obj.message) { ["futureMessage.media.noInfo"] }
        }
        endDate nullable: true, validator: { DateTime end, FutureMessage msg ->
            if (end && end.isBefore(msg.startDate)) {
                ["futureMessage.endDate.endBeforeStart"]
            }
        }
        whenAdjustDaylightSavings nullable: true
        daylightSavingsZone nullable: true
    }

    def afterInsert() { refreshTrigger() }

    def afterUpdate() { refreshTrigger() }

    def afterLoad() { refreshTrigger() }

    // Methods
    // -------

    void checkScheduleDaylightSavingsAdjustment(DateTimeZone zone1) {
        if (whenCreated && startDate && zone1?.isFixed() == false) {
            DateTime prevChangeDate = null,
                changeDate = new DateTime(zone1.nextTransition(whenCreated.getMillis()))
            // stop iterating once we have the daylight savings change point BEFORE the start date
            // and the start time after the start date
            while (changeDate.isBefore(startDate)) {
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
                if ( !whenAdjustDaylightSavings || newChangeDate != whenAdjustDaylightSavings) {
                    whenAdjustDaylightSavings = newChangeDate
                    hasAdjustedDaylightSavings = false
                    daylightSavingsZone = zone1
                }
            }
        }
    }

    // Properties
    // ----------

    @Override
    ReadOnlyMediaInfo getReadOnlyMedia() { media }

    @Override
    ReadOnlyRecord getReadOnlyRecord() { record }

    DateTime getNextFireDate() { trigger ? new DateTime(trigger.nextFireTime) : null }

    boolean getIsRepeating() { false }

    boolean getIsReallyDone() { !trigger || !trigger.mayFireAgain() }

    // Helpers
    // -------

    protected void refreshTrigger() { trigger = IOCUtils.quartzScheduler.getTrigger(triggerKey) }

    protected TriggerKey getTriggerKey() {
        String recordId = record?.id?.toString()
        recordId ? TriggerKey.triggerKey(keyName, recordId) : null
    }

    // no repeat
    protected ScheduleBuilder getScheduleBuilder() { SimpleScheduleBuilder.simpleSchedule() }

    protected boolean getShouldReschedule() { isDirty("startDate") }
}
