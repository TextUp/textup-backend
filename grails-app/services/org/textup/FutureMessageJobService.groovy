package org.textup

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import java.util.concurrent.*
import org.quartz.*
import org.textup.annotation.*
import org.textup.job.*
import org.textup.rest.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*

@GrailsTypeChecked
@Transactional
class FutureMessageJobService {

    NotificationService notificationService
    OutgoingMessageService outgoingMessageService
    SocketService socketService
    ThreadService threadService

    Result<Void> trySchedule(boolean isNew, FutureMessage fMsg) {
        if (!isNew && !fMsg.shouldReschedule) {
            return Result.void()
        }
        try {
            QuartzUtils.tryBuildTrigger(fMsg)
                .then { Tuple<Trigger, Trigger> tup1 ->
                    Tuple.split(tup1) { Trigger newTrigger, Trigger oldTrigger = null ->
                        Date nextFire = oldTrigger ?
                            IOCUtils.quartzScheduler.rescheduleJob(fMsg.triggerKey, newTrigger) :
                            IOCUtils.quartzScheduler.scheduleJob(newTrigger)
                        // rescheduleJob can return null if unsuccessful
                        if (nextFire) {
                            Result.void()
                        }
                        else {
                            IOCUtils.resultFactory.failWithCodeAndStatus(
                                "futureMessageJobService.unspecifiedError",
                                ResultStatus.INTERNAL_SERVER_ERROR)
                        }
                    }
                }
        }
        catch (Throwable e) { IOCUtils.resultFactory.failWithThrowable(e) }
    }

    Result<Void> tryUnschedule(FutureMessage fMsg) {
        try {
            if (!IOCUtils.quartzScheduler.unscheduleJob(fMsg.triggerKey)) {
                log.debug("tryUnschedule: missing trigger key ${fMsg.triggerKey} for id ${fMsg.id}")
            }
            Result.void()
        }
        catch (Throwable e) { IOCUtils.resultFactory.failWithThrowable(e) }
    }

    ResultGroup<FutureMessage> cancelAll(Collection<FutureMessage> fMsgs) {
        ResultGroup.collect(fMsgs) { FutureMessage fMsg ->
            tryUnschedule(fMsg).then {
                fMsg.isDone = true
                DomainUtils.trySave(fMsg)
            }
        }
    }

    @RollbackOnResultFailure
    Result<Void> execute(String futureKey, Long staffId) {
        FutureMessages.mustFindForKey(futureKey)
            .then { FutureMessage fMsg -> Staffs.mustFindForId(staffId).curry(fMsg) }
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
                outgoingMessageService.tryStart(rType, r1, temp1, Author.create(s1)).curry(fMsg)
            }
            .then { FutureMessage fMsg, Tuple<List<RecordItem>, Future<?>> processed ->
                Tuple.split(processed) { List<RecordItem> rItems, Future<?> fut1 ->
                    rItems.each { RecordItem item1 -> item1.wasScheduled = true }
                    if (fMsg.notifySelf) {
                        startNotifySelf(rItems, fut1)
                    }
                    DomainUtils.trySaveAll(rItems)
                }
            }
    }

    @OptimisticLockingRetry
    Result<FutureMessage> markDone(String futureKey) {
        FutureMessages.mustFindForKey(futureKey)
            .then { FutureMessage fMsg ->
                fMsg.isDone = true
                socketService.sendFutureMessages([fMsg]) // will refresh trigger
                DomainUtils.trySave(fMsg)
            }
    }

    // Helpers
    // -------

    protected void startNotifySelf(Collection<RecordItem> rItems, Future<?> future) {
        NotificationUtils.tryBuildNotificationGroup(rItems)
            .logFail("startNotifySelf: building")
            .thenEnd { NotificationGroup notifGroup ->
                if (notifGroup.canNotifyAny(NotificationFrequency.IMMEDIATELY)) {
                    // wait for the future to finish ASYNCHRONOUSLY to avoid blocking this method
                    // to allow the record items to save. Otherwise, when the future resolves, the ids
                    // will point to non-existent items because we blocked the parent method from saving here
                    threadService.submit {
                        DehydratedNotificationGroup.tryCreate(notifGroup)
                            .then { DehydratedNotificationGroup dng1 ->
                                finishNotifySelf(dng1, future)
                            }
                            .logFail("startNotifySelf: notifying in new thread")
                    }
                }
            }
    }

    protected Result<?> finishNotifySelf(Rehydratable<NotificationGroup> dng1, Future<?> future) {
        // wait for processing and sending to finish. This Future SHOULD be resolve quickly
        // because media processing already took place when creating the future message
        // so in this future, we are simply sending and storing the outgoing message, including
        // the already-processed media referred to in the `media` prop of the outgoing message
        future.get()
        dng1.tryRehydrate()
            .then { NotificationGroup notifGroup ->
                ResultGroup<RecordItem> resGroup = new ResultGroup<>()
                notifGroup.eachItem { RecordItem rItem1 ->
                    rItem1.numNotified = notifGroup
                        .getNumNotifiedForItem(NotificationFrequency.IMMEDIATELY, rItem1)
                    resGroup << DomainUtils.trySave(rItem1)
                }
                resGroup.toEmptyResult(false).curry(notifGroup)
            }
            .then { NotificationGroup notifGroup ->
                notificationService.send(NotificationFrequency.IMMEDIATELY, notifGroup)
            }
    }
}
