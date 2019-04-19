package org.textup

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import java.util.concurrent.*
import org.textup.action.*
import org.textup.annotation.*
import org.textup.media.*
import org.textup.rest.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*

@GrailsTypeChecked
@Transactional
class MediaService {

    MediaActionService mediaActionService
    StorageService storageService
    ThreadService threadService

    Result<Future<Result<?>>> tryCreateOrUpdate(WithMedia withMedia, Map body,
        boolean isPublic = false) {

        if (mediaActionService.hasActions(body)) {
            MediaInfo.tryCreate(withMedia.media).then { MediaInfo mInfo ->
                // need to associate media with media owner in case media is newly-created
                withMedia.media = mInfo
                tryStartProcessing(mInfo, body, isPublic)
            }
        }
        else { IOCUtils.resultFactory.success(AsyncUtils.noOpFuture()) }
    }

    Result<Tuple<MediaInfo, Future<Result<?>>>> tryCreate(Map body, boolean isPublic = false) {
        if (mediaActionService.hasActions(body)) {
            MediaInfo.tryCreate()
                .then { MediaInfo mInfo -> tryStartProcessing(mInfo, body, isPublic).curry(mInfo) }
                .then { MediaInfo mInfo, Future<?> fut1 ->
                    IOCUtils.resultFactory.success(mInfo, fut1)
                }
        }
        else { IOCUtils.resultFactory.success(null, AsyncUtils.noOpFuture()) }
    }

    // Helpers
    // -------

    protected Result<Future<?>> tryStartProcessing(MediaInfo mInfo, Map body, boolean isPublic) {
        mediaActionService.tryHandleActions(mInfo, body)
            .logFail("tryStartProcessing: building initial version")
            .then { List<UploadItem> uItems -> PartialUploads.tryCreate(uItems) }
            .then { PartialUploads pu1 -> mInfo.tryAddAllElements(pu1.elements).curry(pu1) }
            .then { PartialUploads pu1 ->
                Collection<String> errors = storageService.uploadAsync(pu1.uploads)
                    .logFail("tryStartProcessing: uploading initial media")
                    .errorMessages
                RequestUtils.trySet(RequestUtils.UPLOAD_ERRORS, errors)
                IOCUtils.resultFactory.success(threadService.delay(5, TimeUnit.SECONDS) {
                    DehydratedPartialUploads.tryCreate(pu1)
                        .then { DehydratedPartialUploads dpu1 -> tryFinishProcessing(mInfo.id, dpu1) }
                        .logFail("trying to finish processing for MediaInfo `${mInfo.id}`")
                })
            }
    }

    protected Result<MediaInfo> tryFinishProcessing(Long mediaId, Rehydratable<PartialUploads> dpu1) {
        MediaInfos.mustFindForId(mediaId)
            .then { MediaInfo mInfo -> dpu1.tryRehydrate().curry(mInfo) }
            .then { MediaInfo mInfo, PartialUploads partials ->
                ResultGroup<List<UploadItem>> resGroup = new ResultGroup<>()
                partials.eachUpload { UploadItem uItem1, MediaElement el1 ->
                    resGroup << storeUpload(uItem1, el1)
                }
                resGroup.toResult(true).curry(mInfo)
            }
            .then { MediaInfo mInfo, List<List<UploadItem>> uItems ->
                storageService.uploadAsync(CollectionUtils.mergeUnique(uItems))
                    .logFail("tryFinishProcessing: uploading")
                DomainUtils.trySave(mInfo)
            }
    }

    protected Result<List<UploadItem>> storeUpload(UploadItem initialUpload, MediaElement el1) {
        MediaPostProcessor.process(initialUpload.type, initialUpload.data)
            .then { Tuple<UploadItem, List<UploadItem>> processed ->
                Tuple.split(processed) { UploadItem sendItem, List<UploadItem> altItems ->
                    sendItem.isPublic = initialUpload.isPublic
                    el1.sendVersion = MediaElementVersion.createIfPresent(sendItem)
                    altItems.each { UploadItem uItem ->
                        uItem.isPublic = initialUpload.isPublic
                        el1.addToAlternateVersions(MediaElementVersion.createIfPresent(uItem))
                    }
                    DomainUtils.trySave(el1).then {
                        IOCUtils.resultFactory.success(CollectionUtils.mergeUnique([[sendItem], altItems]))
                    }
                }
            }
    }
}
