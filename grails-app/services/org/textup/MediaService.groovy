package org.textup

import com.twilio.rest.api.v2010.account.message.Media
import grails.compiler.GrailsCompileStatic
import grails.transaction.Transactional
import org.apache.commons.io.IOUtils
import org.apache.http.client.methods.HttpGet
import org.apache.http.HttpResponse
import org.apache.http.HttpStatus as ApacheHttpStatus
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients
import org.textup.type.CallResponse
import org.textup.type.MediaVersion
import org.textup.validator.action.ActionContainer
import org.textup.validator.action.MediaAction
import org.textup.validator.BasePhoneNumber
import org.textup.validator.TempRecordReceipt
import org.textup.validator.UploadItem

@GrailsCompileStatic
@Transactional
class MediaService {

    CallService callService
    ResultFactory resultFactory
    TextService textService

    // Handling media actions
    // ----------------------

    boolean hasMediaActions(Map body) { !!body?.doMediaActions }
    protected Object getMediaActions(Map body) { body?.doMediaActions }

    Result<MediaInfo> handleActions(MediaInfo mInfo, Closure<Void> collectUploadItems, Map body) {
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
                    Result<MediaInfo> res = doAddMedia(mInfo, a1.mimeType, a1.byteData)
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

    protected Result<MediaInfo> doAddMedia(MediaInfo mInfo, String mimeType, byte[] data) {
        createUploads(mimeType, data)
            .then { List<UploadItem> uItems ->
                MediaElement.create(mimeType, uItems)
            }
            .then { MediaElement e1 ->
                mInfo.addToMediaElements(e1)
                resultFactory.success(mInfo)
            }
    }
    protected Result<List<UploadItem>> createUploads(String mimeType, byte[] data) {
        List<UploadItem> toUpload = []
        createSendVersion(mimeType, data)
            .then { UploadItem uItem ->
                toUpload << uItem
                createDisplayVersions(mimeType, data)
            }
            .then { List<UploadItem> displayVersions ->
                toUpload += displayVersions
                resultFactory.success(toUpload)
            }
    }
    protected Result<UploadItem> createSendVersion(String mimeType, byte[] data) {
        UploadItem uItem = new UploadItem(mediaVersion: MediaVersion.SEND, mimeType: mimeType, data: data)
        uItem.tryResizeToWidth(MediaVersion.SEND.maxWidthInPixels)
            .logFail("MediaService.createSendVersion: resizing")
        uItem.tryCompress(MediaVersion.SEND.maxSizeInBytes)
            .logFail("MediaService.createSendVersion: compressing")
        if (uItem.validate()) {
            resultFactory.success(uItem)
        }
        else { resultFactory.failWithValidationErrors(uItem.errors) }
    }
    protected Result<List<UploadItem>> createDisplayVersions(String mimeType, byte[] data) {
        MediaVersion nextVersion = MediaVersion.LARGE
        List<UploadItem> uItems = []
        UploadItem currentItem
        while (currentItem == null && nextVersion != null) {
            currentItem = new UploadItem(mediaVersion: nextVersion, mimeType: mimeType, data: data)
            currentItem.tryResizeToWidth(nextVersion.maxWidthInPixels)
                .logFail("MediaService.createDisplayVersions: resizing for ${nextVersion}")
            currentItem.tryCompress(nextVersion.maxSizeInBytes)
                .logFail("MediaService.createDisplayVersions: compressing for ${nextVersion}")
            if (currentItem.validate()) {
                uItems << currentItem
                nextVersion = currentItem.mediaVersion.next
            }
            else { return resultFactory.failWithValidationErrors(currentItem.errors) }
        }
        resultFactory.success(uItems)
    }

    // Receiving media
    // ---------------

    Result<MediaInfo> buildFromIncomingMedia(Map<String, String> urlToMimeType,
        Closure<Void> collectMediaIds) {

        try {
            MediaInfo mInfo = new MediaInfo()
            List<Result<MediaInfo>> failRes = []
            urlToMimeType.each { String url, String mimeType ->
                CloseableHttpClient client = HttpClients.createDefault()
                client.withCloseable {
                    HttpResponse resp = client.execute(new HttpGet(url))
                    resp.withCloseable {
                        int statusCode = resp.statusLine.statusCode
                        if (statusCode == ApacheHttpStatus.SC_OK) {
                            resp.entity.content.withStream { InputStream stream ->
                                byte[] data = IOUtils.toByteArray(stream)
                                Result<MediaInfo> res = doAddMedia(mInfo, mimeType, data)
                                if (res.success) {
                                    collectMediaIds(extractMediaIdFromUrl(url))
                                }
                                else { failRes << res  }
                            }
                        }
                        else {
                            return resultFactory.failWithCodeAndStatus(
                                "mediaService.buildFromIncomingMedia.couldNotRetrieveMedia",
                                ResultStatus.convert(statusCode),
                                [resp.statusLine.reasonPhrase])
                        }
                    }
                }
            }
            if (failRes) {
                resultFactory.failWithResultsAndStatus(failRes, ResultStatus.INTERNAL_SERVER_ERROR)
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
            this.&deleteMediaHelper.curry(messageId))
        ResultGroup<Boolean> resGroup = new ResultGroup<Boolean>(resList)
        if (resGroup.anyFailures) {
            resultFactory.failWithResultsAndStatus(resGroup.failures, ResultStatus.INTERNAL_SERVER_ERROR)
        }
        else { resultFactory.success() }
    }
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
            sendWithMediaForText(fromNum, toNums, msg1, mInfo) :
            sendWithMediaForCall(fromNum, toNums, callToken, mInfo)
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
