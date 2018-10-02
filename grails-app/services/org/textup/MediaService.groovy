package org.textup

import com.twilio.rest.api.v2010.account.message.Media
import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import org.apache.commons.io.IOUtils
import org.apache.commons.lang3.tuple.Pair
import org.apache.http.client.methods.HttpGet
import org.apache.http.HttpResponse
import org.apache.http.HttpStatus as ApacheHttpStatus
import org.apache.http.impl.client.CloseableHttpClient
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.textup.media.*
import org.textup.type.*
import org.textup.validator.*
import org.textup.validator.action.*

@GrailsTypeChecked
@Transactional
class MediaService {

    CallService callService
    GrailsApplication grailsApplication
    ResultFactory resultFactory
    TextService textService

    // Handling media actions
    // ----------------------

    boolean hasMediaActions(Map body) { !!body?.doMediaActions }
    protected Object getMediaActions(Map body) { body?.doMediaActions }

    Result<MediaInfo> handleActions(MediaInfo mInfo, Closure<Void> collectUploads, Map body) {
        // validate actions
        ActionContainer ac1 = new ActionContainer(getMediaActions(body))
        List<MediaAction> actions = ac1.validateAndBuildActions(MediaAction)
        if (ac1.hasErrors()) {
            return resultFactory.failWithValidationErrors(ac1.errors)
        }
        List<Result<MediaInfo>> failRes = []
        // process actions
        actions.each { MediaAction a1 ->
            switch (a1) {
                case Constants.MEDIA_ACTION_ADD:
                    Result<MediaInfo> res = doAddMedia(mInfo, collectUploads, a1.type, a1.byteData)
                    if (!res.success) { failRes << res }
                    break
                default: // Constants.MEDIA_ACTION_REMOVE
                    mInfo.removeMediaElement(a1.uid)
            }
        }
        if (failRes) {
            resultFactory.failWithResultsAndStatus(failRes, ResultStatus.UNPROCESSABLE_ENTITY)
        }
        else {
            mInfo.save() ? resultFactory.success(mInfo) : resultFactory.failWithValidationErrors(mInfo.errors)
        }
    }

    protected Result<MediaInfo> doAddMedia(MediaInfo mInfo, Closure<Void> collectUploads,
        MediaType type, byte[] data) {
        MediaPostProcessor
            .process(type, data)
            .then { Pair<UploadItem, List<UploadItem>> processed ->
                collectUploads([processed.left]) // called with `addAll` so accepts a collection as argument
                collectUploads(processed.right) // called with `addAll` so accepts a collection as argument
                MediaElement.create(processed.left, processed.right)
            }
            .then { MediaElement e1 ->
                mInfo.addToMediaElements(e1)
                resultFactory.success(mInfo)
            }
    }

    // Receiving media
    // ---------------

    Result<MediaInfo> buildFromIncomingMedia(Map<String, String> urlToMimeType,
        Closure<Void> collectUploads, Closure<Void> collectMediaIds) {

        try {
            MediaInfo mInfo = new MediaInfo()
            ResultGroup<MediaInfo> failGroup = new ResultGroup<>()
            urlToMimeType.each { String url, String mimeType ->
                MediaType type = MediaType.convertMimeType(mimeType)
                if (!type) {
                    failGroup << resultFactory.failWithCodeAndStatus(
                        "mediaService.buildFromIncomingMedia.invalidMimeType",
                        ResultStatus.UNPROCESSABLE_ENTITY,
                        [mimeType])
                    return
                }
                String sid = grailsApplication.flatConfig["textup.apiKeys.twilio.sid"],
                    authToken = grailsApplication.flatConfig["textup.apiKeys.twilio.authToken"]
                CloseableHttpClient client = Helpers.buildBasicAuthHttpClient(sid, authToken)
                client.withCloseable {
                    HttpResponse resp = client.execute(new HttpGet(url))
                    resp.withCloseable {
                        int statusCode = resp.statusLine.statusCode
                        if (statusCode == ApacheHttpStatus.SC_OK) {
                            resp.entity.content.withStream { InputStream stream ->
                                byte[] data = IOUtils.toByteArray(stream)
                                Result<?> res = doAddMedia(mInfo, collectUploads, type, data)
                                if (res.success) {
                                    collectMediaIds(extractMediaIdFromUrl(url))
                                }
                                else { failGroup << res  }
                            }
                        }
                        else {
                            failGroup << resultFactory.failWithCodeAndStatus(
                                "mediaService.buildFromIncomingMedia.couldNotRetrieveMedia",
                                ResultStatus.convert(statusCode),
                                [resp.statusLine.reasonPhrase])
                        }
                    }
                }
            }
            if (failGroup.anyFailures) {
                resultFactory.failWithResultsAndStatus(failGroup.failures, failGroup.failureStatus)
            }
            else {
                mInfo.save() ? resultFactory.success(mInfo) :
                    resultFactory.failWithValidationErrors(mInfo.errors)
            }
        }
        catch (Throwable e) {
            log.error("MediaService.buildFromIncomingMedia throwable: ${e.message}")
            e.printStackTrace()
            resultFactory.failWithThrowable(e)
        }
    }
    protected String extractMediaIdFromUrl(String url) {
        url ? url.substring(url.lastIndexOf("/") + 1) : ""
    }

    Result<Void> deleteMedia(String messageId, Collection<String> mediaIds) {
        List<Result<Boolean>> resList = Helpers.<String>doAsyncInBatches(mediaIds,
            // do not curry to enable mocking during testing
            { String mediaId -> this.deleteMediaHelper(messageId, mediaId) })
        ResultGroup<Boolean> resGroup = new ResultGroup<Boolean>(resList)
        if (resGroup.anyFailures) {
            resultFactory.failWithResultsAndStatus(resGroup.failures, ResultStatus.INTERNAL_SERVER_ERROR)
        }
        else { resultFactory.success() }
    }

    // [UNTESTED] because of limitations in mocking
    protected Result<Boolean> deleteMediaHelper(String messageId, String mediaId) {
        try {
            resultFactory.success(Media.deleter(messageId, mediaId).delete())
        }
        catch (Throwable e) {
            log.error("MediaService.deleteMediaHelper: ${e.message}")
            e.printStackTrace()
            resultFactory.failWithThrowable(e)
        }
    }

    // Sending media
    // -------------

    Result<List<TempRecordReceipt>> sendWithMedia(BasePhoneNumber fromNum,
        List<? extends BasePhoneNumber> toNums, String msg1 = "",
        MediaInfo mInfo = null, Token callToken = null) {
        ResultGroup<TempRecordReceipt> resGroup = callToken ?
            sendWithMediaForCall(fromNum, toNums, callToken, mInfo) :
            sendWithMediaForText(fromNum, toNums, msg1, mInfo)
        if (resGroup.anyFailures) {
            resultFactory.failWithResultsAndStatus(resGroup.failures,
                resGroup.failureStatus)
        }
        else {
            resultFactory.success(resGroup.successes*.payload)
        }
    }

    protected ResultGroup<TempRecordReceipt> sendWithMediaForText(BasePhoneNumber fromNum,
        List<? extends BasePhoneNumber> toNums, String msg1, MediaInfo mInfo = null) {
        ResultGroup<TempRecordReceipt> resGroup = new ResultGroup<>()
        // if no media, then just send message as a text
        if (!mInfo || mInfo.isEmpty()) {
            resGroup << textService.send(fromNum, toNums, msg1)
        }
        else { // if yes media, then send media in as many batches as needed
            mInfo.forEachBatch { List<MediaElement> batchSoFar ->
                resGroup << textService.send(fromNum, toNums, msg1, batchSoFar)
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
