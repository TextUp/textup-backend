package org.textup

import com.twilio.sdk.resource.factory.MessageFactory
import com.twilio.sdk.resource.instance.Message
import com.twilio.sdk.TwilioRestException
import grails.transaction.Transactional
import org.apache.http.message.BasicNameValuePair
import org.hibernate.FlushMode
import org.textup.rest.TwimlBuilder
import static org.springframework.http.HttpStatus.*

@Transactional
class TextService {

	def resultFactory
    def twilioService

    Result<RecordItemReceipt> send(PhoneNumber fromNum, List<PhoneNumber> toNums, String message) {
        Result<Message> res
        for (toNum in toNums) {
            res = this.tryText(text, toNum, from)
            //record receipt and return on first success
            if (res.success) {
                Message m1 = res.payload
                RecordItemReceipt receipt = new RecordItemReceipt(apiId:m1.sid)
                receipt.receivedBy = toNum
                if (receipt.save()) {
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
        return resultFactory.failWithMessage(UNPROCESSABLE_ENTITY, "textService.text.allFailed")
	}

    protected Result<Message> tryText(PhoneNumber fromNum, PhoneNumber toNum, String message) {
        try {
            MessageFactory messageFactory = twilioService.account.messageFactory
            def l = [Body:message, To:toNum.e164PhoneNumber, From:fromNum.e164PhoneNumber]
            def params = l.collect { k, v -> new BasicNameValuePair(k, v) }
            Message m = messageFactory.create(params)
            resultFactory.success(m)
        }
        catch (TwilioRestException e) {
            log.error("TextService.tryText: ${e.message}")
            return resultFactory.failWithThrowable(e)
        }
    }
}
