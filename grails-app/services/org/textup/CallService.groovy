package org.textup

import com.twilio.sdk.resource.factory.CallFactory
import com.twilio.sdk.resource.instance.Call
import grails.transaction.Transactional
import org.hibernate.FlushMode
import static org.springframework.http.HttpStatus.*

@Transactional
class CallService {

    def grailsLinkGenerator
    def resultFactory
    def twilioService

    Result<RecordItemReceipt> start(PhoneNumber fromNum, PhoneNumber toNum, Map afterPickup) {
        Map callParams = [namespace:"v1", resource:"publicRecord",
            action:"save", absolute:true]
        String afterPickup = grailsLinkGenerator.link(callParams +
                [params:afterPickup]),
            callback = grailsLinkGenerator.link(callParams +
                [params:[handle:Constants.CALLBACK_STATUS]])
        try {
            CallFactory cFactory = twilioService.account.callFactory
            Call call = cFactory.create(To:toNum.e164PhoneNumber, From:fromNum.e164PhoneNumber,
                Url:afterPickup, StatusCallback:callback)
            RecordItemReceipt receipt = new RecordItemReceipt(apiId:call.sid)
            receipt.receivedBy = toNum
            if (receipt.save()) {
                resultFactory.success(receipt)
            }
            else { resultFactory.failWithValidationErrors(receipt.errors) }
        }
        catch (Throwable e) {
            log.error("CallService.startBridgeCall: ${e.message}")
            resultFactory.failWithThrowable(e)
        }
    }
}
