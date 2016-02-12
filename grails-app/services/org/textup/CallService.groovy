package org.textup

import com.twilio.sdk.resource.factory.CallFactory
import com.twilio.sdk.resource.instance.Call
import com.twilio.sdk.TwilioRestClient
import grails.compiler.GrailsCompileStatic
import grails.transaction.Transactional
import org.codehaus.groovy.grails.web.mapping.LinkGenerator
import org.hibernate.FlushMode
import org.textup.validator.PhoneNumber
import org.textup.validator.TempRecordReceipt
import static org.springframework.http.HttpStatus.*

@GrailsCompileStatic
@Transactional
class CallService {

    LinkGenerator grailsLinkGenerator
    ResultFactory resultFactory
    TwilioRestClient twilioService

    Result<TempRecordReceipt> start(PhoneNumber fromNum, PhoneNumber toNum, Map afterPickup) {
        String afterLink = grailsLinkGenerator.link(namespace:"v1", resource:"publicRecord",
                action:"save", absolute:true, params:afterPickup),
            callback = grailsLinkGenerator.link(namespace:"v1", resource:"publicRecord",
                action:"save", absolute:true, params:[handle:Constants.CALLBACK_STATUS])
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
            log.error("CallService.startBridgeCall: ${e.message}")
            resultFactory.failWithThrowable(e)
        }
    }
}
