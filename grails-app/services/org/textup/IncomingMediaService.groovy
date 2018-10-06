package org.textup

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional

@GrailsTypeChecked
@Transactional
class IncomingMediaService {

    GrailsApplication grailsApplication
    ThreadService threadService
    StorageService storageService

    ResultGroup<MediaElement> process(Collection<IsIncomingMedia> incomingMediaList) {
        // step 1: fetch media from Twilio and build appropriate versions
        ResultGroup<Tuple<List<UploadItem>, MediaElement>> outcomes =
            new ResultGroup<>(incomingMediaList.collect(this.&processElement))
            .logFail("IncomingMediaService.process: processing elements")
        // step 2: upload to our media bucket and delete copy from Twilio
        finishProcessingUploads(incomingMediaList, outcomes.payload*.first.flatten())
            .logFail("IncomingMediaService.process: updating and deleting")
        outcomes
    }

    // Helpers
    // -------

    protected Result<Tuple<List<UploadItem>, MediaElement>> processElement(IsIncomingMedia im1) {
        if (!im1.validate()) {
            return resultFactory.failWithValidationErrors(im1.errors)
        }
        MediaType type = MediaType.convertMimeType(im1.mimeType)
        if (!type) {
            return resultFactory.failWithCodeAndStatus("incomingMediaService.processElement.invalidMimeType",
                ResultStatus.UNPROCESSABLE_ENTITY, [mimeType])
        }
        String sid = grailsApplication.flatConfig["textup.apiKeys.twilio.sid"],
            authToken = grailsApplication.flatConfig["textup.apiKeys.twilio.authToken"]
        Helpers.executeBasicAuthRequest(sid, authToken, new HttpGet(url.im1)) { HttpResponse resp ->
            int statusCode = resp.statusLine.statusCode
            if (statusCode == ApacheHttpStatus.SC_OK) {
                return resultFactory.failWithCodeAndStatus(
                    "incomingMediaService.processElement.couldNotRetrieveMedia",
                    ResultStatus.convert(statusCode), [resp.statusLine.reasonPhrase])
            }
            resp.entity.content.withStream { InputStream stream ->
                byte[] data = IOUtils.toByteArray(stream)
                MediaPostProcessor
                    .process(type, data)
                    .then { Tuple<UploadItem, List<UploadItem>> processed ->
                        MediaElement
                            .create(processed.first, processed.second)
                            .curry(processed.second.clone() << processed.first)
                    }
                    .then { List<UploadItems> uItems, MediaElement e1 ->
                        resultFactory.success(uItems, e1)
                    }
            }
        }
    }

    protected Result<Void> finishProcessingUploads(Collection<IsIncomingMedia> incomingMediaList,
        Collection<UploadItem> itemsToUpload) {

        // step 1: upload our processed copies
        ResultGroup<?> resGroup = storageService.uploadAsync(itemsToUpload)
            .logFail("IncomingMediaService.finishProcessingUploads: uploading processed media")
        // step 2: delete media only if no upload errors after a delay
        if (itemsToUpload) {
            // need a delay here because try to delete immediately results in an error saying
            // that the incoming message hasn't finished delivering yet. For incoming message
            // no status callbacks so we have to wait a fixed period of time then attempt to delete
            TimeUnit.SECONDS.sleep(5)
            incomingMediaList.each { IsIncomingMedia im1 -> resGroup << im1.delete() }
        }
        if (resGroup.anyFailures) {
            resultFactory.failWithGroup(resGroup)
        }
        else { resultFactory.success() }
    }
}
