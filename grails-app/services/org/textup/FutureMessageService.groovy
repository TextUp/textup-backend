package org.textup

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import java.util.concurrent.Future
import org.joda.time.DateTime
import org.quartz.*
import org.textup.job.FutureMessageJob
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*

@GrailsTypeChecked
@Transactional
class FutureMessageService {

    AuthService authService
    MediaService mediaService
    NotificationService notificationService
    OutgoingMessageService outgoingMessageService
    ResultFactory resultFactory
    Scheduler quartzScheduler
    SocketService socketService

	// Scheduler
	// ---------

    @Transactional(readOnly=true)
	protected Result<Void> doSchedule(FutureMessage fMsg) {
        try {
            TriggerKey trigKey = fMsg.triggerKey
            Trigger trig = quartzScheduler.getTrigger(trigKey)
            TriggerBuilder builder = trig ? trig.triggerBuilder : TriggerBuilder.newTrigger()
            Trigger newTrig = this.buildTrigger(trigKey, builder, fMsg)
            // schedule or reschedule trigger
            Date nextFire = trig ? quartzScheduler.rescheduleJob(trigKey, newTrig) :
                quartzScheduler.scheduleJob(newTrig)
            if (nextFire) { // rescheduleJob can return null if unsuccessful
                resultFactory.success()
            }
            else {
                resultFactory.failWithCodeAndStatus("futureMessageService.schedule.unspecifiedError",
                    ResultStatus.INTERNAL_SERVER_ERROR)
            }
        }
        catch (Throwable e) { resultFactory.failWithThrowable(e) }
    }
    @Transactional(readOnly=true)
    Result<Void> unschedule(FutureMessage fMsg) {
        try {
            if (!quartzScheduler.unscheduleJob(fMsg.triggerKey)) {
                log.debug("FutureMessageService.unschedule: tried to unschedule \
                    nonexistent trigger with key ${fMsg.triggerKey} for \
                    message with id ${fMsg.id}")
            }
            resultFactory.success()
        }
        catch (Throwable e) { resultFactory.failWithThrowable(e) }
    }
    protected Trigger buildTrigger(TriggerKey trigKey, TriggerBuilder builder, FutureMessage fMsg) {
        builder
            .forJob(FutureMessageJob.class.canonicalName)
            .withIdentity(trigKey)
            .startAt(fMsg.startDate?.toDate())
            .usingJobData(Constants.JOB_DATA_FUTURE_MESSAGE_KEY, fMsg.keyName)
            .usingJobData(Constants.JOB_DATA_STAFF_ID, authService.loggedInAndActive?.id)
        if (fMsg.endDate) {
            builder.endAt(fMsg.endDate.toDate())
        }
        ScheduleBuilder sBuilder = fMsg.scheduleBuilder
        if (sBuilder) {
            builder.withSchedule(sBuilder)
        }
        builder.build()
    }

    // Execute
    // -------

    @OptimisticLockingRetry
    Result<FutureMessage> markDone(String futureKey) {
        FutureMessage fMsg = FutureMessage.findByKeyName(futureKey)
        if (!fMsg) {
            return resultFactory.failWithCodeAndStatus("futureMessageService.markDone.messageNotFound",
                ResultStatus.NOT_FOUND, [futureKey])
        }
        fMsg.isDone = true
        if (fMsg.save()) {
            socketService.sendFutureMessages([fMsg]) // socketService will refresh trigger
            resultFactory.success(fMsg)
        }
        else { resultFactory.failWithValidationErrors(fMsg.errors) }
    }

    @RollbackOnResultFailure
    ResultGroup<RecordItem> execute(String futureKey, Long staffId) {
        FutureMessage fMsg = FutureMessage.findByKeyName(futureKey)
        if (!fMsg) {
            return resultFactory.failWithCodeAndStatus(
                "futureMessageService.execute.messageNotFound", ResultStatus.NOT_FOUND).toGroup()
        }
        OutgoingMessage msg = fMsg.toOutgoingMessage()
        Phone[] phones = msg.phones.toArray() as Phone[]
        if (!phones || !phones[0]) {
            return resultFactory.failWithCodeAndStatus(
                "futureMessageService.execute.phoneNotFound", ResultStatus.NOT_FOUND).toGroup()
        }
        Phone p1 = phones[0]
        // Don't need to send through socket here because status callback will send through socket
        // after the messages and processed and sent out
        Tuple<ResultGroup<RecordItem>, Future<?>> processed = outgoingMessageService
            .processMessage(p1, msg, Staff.get(staffId))
        ResultGroup<RecordItem> resGroup = processed.first
        Future<?> future = processed.second
        // notify staffs is any successes
        if (fMsg.notifySelf && resGroup.anySuccesses) {
            future.get() // wait for processing and sending to finish
            String instr = Helpers.getMessage("futureMessageService.notifyStaff.notification")
            List<BasicNotification> notifs = notificationService
                .build(p1, msg.contacts.recipients, msg.tags.recipients)
            notificationService.send(notifs, true, fMsg.message, instr)
                .logFail("FutureMessageService.execute: sending notifications")
            int numNotified = notifs.size()
            resGroup.payload.each { RecordItem rItem -> rItem.numNotified = numNotified }
        }
        resGroup
    }

    // Create
    // ------

    Result<FutureMessage> createForContact(Long cId, Map body, String timezone = null) {
        this.create(Contact.get(cId)?.record, body, timezone)
    }
    Result<FutureMessage> createForSharedContact(Long scId, Map body, String timezone = null) {
        SharedContact sc1 = SharedContact.get(scId)
        if (!sc1) {
            return resultFactory.failWithCodeAndStatus(
                "futureMessageService.create.noRecordOrInsufficientPermissions",
                ResultStatus.UNPROCESSABLE_ENTITY)
        }
        sc1.tryGetRecord().then { Record rec1 -> this.create(rec1, body, timezone) }
    }
    Result<FutureMessage> createForTag(Long ctId, Map body, String timezone = null) {
        this.create(ContactTag.get(ctId)?.record, body, timezone)
    }

    @RollbackOnResultFailure
    protected Result<FutureMessage> create(Record rec, Map body, String timezone = null) {
        if (!rec) {
            return resultFactory.failWithCodeAndStatus(
                "futureMessageService.create.noRecordOrInsufficientPermissions",
                ResultStatus.UNPROCESSABLE_ENTITY)
        }
        Future<?> future
        SimpleFutureMessage fm0 = new SimpleFutureMessage(record: rec, language: rec.language)
        Result<FutureMessage> res = mediaService.tryProcess(fm0, body)
            .then { Tuple<WithMedia, Future<Result<MediaInfo>>> processed ->
                future = processed.second
                setFromBody(fm0, body, timezone, ResultStatus.CREATED)
            }
        if (!res.success && future) { future.cancel(true) }
        res
    }

    // Update
    // ------

    @RollbackOnResultFailure
    Result<FutureMessage> update(Long fId, Map body, String timezone = null) {
        FutureMessage fMsg = FutureMessage.get(fId)
        if (!fMsg) {
            return resultFactory.failWithCodeAndStatus("futureMessageService.update.notFound",
                ResultStatus.NOT_FOUND, [fId])
        }
        // need to set first to ensure that future message validators take into account
        // the media object too
        Future<?> future
        Result<FutureMessage> res = mediaService.tryProcess(fMsg, body)
            .then { Tuple<WithMedia, Future<Result<MediaInfo>>> processed ->
                future = processed.second
                setFromBody(fMsg, body, timezone)
            }
        if (!res.success && future) { future.cancel(true) }
        res
    }

    // Delete
    // ------

    @RollbackOnResultFailure
    Result<Void> delete(Long fId) {
        FutureMessage fMsg = FutureMessage.get(fId)
        if (fMsg) {
            deleteHelper(fMsg)
                .then {
                    if (fMsg.save()) {
                        resultFactory.success()
                    }
                    else { resultFactory.failWithValidationErrors(fMsg.errors) }
                }
        }
        else {
            resultFactory.failWithCodeAndStatus("futureMessageService.delete.notFound",
                ResultStatus.NOT_FOUND, [fId])
        }
    }
    // for mocking during testing
    protected Result<Void> deleteHelper(FutureMessage fMsg) {
        fMsg.cancel()
    }

    // Helper Methods
    // --------------

    protected Result<FutureMessage> setFromBody(FutureMessage fMsg, Map body,
        String timezone = null, ResultStatus status = ResultStatus.OK) {

        fMsg.with {
            if (body.notifySelf != null) notifySelf = body.notifySelf
            if (body.type) {
                type = Helpers.convertEnum(FutureMessageType, body.type)
            }
            if (body.message) message = body.message
            // optional properties
            if (body.startDate) {
                startDate = Helpers.toDateTimeWithZone(body.startDate, timezone)
            }
            if (body.language) {
                language = Helpers.convertEnum(VoiceLanguage, body.language)
            }
            // don't wrap endDate setter in if statement because we want to support nulling
            // endDate by omitting it from the passed-in body
            endDate = Helpers.toDateTimeWithZone(body.endDate, timezone)
        }
        if (fMsg.instanceOf(SimpleFutureMessage)) {
            SimpleFutureMessage sMsg = fMsg as SimpleFutureMessage
            // repeat count is nullable!
            sMsg.repeatCount = Helpers.to(Integer, body.repeatCount)
            if (body.repeatIntervalInDays) {
                sMsg.repeatIntervalInDays = Helpers.to(Integer, body.repeatIntervalInDays)
            }
        }
        // if timezone is provided, determine if we need to schedule a date to adjust to
        // account for daylight savings time
        if (timezone) {
            fMsg.checkScheduleDaylightSavingsAdjustment(Helpers.getZoneFromId(timezone))
        }
        // for some reason, calling save here instantly persists the message
        if (fMsg.validate()) {
            boolean isNew = !fMsg.id // is new if no id yet
            if (isNew || fMsg.shouldReschedule) {
                Result res = doSchedule(fMsg)
                if (!res.success) {
                    return resultFactory.failWithResultsAndStatus([res], res.status)
                }
            }
            // call save finally here to persist the message
            if (fMsg.save()) {
                socketService.sendFutureMessages([fMsg]) // socketService will refresh trigger
                resultFactory.success(fMsg, status)
            }
            else { resultFactory.failWithValidationErrors(fMsg.errors) }
        }
        else { resultFactory.failWithValidationErrors(fMsg.errors) }
    }
}
