package org.textup.rest

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import java.util.concurrent.TimeUnit
import org.textup.*
import org.textup.annotation.*
import org.textup.cache.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*

@GrailsTypeChecked
@Transactional
class CallbackStatusService {

    CallService callService
    RecordItemReceiptCache receiptCache
    SocketService socketService
    ThreadService threadService

    // Moved creation of new thread to PublicRecordController to avoid self-calls.
    // Aspect advice is not applied on self-calls because this bypasses the proxies Spring AOP
    // relies on. See https://docs.spring.io/spring/docs/3.1.x/spring-framework-reference/html/aop.html#aop-understanding-aop-proxies
    @OptimisticLockingRetry
    void process(TypeMap params) {
        String callId = params.string(TwilioUtils.ID_CALL),
            textId = params.string(TwilioUtils.ID_TEXT)
        if (callId) {
            ReceiptStatus status = ReceiptStatus.translate(params.string(TwilioUtils.STATUS_CALL))
            Integer duration = params.int(TwilioUtils.CALL_DURATION)
            // duration may be 0 if call is ended immediately
            if (status && duration != null) {
                String parentId = params.string(TwilioUtils.ID_PARENT_CALL)
                if (parentId) {
                    PhoneNumber.tryUrlDecode(params.string(CallbackUtils.PARAM_CHILD_CALL_NUMBER))
                        .thenEnd { PhoneNumber childNum ->
                            handleUpdateForChildCall(parentId, callId, childNum, status, duration)
                        }
                }
                else { handleUpdateForParentCall(callId, status, duration, params) }
            }
        }
        else if (textId) {
            ReceiptStatus status = ReceiptStatus.translate(params.string(TwilioUtils.STATUS_TEXT))
            if (status) {
                handleUpdateForText(textId, status)
            }
        }
    }

    // Helpers for three types of entities to update
    // ---------------------------------------------

    // From the statusCallback attribute on the original POST request to the Text resource
    protected void handleUpdateForText(String textId, ReceiptStatus status) {
        updateExistingReceipts(textId, status)
            .logFail("handleUpdateForText")
            .thenEnd { Collection<RecordItemReceiptCacheInfo> infos -> sendAfterDelay(infos) }
    }

    // From statusCallback attribute on the Number verb
    protected void handleUpdateForChildCall(String parentId, String childId,
        PhoneNumber childNumber, ReceiptStatus status, Integer duration) {

        createNewReceipts(parentId, childId, childNumber, status, duration)
            .logFail("handleUpdateForChildCall")
            .thenEnd { Collection<RecordItemReceiptCacheInfo> infos -> sendAfterDelay(infos) }
    }

    // From the statusCallback attribute on the original POST request to the Call resource
    protected void handleUpdateForParentCall(String callId, ReceiptStatus status,
        Integer duration, TypeMap params) {

        updateExistingReceipts(callId, status, duration)
            .logFail("handleUpdateForParentCall")
            .thenEnd { Collection<RecordItemReceiptCacheInfo> infos ->
                // try to retry parent call if failed
                if (status == ReceiptStatus.FAILED) {
                    tryRetryParentCall(callId, params)
                }
                sendAfterDelay(infos)
            }
    }

    // Shared helpers
    // --------------

    protected Result<Collection<RecordItemReceiptCacheInfo>> createNewReceipts(String parentId,
        String childId, PhoneNumber childNumber, ReceiptStatus status, Integer duration) {

        Collection<RecordItemReceiptCacheInfo> infos = receiptCache.findEveryReceiptInfoByApiId(parentId)
        ResultGroup
            .collect(AsyncUtils.getAllIds(RecordItem, infos*.itemId)) { RecordItem rItem1 ->
                RecordItemReceipt.tryCreate(rItem1, childId, status, childNumber)
                    .then { RecordItemReceipt rpt2 ->
                        rpt2.numBillable = duration
                        DomainUtils.trySave(rpt2)
                    }
            }
            .toEmptyResult(false)
            .then { IOCUtils.resultFactory.success(infos) }
    }

    protected Result<Collection<RecordItemReceiptCacheInfo>> updateExistingReceipts(String apiId,
        ReceiptStatus newStatus, Integer newDuration = null) {

        Collection<RecordItemReceiptCacheInfo> infos = receiptCache.findEveryReceiptInfoByApiId(apiId)
        // (1) It's okay if we don't find any receipts for a certain apiId because we aren't interested
        // in recording the status of certain messages such as notification messages we send out
        // to staff members.
        // (2) We assume that all the receipts have the same status, so we only check the status
        // of the first receipt
        if (infos) {
            ReceiptStatus oldStatus = infos[0]?.status
            Integer oldDuration = infos[0]?.numBillable
            if (CallbackUtils.shouldUpdateStatus(oldStatus, newStatus) ||
                CallbackUtils.shouldUpdateDuration(oldDuration, newDuration)) {
                infos = receiptCache.updateReceipts(apiId, infos*.id, newStatus, newDuration)
            }
        }
        IOCUtils.resultFactory.success(infos)
    }

    protected void sendAfterDelay(Collection<RecordItemReceiptCacheInfo> infos) {
        // Use ids and refetch in new thread to avoid LazyInitializationExceptions
        // caused by trying to interact with detached Hibernate objects
        if (infos) {
            // send items after a delay because we need this current transaction to commit before
            // attempting to send the items because, in the JSON marshaller, the receipts
            // sent are the PERSISTENT values. If the receipts in the current transaction haven't
            // saved yet, then we won't be sending any of the latest updates
            threadService.delay(5, TimeUnit.SECONDS) {
                //send items with updated status through socket
                socketService.sendItems(AsyncUtils.getAllIds(RecordItem, infos*.itemId))
            }
        }
    }

    // If multiple phone numbers on a call and the status is failure, then retry the call.
    // See CallService.start for the parameters passed into the status callback
    protected void tryRetryParentCall(String callId, TypeMap params) {
        PhoneNumber.tryUrlDecode(params.string(TwilioUtils.FROM))
            .thenEnd { PhoneNumber fromNum ->
                List<PhoneNumber> toNums = params.phoneNumberList(CallService.RETRY_REMAINING, [])
                if (toNums) {
                    try {
                        String accountId = params.string(TwilioUtils.ID_ACCOUNT)
                        Object afterData = params[CallService.RETRY_AFTER_PICKUP]
                        Map afterPickup = (DataFormatUtils.jsonToObject(afterData) ?: [:]) as Map
                        callService
                            .retry(fromNum, toNums, callId, afterPickup, accountId)
                            .logFail("tryRetryParentCall: ${params}")
                    }
                    catch (Throwable e) {
                        log.error("tryRetryParentCall: ${e.message}")
                        e.printStackTrace()
                    }
                }
            }
    }
}
