package org.textup

import com.twilio.sdk.resource.factory.MessageFactory
import com.twilio.sdk.resource.instance.Message
import com.twilio.sdk.TwilioRestClient
import com.twilio.sdk.TwilioRestException
import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import org.apache.http.message.BasicNameValuePair
import org.apache.http.NameValuePair
import org.codehaus.groovy.grails.web.mapping.LinkGenerator
import org.hibernate.FlushMode
import org.textup.rest.TwimlBuilder
import org.textup.validator.BasePhoneNumber
import org.textup.validator.TempRecordReceipt
import static org.springframework.http.HttpStatus.*

@GrailsTypeChecked
@Transactional
class TextService {

    LinkGenerator grailsLinkGenerator
	ResultFactory resultFactory
    TwilioRestClient twilioService

    Result<TempRecordReceipt> send(BasePhoneNumber fromNum,
        List<? extends BasePhoneNumber> toNums, String message) {
        Result<Message> res
        for (toNum in toNums) {
            res = this.tryText(fromNum, toNum, message)
            //record receipt and return on first success
            if (res.success) {
                TempRecordReceipt receipt = new TempRecordReceipt(apiId:res.payload.sid)
                receipt.receivedBy = toNum
                if (receipt.validate()) {
                    return resultFactory.success(receipt)
                }
                else {
                    return resultFactory.failWithValidationErrors(receipt.errors)
                }
            }
            //also return on server error
            else if (!res.success && res.payload.errorCode > 499) {
                return res
            }
        }
        // if still not returned, that means that none of the numbers worked
        return resultFactory.failWithMessageAndStatus(UNPROCESSABLE_ENTITY,
            "textService.text.allFailed")
	}

    protected Result<Message> tryText(BasePhoneNumber fromNum, BasePhoneNumber toNum,
        String message) {
        String callback = grailsLinkGenerator.link(namespace:"v1", resource:"publicRecord",
                action:"save", absolute:true, params:[handle:Constants.CALLBACK_STATUS])
        try {
            MessageFactory messageFactory = twilioService.account.messageFactory
            def l = [Body:message, To:toNum.e164PhoneNumber, From:fromNum.e164PhoneNumber,
                StatusCallback:callback]
            List<NameValuePair> params = l.collect { k, v ->
                new BasicNameValuePair(k, v) as NameValuePair
            }
            Message m = messageFactory.create(params)
            resultFactory.success(m)
        }
        catch (TwilioRestException e) {
            log.error("TextService.tryText: ${e.message}")
            return resultFactory.failWithThrowable(e)
        }
    }
}
