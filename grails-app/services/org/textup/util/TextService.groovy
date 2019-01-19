package org.textup.util

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
        String message, String customAccountId, Collection<URI> mediaUrls = []) {

        ResultGroup<TextService.Outcome> failResults = new ResultGroup<>()
        Result<TextService.Outcome> res
        for (toNum in toNums) {
            if (toNum) {
                res = tryText(fromNum, toNum, message, customAccountId, mediaUrls)
                //record receipt and return on first success
                if (res.success) {
                    TextService.Outcome msgRes = res.payload
                    TempRecordReceipt receipt = new TempRecordReceipt(apiId:msgRes.sid,
                        numSegments: msgRes.numSegments)
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
        }
        if (failResults.isEmpty) {
            IOCUtils.resultFactory.failWithCodeAndStatus("textService.text.noNumbers",
                ResultStatus.UNPROCESSABLE_ENTITY, null)
        }
        else { IOCUtils.resultFactory.failWithGroup(failResults) }
	}

    protected Result<TextService.Outcome> tryText(BasePhoneNumber fromNum, BasePhoneNumber toNum,
        String message, String customAccountId, Collection<URI> mediaUrls) {

        String callback = IOCUtils.getHandleLink(CallbackUtils.STATUS)
        try {
            Message msg1 = messageCreator(fromNum, toNum, message, customAccountId)
                .setStatusCallback(callback)
                .setMediaUrl(new ArrayList<URI>(mediaUrls))
                .create()
            TextService.Outcome msgRes = new TextService.Outcome(sid: msg1.sid,
                numSegments: TypeConversionUtils.to(Integer, msg1.numSegments))
            IOCUtils.resultFactory.success(msgRes)
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

    protected static class Outcome {
        String sid
        Integer numSegments
    }
}
