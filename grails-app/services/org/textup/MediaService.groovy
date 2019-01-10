package org.textup

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import java.util.concurrent.*
import org.textup.media.*
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*
import org.textup.validator.action.*

@GrailsTypeChecked
@Transactional
class MediaService {

    ResultFactory resultFactory
    StorageService storageService
    ThreadService threadService

    Result<Tuple<WithMedia, Future<Result<MediaInfo>>>> tryProcess(WithMedia withMedia, Map body,
        boolean isPublic = false) {

        if (!hasActions(body)) {
            return resultFactory.success(withMedia, AsyncUtils.noOpFuture(withMedia.media))
        }
        tryProcess(withMedia.media ?: new MediaInfo(), body, isPublic)
            .then { Tuple<MediaInfo, Future<?>> processed ->
                withMedia.media = processed.first
                resultFactory.success(withMedia, processed.second)
            }
    }

    Result<Tuple<MediaInfo, Future<Result<MediaInfo>>>> tryProcess(MediaInfo mInfo, Map body,
        boolean isPublic = false) {

        if (!hasActions(body)) {
            return resultFactory.success(mInfo, AsyncUtils.noOpFuture(mInfo))
        }
        tryHandleActions(mInfo, body)
            .logFail("tryProcess: building initial version")
            .then { Result<ResultGroup<UploadItem>> uploadGroup ->
                ResultGroup<MediaElement> elGroup = new ResultGroup<>()
                uploadGroup.payload.each { UploadItem initialUpload ->
                    initialUpload.isPublic = isPublic
                    elGroup << MediaElement.create(null, [initialUpload.toMediaElementVersion()])
                }
                elGroup.toResult().curry(uploadGroup.payload, elGroup.payload)
            }
            .then { List<UploadItem> uItems, List<MediaElement> elements ->
                List<Tuple<UploadItem, Long>> toProcessIds = []
                elements.each { MediaElement e1 ->
                    mInfo.addToMediaElements(e1)
                    toProcessIds << Tuple.create(initialUpload, e1.id)
                }
                DomainUtils.trySave(mInfo).curry(uItems, toProcessIds)
            }
            .then { List<UploadItem> uItems, List<Tuple<UploadItem, Long>> pInfo, MediaInfo mInfo ->
                List<String> errors = storageService.uploadAsync(uItems)
                    .logFail("tryProcess: uploading initial media")
                    .errorMessages
                Utils.trySetOnRequest(Constants.REQUEST_UPLOAD_ERRORS, errors)
                    .logFail("tryProcess: setting upload errors on request")
                resultFactory.success(mInfo, threadService.delay(5, TimeUnit.SECONDS) {
                    tryFinishProcessing(mInfo.id, pInfo)
                })
            }
    }

    // Helpers
    // -------

    protected Result<MediaInfo> tryFinishProcessing(Long mediaId,
        List<Tuple<UploadItem, Long>> toProcessIds) {

        // step 1: re-fetch all domain objects from ids so that these are attached to this session
        MediaInfo mInfo = MediaInfo.get(mediaId)
        if (!mInfo) {
            return resultFactory.failWithCodeAndStatus("tryFinishProcessing.mediaInfoNotFound",
                ResultStatus.NOT_FOUND, [mediaId])
        }
        List<Tuple<UploadItem, MediaElement>> toProcess = rebuildElementsToProcess(toProcessIds)
        // step 2: start processing elements
        ResultGroup<Tuple<List<UploadItem>, MediaElement>> outcomes = new ResultGroup<>()
        toProcess.each { Tuple<UploadItem, MediaElement> processed ->
            outcomes << processElement(processed.first, processed.second)
        }
        outcomes.logFail("tryFinishProcessing")
        // step 3: upload all successes
        List<UploadItem> toUpload = []
        outcomes.payload.each { Tuple<List<UploadItem>, MediaElement> processed ->
            toUpload.addAll(processed.first)
        }
        storageService.uploadAsync(toUpload)
            .logFail("tryProcess: uploading initial media")

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
        Map<Long, MediaElement> idToObject = AsyncUtils.idMap(MediaElement.getAll(elIds))
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
