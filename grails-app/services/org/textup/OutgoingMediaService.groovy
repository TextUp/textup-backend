package org.textup

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional

@GrailsTypeChecked
@Transactional
class OutgoingMediaService {

    ResultFactory resultFactory

    Result<List<TempRecordReceipt>> send(BasePhoneNumber fromNum,
        List<? extends BasePhoneNumber> toNums, String msg1 = "",
        MediaInfo mInfo = null, Token callToken = null) {
        ResultGroup<TempRecordReceipt> resGroup = callToken ?
            sendWithMediaForCall(fromNum, toNums, callToken, mInfo) :
            sendWithMediaForText(fromNum, toNums, msg1, mInfo)
        if (resGroup.anyFailures) {
            resultFactory.failWithGroup(resGroup)
        }
        else { resultFactory.success(resGroup.successes*.payload) }
    }

    // Helpers
    // -------

    protected ResultGroup<TempRecordReceipt> sendWithMediaForText(BasePhoneNumber fromNum,
        List<? extends BasePhoneNumber> toNums, String msg1, MediaInfo mInfo = null) {
        ResultGroup<TempRecordReceipt> resGroup = new ResultGroup<>()
        // if no media, then just send message as a text
        if (!mInfo || mInfo.isEmpty()) {
            resGroup << textService.send(fromNum, toNums, msg1)
        }
        else { // if yes media, then send media in as many batches as needed
            mInfo.forEachBatch { List<MediaElement> batchSoFar ->
                Collection<URI> mediaUrls = batchSoFar
                    .collect { MediaElement e1 -> e1.sendVersion?.link?.toURI() }
                resGroup << textService.send(fromNum, toNums, msg1, mediaUrls)
            }
        }
        resGroup
    }

    protected ResultGroup<TempRecordReceipt> sendWithMediaForCall(BasePhoneNumber fromNum,
        List<? extends BasePhoneNumber> toNums, Token callToken, MediaInfo mInfo = null) {
        ResultGroup<TempRecordReceipt> resGroup = new ResultGroup<>()
        // if this call has media (currently only images), send only media as a text
        if (mInfo && !mInfo.isEmpty()) {
            resGroup.merge(sendWithMediaForText(fromNum, toNums, "", mInfo))
        }
        resGroup << callService.start(fromNum, toNums, [
            handle: CallResponse.DIRECT_MESSAGE,
            token: callToken.token
        ])
        resGroup
    }
}
