package org.textup

import com.twilio.exception.ApiException
import com.twilio.rest.api.v2010.account.Call
import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import org.codehaus.groovy.grails.web.mapping.LinkGenerator
import org.textup.validator.BasePhoneNumber
import org.textup.validator.PhoneNumber
import org.textup.validator.TempRecordReceipt

@GrailsTypeChecked
@Transactional
class CallService {

    LinkGenerator grailsLinkGenerator
    ResultFactory resultFactory

    Result<TempRecordReceipt> start(PhoneNumber fromNum, BasePhoneNumber toNum, Map afterPickup) {
        String callback = grailsLinkGenerator.link(namespace:"v1", resource:"publicRecord",
            action:"save", absolute:true, params:[handle:Constants.CALLBACK_STATUS])
        this.doCall(fromNum, toNum, afterPickup, callback)
    }
    Result<TempRecordReceipt> start(PhoneNumber fromNum,
        List<? extends BasePhoneNumber> toNums, Map afterPickup) {
        if (!(toNums?.size() > 1)) {
            this.start(fromNum, toNums ? toNums[0] : null, afterPickup)
        }
        // if multiple numbers, we want to add in retry logic
        List<String> toPhoneNums = toNums
            .collect { BasePhoneNumber pNum -> pNum.e164PhoneNumber }
        int numRemaining = toNums.size() - 1
        String afterPickupJson = Helpers.toJsonString(afterPickup)
        for (int i = numRemaining; i >= 0 ; i--) {
            List<String> remaining = Helpers.takeRight(toPhoneNums, i)
            BasePhoneNumber toNum = toNums[numRemaining - i]
            if (!remaining) { // when only no extra numbers remaining
                return start(fromNum, toNum, afterPickup)
            }
            String callback = grailsLinkGenerator.link(namespace:"v1",
                resource:"publicRecord", action:"save", absolute:true,
                params:[handle:Constants.CALLBACK_STATUS,
                    remaining:remaining, afterPickup:afterPickupJson])
            Result<TempRecordReceipt> res = doCall(fromNum, toNum, afterPickup, callback)
            if (res.success) {
                return res
            }
        }
        resultFactory.failWithCodeAndStatus("callService.doCall.missingInfo",
            ResultStatus.UNPROCESSABLE_ENTITY)
    }
    Result<TempRecordReceipt> retry(PhoneNumber fromNum,
        List<? extends BasePhoneNumber> toNums, String apiId, Map afterPickup) {

        this.start(fromNum, toNums, afterPickup).then({ TempRecordReceipt r1 ->
            List<RecordItem> items = RecordItem.findEveryByApiId(apiId)
            RecordItem.findEveryByApiId(apiId).each { RecordItem item1 ->
                item1.addReceipt(r1)
                item1.save()
            }
            resultFactory.success(r1)
        })
    }

    protected Result<TempRecordReceipt> doCall(PhoneNumber fromNum, BasePhoneNumber toNum,
        Map afterPickup, String callback) {
        if (!fromNum || !toNum) {
            resultFactory.failWithCodeAndStatus("callService.doCall.missingInfo",
                ResultStatus.UNPROCESSABLE_ENTITY)
        }
        String afterLink = grailsLinkGenerator.link(namespace:"v1", resource:"publicRecord",
            action:"save", absolute:true, params:afterPickup)
        try {
            Call call = Call
                .creator(toNum.toApiPhoneNumber(), fromNum.toApiPhoneNumber(), new URI(afterLink))
                .setStatusCallback(callback)
                .create()
            TempRecordReceipt receipt = new TempRecordReceipt(apiId:call.sid)
            receipt.receivedBy = toNum
            if (receipt.validate()) {
                resultFactory.success(receipt)
            }
            else { resultFactory.failWithValidationErrors(receipt.errors) }
        }
        catch (ApiException | URISyntaxException e) {
            log.error("CallService.doCall: ${e.message}")
            resultFactory.failWithThrowable(e)
        }
    }
}
