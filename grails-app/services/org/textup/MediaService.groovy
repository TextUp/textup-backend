package org.textup

import grails.compiler.GrailsCompileStatic
import grails.transaction.Transactional
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import javax.imageio.ImageWriter
import javax.imageio.stream.ImageOutputStream

@GrailsCompileStatic
@Transactional
class MediaService {

    SerializerService serializerService
    StorageService storageService

    // Media domain helpers
    // --------------------

    Result<MediaInfo> synchronizeMedia(WithMedia owner, String data) {
        owner.serializedMedia = data
        serializerService.<MediaInfo>deserialize(data)
            .logFail("MediaService.synchronizeMedia")
            .then({ MediaInfo mInfo ->
                owner.media = mInfo
                resultFactory.success(mInfo)
            })
    }

    Result<String> synchronizeMedia(WithMedia owner, MediaInfo mInfo) {
        owner.mediaItems = mInfo
        serializerService.serialize(mInfo)
            .logFail("MediaService.synchronizeMedia")
            .then({ String data ->
                owner.serializedMedia = data
                resultFactory.success(data)
            })
    }

    MediaInfo getMedia(ReadOnlyWithMedia owner) {
        owner.media ?:
            owner.serializedMedia && serializerService // serializedMedia may be null
                .<MediaInfo>deserialize(owner.serializedMedia)
                .logFail("MediaService.getMedia")
                ?.payload ?:
                new MediaInfo()
    }

    // Media actions
    // -------------

    <T extends WithMedia> Result<T> handleMediaActions(T owner, Collection<String> errorMsgs, Map body) {
        buildMediaInfo(owner.media, errorMsgs, body)
            .then { MediaInfo mInfo -> owner.synchronizeMedia(mInfo) }
            .then { String data ->
                if (owner.validate()) {
                    resultFactory.success(owner)
                }
                else { resultFactory.failWithValidationErrors(owner.errors) }
            }
    }
    Result<MediaInfo> buildMediaInfo(MediaInfo mInfo, Collection<String> errorMsgs, Map body) {
        if (!body.doMediaActions) {
            return mInfo.save() ? resultFactory.success(mInfo) :
                resultFactory.failWithValidationErrors(mInfo.errors)
        }
        // validate actions
        ActionContainer ac1 = new ActionContainer(body.doMediaActions)
        List<MediaAction> actions = ac1.validateAndBuildActions(MediaAction)
        if (ac1.hasErrors()) {
            return resultFactory.failWithValidationErrors(ac1.errors)
        }
        // process actions
        List<UploadItem> itemsToUpload = []
        actions.each { MediaAction a1 ->
            switch (a1) {
                case Constants.MEDIA_ACTION_ADD:
                    createUploads(a1.mimeType, a1.byteData)
                        .then({ List<UploadItem> uItems ->
                            // construct media element
                            MediaElement e1 = new MediaElement(type: MediaType.convertMimeType(mimeType))
                            uItems.each(e1.&addVersion)
                            // add element to info parent object + save items to batch upload later
                            mInfo.elements << e1
                            itemsToUpload += uItems
                        }, errorMessages.&addAll)
                    break
                default: // Constants.MEDIA_ACTION_REMOVE
                    mInfo.removeElement(a1.uid)
            }
        }
        // batch upload actions
        storageService.uploadAsync(itemsToUpload)
            .failures
            .each { Result<?> failRes -> errorMessages += failRes.errorMessages }
        // save constructed object
        mInfo.save() ? resultFactory.success(mInfo) :
            resultFactory.failWithValidationErrors(mInfo.errors)
    }
    protected Result<List<UploadItem>> createUploads(String mimeType, byte[] data) {
        List<UploadItem> toUpload = Collections.emptyList()
        createSendVersion(mimeType, data)
            .then { UploadItem uItem ->
                toUpload << uItem
                createDisplayVersions(mimeType, data)
            }
            .then { EnumMap<MediaVersion, MediaElementVersion> displayVersions ->
                toUpload += displayVersions
                resultFactory.success(toUpload)
            }
    }
    protected Result<UploadItem> createSendVersion(String mimeType, byte[] data) {
        UploadItem uItem = new UploadItem(version: MediaVersion.SEND, mimeType: mimeType, data: data)
        uItem.tryResizeToWidth(MediaVersion.SEND.maxWidthInPixels)
        uItem.tryCompress(MediaVersion.SEND.maxSizeInBytes)
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
            currentItem = new UploadItem(version: nextVersion, mimeType: mimeType, data: data)
            currentItem.tryResizeToWidth(nextVersion.maxWidthInPixels)
            uItem.tryCompress(nextVersion.maxSizeInBytes)
            if (currentItem.validate()) {
                uItems << currentItem
                nextVersion = currentItem.version.next
            }
            else { return resultFactory.failWithValidationErrors(currentItem.errors) }
        }
        resultFactory.success(uItems)
    }

    // Sending media
    // -------------

    Result<List<TempRecordReceipt>> sendWithMedia(boolean sendAsText, BasePhoneNumber fromNum,
        List<? extends BasePhoneNumber> toNums, MediaInfo mInfo, String msg1, VoiceLanguage lang1,
        String phoneName) {

        ResultGroup<TempRecordReceipt> resGroup = sendAsText ?
            sendWithMediaForText(fromNum, toNums, mInfo, msg1) :
            sendWithMediaForCall(fromNum, toNums, mInfo, msg1, lang1, phoneName)
        if (resGroup.anyFailures) {
            resultFactory.failWithResultsAndStatus(resGroup.failures,
                resGroup.failureStatus, false)
        }
        else {
            resultFactory.success(resGroup.successes*.payload)
        }
    }

    protected ResultGroup<TempRecordReceipt> sendWithMediaForText(BasePhoneNumber fromNum,
        List<? extends BasePhoneNumber> toNums, MediaInfo mInfo, String msg1 = "") {
        ResultGroup<TempRecordReceipt> resGroup = new ResultGroup<>()
        if (mInfo.isEmpty()) {
            resGroup << textService.send(fromNum, toNums, msg1)
        }
        else {
            mInfo.forEachBatch { List<MediaElement> batchSoFar ->
                resGroup << textService.send(fromNum, toNums, msg1, batchSoFar)
            }
        }
        resGroup
    }

    protected ResultGroup<TempRecordReceipt> sendWithMediaForCall(BasePhoneNumber fromNum,
        List<? extends BasePhoneNumber> toNums, MediaInfo mInfo, String msg1, VoiceLanguage lang1,
        String phoneName) {
        ResultGroup<TempRecordReceipt> resGroup = new ResultGroup<>()
        if (!mInfo.isEmpty()) {
            resGroup << sendWithMediaForText("", mInfo)
        }

        // TODO should NOT be leaking message contents here!

        resGroup << callService.start(fromNum, sortedNums, [
            handle:CallResponse.DIRECT_MESSAGE,
            message:msg1,
            identifier:phoneName,
            // pass in string representation of this enum NOT the twiml value
            // because we need to parse this string value to re-obtain the enum
            // on the callback hook after pick-up
            language:lang1?.toString()
        ])
        resGroup
    }
}
