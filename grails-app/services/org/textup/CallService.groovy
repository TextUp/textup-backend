package org.textup

import com.twilio.exception.ApiException
import com.twilio.rest.api.v2010.account.Call
import com.twilio.rest.api.v2010.account.Call.UpdateStatus
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

    Result<TempRecordReceipt> start(BasePhoneNumber fromNum, List<? extends BasePhoneNumber> toNums,
        Map afterPickup, String customAccountId) {

        if (toNums) {
            List<String> toPhoneNums = toNums.collect { BasePhoneNumber n1 -> n1.e164PhoneNumber }
            String afterPickupJson = DataFormatUtils.toJsonString(afterPickup)
            int numRemaining = toNums.size()
            for (int i = numRemaining; i > 0; i--) {
                BasePhoneNumber toNum = toNums[numRemaining - i]
                // subtract one because we don't want to include the number we are trying in this
                // iteration in the list of numbers remaining
                List<String> remaining = CollectionUtils.takeRight(toPhoneNums, i - 1)
                // For the first number in the list that succeeds, we append the remaining numbers
                // to try as callback parameter so that the PublicRecordController can retry with
                // the remaining numbers until none are left.
                String callback = buildCallbackUrl(remaining, afterPickupJson)
                Result<TempRecordReceipt> res =
                    doCall(fromNum, toNum, afterPickup, callback, customAccountId)
                // We start with the first number **that doesn't IMMEDIATELY fail**. If no numbers
                // remaining to try, we have to return
                if (!remaining || res.success) {
                    return res
                }
            }
        }
        IOCUtils.resultFactory.failWithCodeAndStatus("callService.start.missingInfoOrAllFailed",
            ResultStatus.UNPROCESSABLE_ENTITY)
    }

    Result<TempRecordReceipt> retry(BasePhoneNumber fromNum, List<? extends BasePhoneNumber> toNums,
        String apiId, Map afterPickup, String customAccountId) {

        start(fromNum, toNums, afterPickup, customAccountId).then { TempRecordReceipt r1 ->
            RecordItem.findEveryByApiId(apiId)?.each { RecordItem item1 ->
                item1.addReceipt(r1)
                item1.save()
            }
            IOCUtils.resultFactory.success(r1)
        }
    }

    // interrupt existing call with the following Twiml
    // see https://www.twilio.com/docs/voice/api/call#update-a-call-resource
    Result<Void> interrupt(String callId, Map afterPickup, String customAccountId) {
        try {
            callUpdater(callId, customAccountId)
                .setUrl(IOCUtils.getWebhookLink(afterPickup))
                .setStatusCallback(IOCUtils.getWebhookLink(handle: Constants.CALLBACK_STATUS))
                .update()
            IOCUtils.resultFactory.success()
        }
        catch (Throwable e) { IOCUtils.resultFactory.failWithThrowable(e) }
    }

    // use `setStatus` to end calls instead of interrupt and redirecting to Twiml with HangUp
    // because this works for both in-progress and still-ringing calls. Interrupting only
    // works for in-progress calls and will fail for still-ringing calls
    Result<Void> hangUpImmediately(String callId, String customAccountId) {
        try {
            callUpdater(callId, customAccountId)
                // hang up call either ringing or already in progress
                // see https://www.twilio.com/docs/voice/api/call#update-a-call-resource
                .setStatus(UpdateStatus.COMPLETED)
                .setStatusCallback(IOCUtils.getWebhookLink(handle: Constants.CALLBACK_STATUS))
                .update()
            IOCUtils.resultFactory.success()
        }
        catch (Throwable e) { IOCUtils.resultFactory.failWithThrowable(e) }
    }

    // Helpers
    // -------

    protected String buildCallbackUrl(List<String> remaining, String afterPickupJson) {
        if (remaining && afterPickupJson) {
            IOCUtils.getWebhookLink(handle: Constants.CALLBACK_STATUS, remaining: remaining,
                afterPickup: afterPickupJson)
        }
        // Will not add retry parameters if no numbers remaining to try
        else { IOCUtils.getWebhookLink(handle: Constants.CALLBACK_STATUS) }
    }

    protected Result<TempRecordReceipt> doCall(BasePhoneNumber fromNum, BasePhoneNumber toNum,
        Map afterPickup, String callback, String customAccountId) {

        if (!fromNum || !toNum) {
            return IOCUtils.resultFactory.failWithCodeAndStatus("callService.doCall.missingInfo",
                ResultStatus.UNPROCESSABLE_ENTITY, null)
        }
        try {
            CallCreator creator = callCreator(fromNum, toNum, afterPickup, customAccountId)
            CallService.Outcome cOutcome = executeCall(creator, callback)
            TempRecordReceipt receipt = new TempRecordReceipt(apiId: cOutcome.sid)
            receipt.contactNumber = toNum
            if (receipt.validate()) {
                IOCUtils.resultFactory.success(receipt)
            }
            else { IOCUtils.resultFactory.failWithValidationErrors(receipt.errors) }
        }
        catch (Throwable e) {
            log.error("CallService.doCall: ${e.class}, ${e.message}")
            // if an ApiException from Twilio, then would be a validation error
            Result res = IOCUtils.resultFactory.failWithThrowable(e)
            if (e instanceof ApiException) {
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

    protected static class Outcome {
        String sid
    }
}
