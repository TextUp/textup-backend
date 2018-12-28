package org.textup

import com.twilio.exception.ApiException
import com.twilio.rest.api.v2010.account.Message
import com.twilio.rest.api.v2010.account.MessageCreator
import com.twilio.type.PhoneNumber as TwilioPhoneNumber
import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*

@GrailsTypeChecked
@Transactional
class TextService {

    Result<TempRecordReceipt> send(BasePhoneNumber fromNum, List<? extends BasePhoneNumber> toNums,
        String message, Collection<URI> mediaUrls = [], String customAccountId) {

        ResultGroup<Message> failResults = new ResultGroup<>()
        Result<Message> res
        for (toNum in toNums) {
            res = tryText(fromNum, toNum, message, mediaUrls, customAccountId)
            //record receipt and return on first success
            if (res.success) {
                Message tMessage = res.payload
                TempRecordReceipt receipt = new TempRecordReceipt(apiId:tMessage.sid,
                    numSegments: TypeConversionUtils.to(Integer, tMessage.numSegments))
                receipt.contactNumber = toNum
                if (receipt.validate()) {
                    return IOCUtils.resultFactory.success(receipt)
                }
                else {
                    return IOCUtils.resultFactory.failWithValidationErrors(receipt.errors)
                }
            }
            else { failResults << res }
        }
        if (failResults.isEmpty) {
            IOCUtils.resultFactory.failWithCodeAndStatus("textService.text.noNumbers",
                ResultStatus.UNPROCESSABLE_ENTITY, null)
        }
        else { IOCUtils.resultFactory.failWithGroup(failResults) }
	}

    protected Result<Message> tryText(BasePhoneNumber fromNum, BasePhoneNumber toNum,
        String message, Collection<URI> mediaUrls, String customAccountId) {

        String callback = IOCUtils.getWebhookLink(handle: Constants.CALLBACK_STATUS)
        try {
            Message msg1 = messageCreator(fromNum, toNum, message, customAccountId)
                .setStatusCallback(callback)
                .setMediaUrl(new ArrayList<URI>(mediaUrls))
                .create()
            IOCUtils.resultFactory.success(msg1)
        }
        catch (Throwable e) {
            log.error("TextService.tryText: ${e.class}, ${e.message}")
            // if an ApiException from Twilio, then would be a validation error
            Result res = IOCUtils.resultFactory.failWithThrowable(e)
            if (e instanceof ApiException) {
                res.status = ResultStatus.UNPROCESSABLE_ENTITY
            }
            res
        }
    }

    protected MessageCreator messageCreator(BasePhoneNumber fromNum, BasePhoneNumber toNum,
        String message, String customAccountId) {

        TwilioPhoneNumber apiTo = toNum.toApiPhoneNumber(),
            apiFrom = fromNum.toApiPhoneNumber()
        customAccountId ?
            Message.creator(customAccountId, apiTo, apiFrom, message) :
            Message.creator(apiTo, apiFrom, message)
    }
}
