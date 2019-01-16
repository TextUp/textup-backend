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

    NotificationService notificationService
    OutgoingMessageService outgoingMessageService
    OutgoingNotificationService outgoingNotificationService
    SocketService socketService
    ThreadService threadService

    @Transactional(readOnly = true)
    Result<Void> trySchedule(FutureMessage fMsg) {
        // TODO check to see if calling save before messes up the isNew check
        if (!DomainUtils.isNew(fMsg) && !fMsg.shouldReschedule) {
            return IOCUtils.resultFactory.success()
        }
        try {
            QuartzUtils.tryBuildTrigger(fMsg)
                .then { Tuple<Trigger, Trigger> tup1 ->
                    Tuple.split(tup1) { Trigger newTrigger, Trigger oldTrigger ->
                        Date nextFire = oldTrigger ?
                            IOCUtils.quartzScheduler.rescheduleJob(fMsg.triggerKey, newTrigger) :
                            IOCUtils.quartzScheduler.scheduleJob(newTrigger)
                        // rescheduleJob can return null if unsuccessful
                        if (nextFire) {
                            IOCUtils.resultFactory.success()
                        }
                        else {
                            IOCUtils.resultFactory.failWithCodeAndStatus(
                                "futureMessageService.schedule.unspecifiedError",
                                ResultStatus.INTERNAL_SERVER_ERROR)
                        }
                    }
                }
        }
        catch (Throwable e) { IOCUtils.resultFactory.failWithThrowable(e) }
    }

    @Transactional(readOnly = true)
    Result<Void> unschedule(FutureMessage fMsg) {
        try {
            if (!IOCUtils.quartzScheduler.unscheduleJob(fMsg.triggerKey)) {
                log.debug("unschedule: tried to unschedule nonexistent trigger with key ${fMsg.triggerKey} for message with id ${fMsg.id}")
            }
            IOCUtils.resultFactory.success()
        }
        catch (Throwable e) { IOCUtils.resultFactory.failWithThrowable(e) }
    }

    ResultGroup<FutureMessage> cancelAll(Collection<FutureMessage> fMsgs) {
        ResultGroup<FutureMessage> resGroup = new ResultGroup<>()
        fMsgs?.each { FutureMessage fMsg -> resGroup << cancel(fMsg) }
        resGroup
    }

    @RollbackOnResultFailure
    Result<Void> execute(String futureKey, Long staffId) {
        FutureMessages.mustFindForKey(futureKey)
            .then { FutureMessage fMsg -> Staffs.mustFindForId(staffId) }
            .then { FutureMessage fMsg, Staff s1 ->
                TempRecordItem.tryCreate(fMsg.message, fMsg.media, null).curry(fMsg, s1)
            }
            .then { FutureMessage fMsg, Staff s1, TempRecordItem temp1 ->
                Collection<PhoneRecord> pRecs = PhoneRecords
                    .buildActiveForRecordIds([fMsg.record.id])
                    .list()
                int max = ValidationUtils.MAX_NUM_TEXT_RECIPIENTS
                Recipients.tryCreate(pRecs, fMsg.language, max).curry(fMsg, s1, temp1)
            }
            .then { FutureMessage fMsg, Staff s1, TempRecordItem temp1, Recipients r1 ->
                RecordItemType rType = fMsg.type.toRecordItemType()
                outgoingMessageService.tryStart(rType, r1, temp1, s1.toAuthor()).curry(fMsg)
            }
            .then { FutureMessage fMsg, Tuple<List<RecordItem>, Future<?>> processed ->
                Tuple.split(processed) { List<RecordItem> rItems, Future<?> fut1 ->
                    rItems.each { RecordItem item1 -> item1.wasScheduled = true }
                    tryNotifySelf(fMsg, fut1, rItems)
                    DomainUtils.trySaveAll(rItems)
                }
            }
    }

    @OptimisticLockingRetry
    Result<FutureMessage> markDone(String futureKey) {
        FutureMessages.mustFindForKey(futureKey)
            .then { FutureMessage fMsg ->
                fMsg.isDone = true
                socketService.sendFutureMessages([fMsg]) // socketService will refresh trigger
                DomainUtils.trySave(fMsg)
            }
    }

    // Helpers
    // -------

    protected Result<FutureMessage> cancel(FutureMessage fMsg) {
        unschedule(fMsg)
            .logFail("cancel")
            .then {
                fMsg.isDone = true
                DomainUtils.trySave(fMsg)
            }
    }

    // TODO finish when notifications revamped
    protected void tryNotifySelf(FutureMessage fMsg, Future<?> future, Collection<RecordItem> rItems) {
        if (fMsg.notifySelf) {
            notificationService
                .build(rItems)
                .logFail("execute: building notify self")
                .then { List<OutgoingNotification> notifs ->
                    // wait for the future to finish ASYNCHRONOUSLY to avoid blocking this method
                    // to allow the record items to save. Otherwise, when the future resolves, the ids
                    // will point to non-existent items because we blocked the parent method from saving here
                    threadService.submit {
                        notifySelf(future, rItems*.id, fMsg.message, notifs)
                            .logFail("execute: notifying self")
                    }
                }
        }
    }

    // TODO finish when notifications revamped
    protected ResultGroup<RecordItem> notifySelf(Future<?> future, Collection<Long> itemIds,
        String message, List<OutgoingNotification> notifs) {
        // wait for processing and sending to finish. This Future SHOULD be resolve quickly
        // because media processing already took place when creating the future message
        // so in this future, we are simply sending and storing the outgoing message, including
        // the already-processed media referred to in the `media` prop of the  OutgoingMessage
        future.get()
        outgoingNotificationService.send(notifs, true, message)
            .logFail("FutureMessageService.execute: sending notifications")
        int numNotified = notifs.size()
        ResultGroup<RecordItem> saveFailiures = new ResultGroup<>()
        boolean didFindAll = true
        Collection<RecordItem> items = RecordItem.getAll(itemIds as Iterable<Serializable>)
        items.each { RecordItem rItem ->
            if (rItem) {
                rItem.numNotified = numNotified
                if (!rItem.save()) {
                    saveFailiures << IOCUtils.resultFactory.failWithValidationErrors(rItem.errors)
                }
            }
            else { didFindAll = false }
        }
        if (!didFindAll) {
            log.error("notifySelf: did not find all items with ids: ${itemIds}")
        }
        saveFailiures
    }
}
