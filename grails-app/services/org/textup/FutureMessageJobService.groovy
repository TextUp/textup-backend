package org.textup

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import java.util.concurrent.Future
import org.quartz.*
import org.textup.job.*
import org.textup.util.*
import org.textup.validator.*

@GrailsTypeChecked
@Transactional
class FutureMessageJobService {

    AuthService authService
    NotificationService notificationService
    OutgoingMessageService outgoingMessageService
    ResultFactory resultFactory
    Scheduler quartzScheduler
    SocketService socketService

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
        // notify staffs is any successes
        if (fMsg.notifySelf && resGroup.anySuccesses) {
            // wait for processing and sending to finish. This Future SHOULD be resolve quickly
            // because media processing already took place when creating the future message
            // so in this future, we are simply sending and storing the outgoing message, including
            // the already-processed media referred to in the `media` prop of the  OutgoingMessage
            future.get()
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
