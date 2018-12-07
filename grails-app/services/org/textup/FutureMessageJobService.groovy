package org.textup

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import java.util.concurrent.Future
import org.quartz.*
import org.textup.job.*
import org.textup.util.*
import org.textup.validator.*
import org.textup.type.*

@GrailsTypeChecked
@Transactional
class FutureMessageJobService {

    AuthService authService
    NotificationService notificationService
    OutgoingMessageService outgoingMessageService
    ResultFactory resultFactory
    Scheduler quartzScheduler
    SocketService socketService
    ThreadService threadService

    @Transactional(readOnly=true)
    Result<Void> schedule(FutureMessage fMsg) {
        try {
            TriggerKey trigKey = fMsg.triggerKey
            Trigger trig = quartzScheduler.getTrigger(trigKey)
            TriggerBuilder builder = trig ? trig.triggerBuilder : TriggerBuilder.newTrigger()
            Trigger newTrig = buildTrigger(trigKey, builder, fMsg)
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

    ResultGroup<FutureMessage> cancelAll(Collection<FutureMessage> fMsgs) {
        ResultGroup<FutureMessage> resGroup = new ResultGroup<>()
        fMsgs?.each { FutureMessage fMsg -> resGroup << cancel(fMsg) }
        resGroup
    }
    protected Result<FutureMessage> cancel(FutureMessage fMsg) {
        unschedule(fMsg)
            .logFail("FutureMessageJobService.cancel")
            .then {
                fMsg.setIsDone(true)
                if (fMsg.save()) {
                    resultFactory.success(fMsg)
                }
                else { resultFactory.failWithValidationErrors(fMsg.errors) }
            }
    }

    @RollbackOnResultFailure
    ResultGroup<RecordItem> execute(String futureKey, Long staffId) {
        FutureMessage fMsg = FutureMessage.findByKeyName(futureKey)
        if (!fMsg) {
            return resultFactory.failWithCodeAndStatus(
                "futureMessageService.execute.messageNotFound", ResultStatus.NOT_FOUND).toGroup()
        }
        Result<OutgoingMessage> msgRes = fMsg.tryGetOutgoingMessage()
        if (!msgRes.success) {
            return msgRes.toGroup()
        }
        OutgoingMessage msg = msgRes.payload
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
        // mark all these record items as having been scheduled originally
        resGroup.payload.each { RecordItem item1 -> item1.setWasScheduled(true) }
        // notify staffs is any successes
        if (fMsg.notifySelf && resGroup.anySuccesses) {
            List<BasicNotification> notifs = notificationService
                .build(p1, msg.contacts.recipients, msg.tags.recipients)
            // wait for the future to finish ASYNCHRONOUSLY to avoid blocking this method
            // to allow the record items to save. Otherwise, when the future resolves, the ids
            // will point to non-existent items because we blocked the parent method from saving here
            threadService.submit {
                notifySelf(future, resGroup.payload*.id, fMsg.message, notifs)
                    .logFail("execute: notifying self")
            }
        }
        resGroup
    }

    protected ResultGroup<RecordItem> notifySelf(Future<?> future, Collection<Long> itemIds,
        String message, List<BasicNotification> notifs) {
        // wait for processing and sending to finish. This Future SHOULD be resolve quickly
        // because media processing already took place when creating the future message
        // so in this future, we are simply sending and storing the outgoing message, including
        // the already-processed media referred to in the `media` prop of the  OutgoingMessage
        future.get()

        String instr = IOCUtils.getMessage("futureMessageService.notifyStaff.notification")
        notificationService.send(notifs, true, message, instr)
            .logFail("FutureMessageService.execute: sending notifications")
        int numNotified = notifs.size()

        ResultGroup<RecordItem> saveFailiures = new ResultGroup<>()
        boolean didFindAll = true
        Collection<RecordItem> items = RecordItem.getAll(itemIds as Iterable<Serializable>)
        items.each { RecordItem rItem ->
            if (rItem) {
                rItem.numNotified = numNotified
                if (!rItem.save()) {
                    saveFailiures << resultFactory.failWithValidationErrors(rItem.errors)
                }
            }
            else { didFindAll = false }
        }
        if (!didFindAll) { log.error("notifySelf: did not find all items with ids: ${itemIds}") }
        saveFailiures
    }

    @OptimisticLockingRetry
    Result<FutureMessage> markDone(String futureKey) {
        FutureMessage fMsg = FutureMessage.findByKeyName(futureKey)
        if (!fMsg) {
            return resultFactory.failWithCodeAndStatus("futureMessageService.markDone.messageNotFound",
                ResultStatus.NOT_FOUND, [futureKey])
        }
        fMsg.setIsDone(true)
        if (fMsg.save()) {
            socketService.sendFutureMessages([fMsg]) // socketService will refresh trigger
            resultFactory.success(fMsg)
        }
        else { resultFactory.failWithValidationErrors(fMsg.errors) }
    }

    // Helpers
    // -------

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
}
