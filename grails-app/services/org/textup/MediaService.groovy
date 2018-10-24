package org.textup

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import java.util.concurrent.*
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

    boolean hasMediaActions(Map body) { !!body?.doMediaActions }
    protected Object getMediaActions(Map body) { body?.doMediaActions }

    Result<Tuple<WithMedia, Future<Result<MediaInfo>>>> tryProcess(WithMedia withMedia, Map body,
        boolean isPublic = false) {

        if (!hasMediaActions(body)) {
            return resultFactory.success(withMedia, Helpers.noOpFuture(withMedia.media))
        }
        tryProcess(withMedia.media ?: new MediaInfo(), body, isPublic)
            .then { Tuple<MediaInfo, Future<?>> processed ->
                withMedia.media = processed.first
                resultFactory.success(withMedia, processed.second)
            }
    }

    Result<Tuple<MediaInfo, Future<Result<MediaInfo>>>> tryProcess(MediaInfo mInfo, Map body,
        boolean isPublic = false) {

        if (!hasMediaActions(body)) {
            return resultFactory.success(mInfo, Helpers.noOpFuture(mInfo))
        }
        ResultGroup<UploadItem> uploadGroup = handleActions(mInfo, body)
            .logFail("MediaService.tryProcess: building initial version")
        ResultGroup<?> elementCreationFailures = new ResultGroup<>()
        List<Tuple<UploadItem, Long>> toProcessIds = []
        uploadGroup.payload.each { UploadItem initialUpload ->
            initialUpload.isPublic = isPublic

            MediaElement e1 = new MediaElement()
            e1.addToAlternateVersions(initialUpload.toMediaElementVersion())
            mInfo.addToMediaElements(e1)
            // need to call save first so that these newly-created elements are assigned an id
            if (e1.save()) {
                toProcessIds << Tuple.create(initialUpload, e1.id)
            }
            else { elementCreationFailures << resultFactory.failWithValidationErrors(e1.errors) }
        }
        if (mInfo.save()) {
            Collection<String> errorMsgs = []
            storageService.uploadAsync(uploadGroup.payload)
                .logFail("MediaService.tryProcess: uploading initial media")
                .failures
                .each { Result<?> failRes -> errorMsgs += failRes.errorMessages }
            Helpers.trySetOnRequest(Constants.REQUEST_UPLOAD_ERRORS, errorMsgs)
                .logFail("MediaService.tryProcess: setting upload errors on request")
            Future<Result<MediaInfo>> fut = threadService.delay(5, TimeUnit.SECONDS) {
                tryFinishProcessing(mInfo.id, toProcessIds)
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

    protected Result<MediaInfo> tryFinishProcessing(Long mediaId,
        List<Tuple<UploadItem, Long>> toProcessIds) {

        // step 1: re-fetch all domain objects from ids so that these are attached to this session
        MediaInfo mInfo = MediaInfo.get(mediaId)
        if (!mInfo) {
            return resultFactory.failWithCodeAndStatus("mediaService.tryFinishProcessing.mediaInfoNotFound",
                ResultStatus.NOT_FOUND, [mediaId])
        }
        List<Tuple<UploadItem, MediaElement>> toProcess = rebuildElementsToProcess(toProcessIds)
        // step 2: start processing elements
        ResultGroup<Tuple<List<UploadItem>, MediaElement>> outcomes = new ResultGroup<>()
        toProcess.each { Tuple<UploadItem, MediaElement> processed ->
            outcomes << processElement(processed.first, processed.second)
        }
        outcomes.logFail("MediaService.tryFinishProcessing")
        // step 3: upload all successes
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
    protected List<Tuple<UploadItem, MediaElement>> rebuildElementsToProcess(List<Tuple<UploadItem, Long>> toProcessIds) {
        // step 1: collect ids so that we can fetch all from db in one call
        Iterable<Serializable> elIds = []
        toProcessIds.each { Tuple<UploadItem, Long> processed -> elIds << processed.second }
        // step 2: fetch from ids and build as map for efficient retrieval
        Map<Long, MediaElement> idToObject = Helpers.buildIdToObjectMap(MediaElement.getAll(elIds))
        // step 3: replace ids with objects in passed-in list of tuples
        List<Tuple<UploadItem, MediaElement>> toProcess = []
        toProcessIds.each { Tuple<UploadItem, Long> processed ->
            MediaElement el = idToObject[processed.second]
            if (el) {
                toProcess << Tuple.create(processed.first, el)
            }
            else { log.error("rebuildElementsToProcess: element `${processed.second}` not found") }
        }
        toProcess
    }

    protected Result<Tuple<List<UploadItem>, MediaElement>> processElement(UploadItem initialUpload,
        MediaElement e1) {

        MediaPostProcessor
            .process(initialUpload.type, initialUpload.data)
            .then { Tuple<UploadItem, List<UploadItem>> processed ->
                UploadItem uItem1 = processed.first
                uItem1.isPublic = initialUpload.isPublic
                e1.sendVersion = uItem1.toMediaElementVersion()

                processed.second.each { UploadItem uItem2 ->
                    uItem2.isPublic = initialUpload.isPublic
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
