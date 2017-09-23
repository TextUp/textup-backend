package org.textup

import com.twilio.exception.ApiException
import com.twilio.rest.api.v2010.account.Message
import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import org.codehaus.groovy.grails.web.mapping.LinkGenerator
import org.textup.rest.TwimlBuilder
import org.textup.validator.BasePhoneNumber
import org.textup.validator.TempRecordReceipt

@GrailsTypeChecked
@Transactional
class TextService {

    LinkGenerator grailsLinkGenerator
	ResultFactory resultFactory

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
        }
        // if still not returned, that means that none of the numbers worked
        resultFactory.failWithCodeAndStatus("textService.text.allFailed",
            ResultStatus.UNPROCESSABLE_ENTITY)
	}

    protected Result<Message> tryText(BasePhoneNumber fromNum, BasePhoneNumber toNum,
        String message) {
        String callback = grailsLinkGenerator.link(namespace:"v1", resource:"publicRecord",
                action:"save", absolute:true, params:[handle:Constants.CALLBACK_STATUS])
        try {
            Message m = Message
                .creator(toNum.toApiPhoneNumber(), fromNum.toApiPhoneNumber(), message)
                .setStatusCallback(callback)
                .create()
            resultFactory.success(m)
        }
        catch (ApiException e) {
            log.error("TextService.tryText: ${e.message}")
            resultFactory.failWithThrowable(e)
        }
    }
}
