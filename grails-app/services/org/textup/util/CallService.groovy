package org.textup.util

import com.twilio.exception.ApiException
import com.twilio.rest.api.v2010.account.Call
import com.twilio.rest.api.v2010.account.CallCreator
import com.twilio.rest.api.v2010.account.CallUpdater
import com.twilio.type.PhoneNumber as TwilioPhoneNumber
import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*

@GrailsTypeChecked
@Transactional
class CallService {

    static final String RETRY_REMAINING = "remaining"
    static final String RETRY_AFTER_PICKUP = "afterPickup"

    Result<TempRecordReceipt> start(BasePhoneNumber fromNum, List<? extends BasePhoneNumber> toNums,
        Map afterPickup, String customAccountId) {

        ResultGroup<TempRecordReceipt> resGroup = new ResultGroup<>()
        int numRemaining = toNums?.size()
        List<String> toPhoneNums = toNums.collect { BasePhoneNumber n1 -> n1.e164PhoneNumber }
        String afterPickupJson = DataFormatUtils.toJsonString(afterPickup)
        // We start with the first number **that doesn't IMMEDIATELY fail**
        for (int i = numRemaining; i > 0 && !resGroup.anySuccesses; --i) {
            BasePhoneNumber toNum = toNums[numRemaining - i]
            // subtract one because we don't want to include the number we are trying in this
            // iteration in the list of numbers remaining
            List<String> remaining = CollectionUtils.takeRight(toPhoneNums, i - 1)
            // For the first number in the list that succeeds, we append the remaining numbers
            // to try as callback parameter so that the PublicRecordController can retry with
            // the remaining numbers until none are left.
            String callback = buildCallbackUrl(remaining, afterPickupJson)
            resGroup << doCall(fromNum, toNum, afterPickup, callback, customAccountId)
        }

        if (resGroup.anySuccesses) {
            IOCUtils.resultFactory.success(resGroup.payload[0])
        }
        else {
            if (resGroup.isEmpty) {
                IOCUtils.resultFactory.failWithCodeAndStatus(
                    "callService.start.missingInfoOrAllFailed", ResultStatus.UNPROCESSABLE_ENTITY)
            }
            else { IOCUtils.resultFactory.failWithGroup(resGroup) }
        }
    }

    Result<TempRecordReceipt> retry(BasePhoneNumber fromNum, List<? extends BasePhoneNumber> toNums,
        String apiId, Map afterPickup, String customAccountId) {

        start(fromNum, toNums, afterPickup, customAccountId).then { TempRecordReceipt rpt1 ->
            ResultGroup
                .collect(RecordItems.findEveryForApiId(apiId)) { RecordItem item1 ->
                    item1.addReceipt(rpt1)
                    DomainUtils.trySave(item1)
                }
                .toEmptyResult(false)
                .then { IOCUtils.resultFactory.success(rpt1) }
        }
    }

    // interrupt existing call with the following Twiml
    // see https://www.twilio.com/docs/voice/api/call#update-a-call-resource
    Result<Void> interrupt(String callId, Map afterPickup, String customAccountId) {
        try {
            callUpdater(callId, customAccountId)
                .setUrl(IOCUtils.getWebhookLink(afterPickup))
                .setStatusCallback(IOCUtils.getHandleLink(CallbackUtils.STATUS))
                .update()
            IOCUtils.resultFactory.success()
        }
        catch (Throwable e) {
            IOCUtils.resultFactory.failWithThrowable(e, "interrupt")
        }
    }

    // Helpers
    // -------

    protected static class Outcome {
        String sid
    }

    protected String buildCallbackUrl(List<String> remaining, String afterPickupJson) {
        if (remaining && afterPickupJson) {
            IOCUtils.getHandleLink(CallbackUtils.STATUS,
                [
                    (CallService.RETRY_REMAINING): remaining,
                    (CallService.RETRY_AFTER_PICKUP): afterPickupJson
                ])
        }
        // Will not add retry parameters if no numbers remaining to try
        else { IOCUtils.getHandleLink(CallbackUtils.STATUS) }
    }

    protected Result<TempRecordReceipt> doCall(BasePhoneNumber fromNum, BasePhoneNumber toNum,
        Map afterPickup, String callback, String customAccountId) {

        if (!fromNum || !toNum) {
            return IOCUtils.resultFactory.failWithCodeAndStatus("callService.doCall.missingInfo",
                ResultStatus.UNPROCESSABLE_ENTITY, null)
        }
        try {
            CallCreator creator = callCreator(fromNum, toNum, afterPickup, customAccountId)
            CallService.Outcome outcome = executeCall(creator, callback)
            TempRecordReceipt.tryCreate(outcome.sid, toNum)
        }
        catch (Throwable e) {
            Result<?> res = IOCUtils.resultFactory.failWithThrowable(e, "doCall")
            if (e instanceof ApiException) { // Twilio ApiException --> is validation error
                res.status = ResultStatus.UNPROCESSABLE_ENTITY
            }
            res
        }
    }
    protected CallService.Outcome executeCall(CallCreator creator, String callback) {
        Call call = creator
            .setStatusCallback(callback)
            .create()
        new CallService.Outcome(sid: call.sid)
    }

    protected CallCreator callCreator(BasePhoneNumber fromNum, BasePhoneNumber toNum,
        Map afterPickup, String customAccountId) {

        TwilioPhoneNumber apiTo = toNum.toApiPhoneNumber(),
            apiFrom = fromNum.toApiPhoneNumber()
        String afterLink = IOCUtils.getWebhookLink(afterPickup)
        URI afterUri = new URI(afterLink)
        customAccountId ?
            Call.creator(customAccountId, apiTo, apiFrom, afterUri) :
            Call.creator(apiTo, apiFrom, afterUri)
    }

    protected CallUpdater callUpdater(String callId, String customAccountId) {
        customAccountId ? Call.updater(customAccountId, callId) : Call.updater(callId)
    }
}
