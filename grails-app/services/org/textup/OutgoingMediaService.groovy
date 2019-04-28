package org.textup

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import org.textup.annotation.*
import org.textup.rest.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*

@GrailsTypeChecked
@Transactional
class OutgoingMediaService {

    CallService callService
    TextService textService

    Result<List<TempRecordReceipt>> trySend(BasePhoneNumber fromNum,
        List<? extends BasePhoneNumber> toNums, String customAccountId, String msg1 = "",
        MediaInfo mInfo = null, Token callToken = null) {

        ResultGroup<TempRecordReceipt> resGroup = callToken ?
            trySendWithMediaForCall(fromNum, toNums, customAccountId, callToken, mInfo) :
            trySendWithMediaForText(fromNum, toNums, customAccountId, msg1, mInfo)
        resGroup.toResult(false)
    }

    // Helpers
    // -------

    protected ResultGroup<TempRecordReceipt> trySendWithMediaForText(BasePhoneNumber fromNum,
        List<? extends BasePhoneNumber> toNums, String customAccountId, String msg1,
        MediaInfo mInfo = null, Collection<MediaType> typesToFind = null) {

        ResultGroup<TempRecordReceipt> resGroup = new ResultGroup<>()
        // if no media, then just send message as a text
        if (!mInfo || mInfo.isEmpty()) {
            resGroup << textService.send(fromNum, toNums, msg1, customAccountId, [])
        }
        else { // if yes media, then send media in as many batches as needed
            mInfo.eachBatchForTypes(typesToFind) { List<MediaElement> batchSoFar ->
                Collection<URI> mediaUrls = batchSoFar
                    .collect { MediaElement e1 -> e1.sendVersion?.link?.toURI() }
                resGroup << textService.send(fromNum, toNums, msg1, customAccountId, mediaUrls)
            }
        }
        resGroup
    }

    protected ResultGroup<TempRecordReceipt> trySendWithMediaForCall(BasePhoneNumber fromNum,
        List<? extends BasePhoneNumber> toNums, String customAccountId, Token callToken,
        MediaInfo mInfo = null) {

        ResultGroup<TempRecordReceipt> resGroup = new ResultGroup<>()
        // if this call has images, send those via text
        if (mInfo?.getMediaElementsByType(MediaType.IMAGE_TYPES)) {
            resGroup << trySendWithMediaForText(fromNum, toNums, customAccountId, "", mInfo,
                MediaType.IMAGE_TYPES)
        }
        // message is read via robo-voice and any recordings are played over the phone
        Map<String, String> afterPickup = CallTwiml.infoForDirectMessage(callToken.token)
        resGroup << callService.start(fromNum, toNums, afterPickup, customAccountId)
        resGroup
    }
}
