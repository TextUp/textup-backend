package org.textup

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import java.util.concurrent.Future
import org.textup.media.*
import org.textup.type.*
import org.textup.validator.*
import org.textup.validator.action.*

@GrailsTypeChecked
@Transactional
class MediaService {

    ResultFactory resultFactory
    StorageService storageService
    ThreadService threadService

    // Handling media actions
    // ----------------------

    boolean hasMediaActions(Map body) { !!body?.doMediaActions }
    protected Object getMediaActions(Map body) { body?.doMediaActions }

    Result<Tuple<WithMedia, Future<Result<MediaInfo>>>> tryProcess(WithMedia withMedia, Map body) {
        if (!hasMediaActions(body)) {
            return resultFactory.success(withMedia, Helpers.noOpFuture(withMedia.media))
        }
        tryProcess(withMedia.media ?: new MediaInfo(), body)
            .then { Tuple<MediaInfo, Future<?>> processed ->
                withMedia.media = processed.first
                resultFactory.success(processed)
            }
    }

    Result<Tuple<MediaInfo, Future<Result<MediaInfo>>>> tryProcess(MediaInfo mInfo, Map body) {
        if (!hasMediaActions(body)) {
            return resultFactory.success(mInfo, Helpers.noOpFuture(mInfo))
        }
        ResultGroup<UploadItem> uploadGroup = handleActions(mInfo, body)
            .logFail("MediaService.tryProcess: building initial version")
        List<Tuple<UploadItem, MediaElement>> toProcess = []
        uploadGroup.payload.each { UploadItem initialUpload ->
            MediaElement e1 = new MediaElement()
            e1.addToAlternateVersions(initialUpload.toMediaElementVersion())
            mInfo.addToMediaElements(e1)
            toProcess << Tuple.create(initialUpload, e1)
        }
        if (mInfo.save()) {
            Collection<String> errorMsgs = []
            storageService.uploadAsync(uploadGroup.payload)
                .logFail("MediaService.tryProcess: uploading initial media")
                .failures
                .each { Result<?> failRes -> errorMsgs += failRes.errorMessages }
            Helpers.trySetOnRequest(Constants.REQUEST_UPLOAD_ERRORS, errorMsgs)
                .logFail("MediaService.tryProcess: setting upload errors on request")
            Future<Result<MediaInfo>> fut = threadService.submit {
                tryFinishProcessing(mInfo, toProcess)
            }
            resultFactory.success(mInfo, fut)
        }
        else { resultFactory.failWithValidationErrors(mInfo.errors) }
    }

    // Helpers
    // -------

    protected ResultGroup<UploadItem> handleActions(MediaInfo mInfo, Map body) {
        // validate actions
        ActionContainer ac1 = new ActionContainer(getMediaActions(body))
        List<MediaAction> actions = ac1.validateAndBuildActions(MediaAction)
        if (ac1.hasErrors()) {
            return resultFactory.failWithValidationErrors(ac1.errors).toGroup()
        }
        ResultGroup<UploadItem> outcomes = new ResultGroup<>()
        // process actions
        actions.each { MediaAction a1 ->
            switch (a1) {
                case Constants.MEDIA_ACTION_ADD:
                    outcomes << MediaPostProcessor.buildInitialData(a1.type, a1.byteData)
                    break
                default: // Constants.MEDIA_ACTION_REMOVE
                    mInfo.removeMediaElement(a1.uid)
            }
        }
        outcomes
    }

    protected Result<MediaInfo> tryFinishProcessing(MediaInfo mInfo,
        List<Tuple<UploadItem, MediaElement>> toProcess) {

        ResultGroup<Tuple<List<UploadItem>, MediaElement>> outcomes = new ResultGroup<>()
        toProcess.each { Tuple<UploadItem, MediaElement> processed ->
            outcomes << processElement(processed.first, processed.second)
        }
        outcomes.logFail("MediaService.tryFinishProcessing")

        List<UploadItem> toUpload = []
        outcomes.payload.each { Tuple<List<UploadItem>, MediaElement> processed ->
            toUpload.addAll(processed.first)
        }
        storageService.uploadAsync(toUpload)
            .logFail("MediaService.tryProcess: uploading initial media")

        if (mInfo.save()) {
            resultFactory.success(mInfo)
        }
        else { resultFactory.failWithValidationErrors(mInfo.errors) }
    }
    protected Result<Tuple<List<UploadItem>, MediaElement>> processElement(UploadItem uItem,
        MediaElement e1) {

        MediaPostProcessor
            .process(uItem.type, uItem.data)
            .then { Tuple<UploadItem, List<UploadItem>> processed ->
                e1.sendVersion = processed.first.toMediaElementVersion()
                processed.second.each { UploadItem uItem2 ->
                    e1.addToAlternateVersions(uItem2.toMediaElementVersion())
                }
                if (e1.save()) {
                    List<UploadItem> uItems = new ArrayList<UploadItem>(processed.second)
                    uItems << processed.first
                    resultFactory.success(uItems, e1)
                }
                else { resultFactory.failWithValidationErrors(e1.errors) }
            }
    }
}
