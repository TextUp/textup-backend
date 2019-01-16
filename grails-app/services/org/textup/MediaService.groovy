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

    StorageService storageService
    ThreadService threadService
    MediaActionService mediaActionService

    Result<Future<Result<?>>> tryCreateOrUpdate(WithMedia withMedia, Map body,
        boolean isPublic = false) {

        if (mediaActionService.hasActions(body)) {
            MediaInfo.tryCreate(withMedia.media).then { MediaInfo mInfo ->
                tryStartProcessing(mInfo, body, isPublic)
            }
        }
        else { IOCUtils.resultFactory.success(null, AsyncUtils.noOpFuture(mInfo)) }
    }

    Result<Tuple<MediaInfo, Future<Result<?>>>> tryCreate(Map body, boolean isPublic = false) {
        if (mediaActionService.hasActions(body)) {
            MediaInfo.tryCreate()
                .then { MediaInfo mInfo -> tryStartProcessing(mInfo, body, isPublic).curry(mInfo) }
                .then { MediaInfo mInfo, Future<?> fut1 ->
                    IOCUtils.resultFactory.success(mInfo, fut1)
                }
        }
        else { IOCUtils.resultFactory.success(null, AsyncUtils.noOpFuture(mInfo)) }
    }

    // Helpers
    // -------

    protected Result<Future<?>> tryStartProcessing(MediaInfo mInfo, Map body, boolean isPublic) {
        mediaActionService.tryHandleActions(mInfo, body)
            .logFail("tryStartProcessing: building initial version")
            .then { List<UploadItem> uItems ->
                PartialUploads pu1 = new PartialUploads()
                uItems.each { UploadItem initialUpload ->
                    initialUpload.isPublic = isPublic
                    pu1.createAndAdd(initialUpload).then { MediaElement el1 ->
                        mInfo.addToMediaElements(el1)
                    }
                }
                DomainUtils.tryValidate(pu1)
            }
            .then { PartialUploads pu1 ->
                storageService.uploadAsync(pu1.uploads)
                    .logFail("tryStartProcessing: uploading initial media")
                    .ifFail { Result<?> failRes ->
                        Utils
                            .trySetOnRequest(Constants.REQUEST_UPLOAD_ERRORS, failRes.errorMessages)
                            .logFail("tryStartProcessing: setting upload errors on request")
                    }
                IOCUtils.resultFactory.success(threadService.delay(5, TimeUnit.SECONDS) {
                    tryFinishProcessing(mInfo.id, pu1.dehydrate())
                        .logFail("trying to finish processing for MediaInfo `${mInfo.id}`")
                })
            }
    }

    protected Result<MediaInfo> tryFinishProcessing(Long mediaId,
        Rehydratable<PartialUploads> dPartials) {

        MediaInfos.mustFindForId(mediaId)
            .then { MediaInfo mInfo -> dPartials.tryRehydrate().curry(mInfo) }
            .then { MediaInfo mInfo, PartialUploads partials ->
                ResultGroup<List<UploadItem>> resGroup = new ResultGroup<>()
                partials.eachUpload { UploadItem uItem1, MediaElement el1 ->
                    resGroup << completeUpload(uItem1, el1)
                }
                resGroup.toResult(true).curry(mInfo)
            }
            .then { MediaInfo mInfo, List<List<UploadItem>> uItems ->
                storageService.uploadAsync(CollectionUtils.mergeUnique(*uItems))
                    .logFail("tryFinishProcessing: uploading")
                DomainUtils.trySave(mInfo)
            }
    }

    protected Result<List<UploadItem>> completeUpload(UploadItem initialUpload, MediaElement el1) {
        MediaPostProcessor.process(initialUpload.type, initialUpload.data)
            .then { Tuple<UploadItem, List<UploadItem>> processed ->
                Tuple.split(processed) { UploadItem sendItem, List<UploadItem>> altItems ->
                    sendItem.isPublic = initialUpload.isPublic
                    el1.sendVersion = sendItem.toMediaElementVersion()
                    altItems.each { UploadItem uItem ->
                        uItem.isPublic = initialUpload.isPublic
                        el1.addToAlternateVersions(uItem.toMediaElementVersion())
                    }
                    DomainUtils.trySave(el1).then {
                        IOCUtils.resultFactory.success(CollectionUtils.mergeUnique([sendItem], altItems))
                    }
                }
            }
    }
}
