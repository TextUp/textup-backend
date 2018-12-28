package org.textup

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import org.textup.rest.*
import org.textup.type.*
import org.textup.validator.*

@GrailsTypeChecked
@Transactional
class OutgoingMediaService {

    CallService callService
    ResultFactory resultFactory
    TextService textService

    Result<List<TempRecordReceipt>> send(BasePhoneNumber fromNum,
        List<? extends BasePhoneNumber> toNums, String customAccountId, String msg1 = "",
        MediaInfo mInfo = null, Token callToken = null) {

        ResultGroup<TempRecordReceipt> resGroup = callToken ?
            sendWithMediaForCall(fromNum, toNums, customAccountId, callToken, mInfo) :
            sendWithMediaForText(fromNum, toNums, customAccountId, msg1, mInfo)
        if (resGroup.anyFailures) {
            resultFactory.failWithGroup(resGroup)
        }
        else { resultFactory.success(resGroup.successes*.payload) }
    }

    // Helpers
    // -------

    protected ResultGroup<TempRecordReceipt> sendWithMediaForText(BasePhoneNumber fromNum,
        List<? extends BasePhoneNumber> toNums, String customAccountId, String msg1,
        MediaInfo mInfo = null, Collection<MediaType> typesToFind = null) {

        ResultGroup<TempRecordReceipt> resGroup = new ResultGroup<>()
        // if no media, then just send message as a text
        if (!mInfo || mInfo.isEmpty()) {
            resGroup << textService.send(fromNum, toNums, msg1, [], customAccountId)
        }
        else { // if yes media, then send media in as many batches as needed
            mInfo.forEachBatch({ List<MediaElement> batchSoFar ->
                Collection<URI> mediaUrls = batchSoFar
                    .collect { MediaElement e1 -> e1.sendVersion?.link?.toURI() }
                resGroup << textService.send(fromNum, toNums, msg1, mediaUrls, customAccountId)
            }, typesToFind)
        }
        resGroup
    }

    protected ResultGroup<TempRecordReceipt> sendWithMediaForCall(BasePhoneNumber fromNum,
        List<? extends BasePhoneNumber> toNums, String customAccountId, Token callToken,
        MediaInfo mInfo = null) {

        ResultGroup<TempRecordReceipt> resGroup = new ResultGroup<>()
        // if this call has images, send those via text
        if (mInfo?.getMediaElementsByType(MediaType.IMAGE_TYPES)) {
            resGroup << sendWithMediaForText(fromNum, toNums, customAccountId, "", mInfo, MediaType.IMAGE_TYPES)
        }
        // message is read via robo-voice and any recordings are played over the phone
        resGroup << callService
            .start(fromNum, toNums, CallTwiml.infoForDirectMessage(callToken.token), customAccountId)
        resGroup
    }
}
