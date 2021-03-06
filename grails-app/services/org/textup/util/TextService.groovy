package org.textup.util

import com.twilio.exception.ApiException
import com.twilio.rest.api.v2010.account.Message
import com.twilio.rest.api.v2010.account.MessageCreator
import com.twilio.type.PhoneNumber as TwilioPhoneNumber
import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import org.textup.*
import org.textup.type.*
import org.textup.util.domain.*
import org.textup.validator.*

@GrailsTypeChecked
@Transactional
class TextService {

    Result<TempRecordReceipt> send(BasePhoneNumber fromNum, List<? extends BasePhoneNumber> toNums,
        String message, String customAccountId, Collection<URI> mediaUrls = []) {

        ResultGroup<TempRecordReceipt> resGroup = new ResultGroup<>()
        CollectionUtils.ensureNoNull(toNums)
            .each { BasePhoneNumber toNum ->
                if (resGroup.anySuccesses == false) { // keep trying until first success
                    resGroup << tryText(fromNum, toNum, message, customAccountId, mediaUrls)
                        .then { TextService.Outcome msgRes ->
                            TempRecordReceipt.tryCreate(msgRes.sid, toNum).curry(msgRes)
                        }
                        .then { TextService.Outcome msgRes, TempRecordReceipt rpt1 ->
                            rpt1.numBillable = msgRes.numBillable
                            DomainUtils.tryValidate(rpt1)
                        }
                }
            }
        if (resGroup.anySuccesses) {
            IOCUtils.resultFactory.success(resGroup.payload[0])
        }
        else {
            if (resGroup.isEmpty) {
                IOCUtils.resultFactory.failWithCodeAndStatus("textService.noNumbers",
                    ResultStatus.UNPROCESSABLE_ENTITY)
            }
            else { IOCUtils.resultFactory.failWithGroup(resGroup) }
        }
	}

    // Helpers
    // -------

    protected static class Outcome {
        String sid
        Integer numBillable
    }

    protected Result<TextService.Outcome> tryText(BasePhoneNumber fromNum, BasePhoneNumber toNum,
        String message, String customAccountId, Collection<URI> mediaUrls) {

        try {
            String callback = IOCUtils.getHandleLink(CallbackUtils.STATUS)
            Message msg1 = messageCreator(fromNum, toNum, message, customAccountId)
                .setStatusCallback(callback)
                .setMediaUrl(new ArrayList<URI>(mediaUrls))
                .create()
            TextService.Outcome msgRes = new TextService.Outcome(sid: msg1.sid,
                numBillable: TypeUtils.to(Integer, msg1.numSegments))
            IOCUtils.resultFactory.success(msgRes)
        }
        catch (Throwable e) {
            Result<?> res = IOCUtils.resultFactory.failWithThrowable(e, "tryText")
            e instanceof ApiException ? // Twilio ApiException --> is validation error
                IOCUtils.resultFactory.failWithResultsAndStatus([res], ResultStatus.UNPROCESSABLE_ENTITY) :
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
