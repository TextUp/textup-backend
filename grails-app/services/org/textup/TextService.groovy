package org.textup

import com.twilio.exception.ApiException
import com.twilio.rest.api.v2010.account.Message
import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import org.textup.type.*
import org.textup.validator.*
import org.textup.util.*

@GrailsTypeChecked
@Transactional
class TextService {

	ResultFactory resultFactory

    Result<TempRecordReceipt> send(BasePhoneNumber fromNum, List<? extends BasePhoneNumber> toNums,
        String message, Collection<URI> mediaUrls = []) {

        ResultGroup<Message> failResults = new ResultGroup<>()
        Result<Message> res
        for (toNum in toNums) {
            res = this.tryText(fromNum, toNum, message, mediaUrls)
            //record receipt and return on first success
            if (res.success) {
                Message tMessage = res.payload
                TempRecordReceipt receipt = new TempRecordReceipt(apiId:tMessage.sid,
                    numSegments: TypeConversionUtils.to(Integer, tMessage.numSegments))
                receipt.contactNumber = toNum
                if (receipt.validate()) {
                    return resultFactory.success(receipt)
                }
                else {
                    return resultFactory.failWithValidationErrors(receipt.errors)
                }
            }
            else { failResults << res }
        }
        if (failResults.isEmpty) {
            resultFactory.failWithCodeAndStatus("textService.text.noNumbers",
                ResultStatus.UNPROCESSABLE_ENTITY, null)
        }
        else { resultFactory.failWithGroup(failResults) }
	}

    protected Result<Message> tryText(BasePhoneNumber fromNum, BasePhoneNumber toNum,
        String message, Collection<URI> mediaUrls) {

        String callback = IOCUtils.getWebhookLink(handle: Constants.CALLBACK_STATUS)
        try {
            Message msg1 = Message
                .creator(toNum.toApiPhoneNumber(), fromNum.toApiPhoneNumber(), message)
                .setStatusCallback(callback)
                .setMediaUrl(new ArrayList<URI>(mediaUrls))
                .create()
            resultFactory.success(msg1)
        }
        catch (Throwable e) {
            log.error("TextService.tryText: ${e.class}, ${e.message}")
            // if an ApiException from Twilio, then would be a validation error
            Result res = resultFactory.failWithThrowable(e)
            if (e instanceof ApiException) {
                res.status = ResultStatus.UNPROCESSABLE_ENTITY
            }
            res
        }
    }
}
