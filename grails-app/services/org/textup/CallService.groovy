package org.textup

import com.twilio.sdk.resource.factory.CallFactory
import com.twilio.sdk.resource.instance.Call
import com.twilio.sdk.TwilioRestClient
import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import org.codehaus.groovy.grails.web.mapping.LinkGenerator
import org.hibernate.FlushMode
import org.textup.validator.BasePhoneNumber
import org.textup.validator.PhoneNumber
import org.textup.validator.TempRecordReceipt
import static org.springframework.http.HttpStatus.*

@GrailsTypeChecked
@Transactional
class CallService {

    LinkGenerator grailsLinkGenerator
    ResultFactory resultFactory
    TwilioRestClient twilioService

    Result<TempRecordReceipt> start(PhoneNumber fromNum, BasePhoneNumber toNum, Map afterPickup) {
        String afterLink = grailsLinkGenerator.link(namespace:"v1", resource:"publicRecord",
                action:"save", absolute:true, params:afterPickup),
            callback = grailsLinkGenerator.link(namespace:"v1", resource:"publicRecord",
                action:"save", absolute:true, params:[handle:Constants.CALLBACK_STATUS])
        this.doCall(fromNum, toNum, afterLink, callback)
    }
    Result<TempRecordReceipt> start(PhoneNumber fromNum,
        List<? extends BasePhoneNumber> toNums, Map afterPickup) {
        if (toNums?.size() > 1) { // if multiple numbers, we want to add in retry logic
            List<String> remaining = toNums[1..-1]
                .collect { BasePhoneNumber pNum -> pNum.e164PhoneNumber }
            Map callbackParams = [handle:Constants.CALLBACK_STATUS, remaining:remaining,
                afterPickup:Helpers.toJsonString(afterPickup)]
            String afterLink = grailsLinkGenerator.link(namespace:"v1", resource:"publicRecord",
                    action:"save", absolute:true, params:afterPickup),
                callback = grailsLinkGenerator.link(namespace:"v1", resource:"publicRecord",
                    action:"save", absolute:true, params:callbackParams)
            this.doCall(fromNum, toNums[0], afterLink, callback)
        }
        else { this.start(fromNum, toNums ? toNums[0] : null, afterPickup) }
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
        }) as Result<TempRecordReceipt>
    }

    protected Result<TempRecordReceipt> doCall(PhoneNumber fromNum, BasePhoneNumber toNum,
        String afterLink, String callback) {
        if (!fromNum || !toNum) {
            resultFactory.failWithMessageAndStatus(UNPROCESSABLE_ENTITY,
                "callService.doCall.missingInfo")
        }
        try {
            CallFactory cFactory = twilioService.account.callFactory
            Call call = cFactory.create(To:toNum.e164PhoneNumber, From:fromNum.e164PhoneNumber,
                Url:afterLink, StatusCallback:callback)
            TempRecordReceipt receipt = new TempRecordReceipt(apiId:call.sid)
            receipt.receivedBy = toNum
            if (receipt.validate()) {
                resultFactory.success(receipt)
            }
            else { resultFactory.failWithValidationErrors(receipt.errors) }
        }
        catch (Throwable e) {
            log.error("CallService.doCall: ${e.message}")
            resultFactory.failWithThrowable(e)
        }
    }
}
