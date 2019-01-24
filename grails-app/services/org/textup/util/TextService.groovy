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

@GrailsTypeChecked // TODO
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
                            rpt1.numSegments = msgRes.numSegments
                            DomainUtils.tryValidate(rpt1)
                        }
                }
            }
        if (resGroup.anySuccesses) {
            IOCUtils.resultFactory.success(resGroup.payload[0])
        }
        else {
            if (resGroup.isEmpty) {
                IOCUtils.resultFactory.failWithCodeAndStatus("textService.text.noNumbers",
                    ResultStatus.UNPROCESSABLE_ENTITY)
            }
            else { IOCUtils.resultFactory.failWithGroup(resGroup) }
        }
	}

    // Helpers
    // -------

    protected static class Outcome {
        String sid
        Integer numSegments
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
                numSegments: TypeUtils.to(Integer, msg1.numSegments))
            IOCUtils.resultFactory.success(msgRes)
        }
        catch (Throwable e) {
            log.error("tryText: ${e.class}, ${e.message}")
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
