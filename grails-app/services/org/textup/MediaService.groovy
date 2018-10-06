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
        uploadGroup.payload.each { UploadItem initialUpload ->
            MediaElement e1 = new MediaElement()
            e1.addToAlternateVersions(initialUpload.toMediaElementVersion())
            mInfo.addToMediaElements(e1)
        }
        if (mInfo.save()) {
            storageService.uploadAsync(uploadGroup.payload)
                .logFail("MediaService.tryProcess: uploading initial media")
            Future<Result<MediaInfo>> fut = threadService.submit {
                mediaService.tryFinishProcessing(mInfo)
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
        storageService.uploadAsync(outcomes.payload*.first.flatten())
            .logFail("MediaService.tryProcess: uploading initial media")
        if (mInfo.save()) {
            // send new media info through socket
            socketService.sendMedia([mInfo])
            resultFactory.success(mInfo)
        }
        else { resultFactory.failWithValidationErrors(mInfo.errors) }
    }
    protected Result<Tuple<List<UploadItem>, MediaElement>> processElement(UploadItem uItem,
        MediaElement e1) {

        MediaPostProcessor
            .process(uItem.type, uItem.data)
            .then { Tuple<UploadItem, List<UploadItem>> processed ->
                e1.sendVersion = processed.first
                processed.second.each { MediaElementVersion v1 -> e1.addToAlternateVersions(v1) }
                if (e1.save()) {
                    resultFactory.success(processed.second.clone() << processed.first, e1)
                }
                else { resultFactory.failWithValidationErrors(e1.errors) }
            }
    }
}
