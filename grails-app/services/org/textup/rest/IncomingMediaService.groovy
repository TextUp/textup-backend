package org.textup.rest

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import java.util.concurrent.*
import org.apache.commons.io.IOUtils
import org.apache.http.client.methods.HttpGet
import org.apache.http.HttpResponse
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.textup.*
import org.textup.media.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*

@GrailsTypeChecked
@Transactional
class IncomingMediaService {

    GrailsApplication grailsApplication
    StorageService storageService

    Result<List<MediaElement>> process(Collection<? extends IsIncomingMedia> incomingMediaList) {
        DomainUtils.tryValidateAll(incomingMediaList)
            .then {
                Collection<UploadItem> toUpload = []
                ResultGroup
                    .collect(incomingMediaList) { IsIncomingMedia im1 ->
                        processElement(im1)
                            .logFail("process: mediaId: ${im1.mediaId}")
                            .then { Tuple<List<UploadItem>, MediaElement> processed ->
                                Tuple.split(processed) { List<UploadItem> uItems, MediaElement el1 ->
                                    toUpload.addAll(uItems)
                                    DomainUtils.trySave(el1)
                                }
                            }
                    }
                    .logFail("process: saving elements")
                    .toResult(true)
                    .curry(toUpload)
            }
            .then { Collection<UploadItem> toUpload, List<MediaElement> els ->
                finishProcessingUploads(incomingMediaList, toUpload)
                    .logFail("process: finish processing elements")
                IOCUtils.resultFactory.success(els)
            }
    }

    // Helpers
    // -------

    protected Result<Tuple<List<UploadItem>, MediaElement>> processElement(IsIncomingMedia im1) {
        MediaType.tryConvertMimeType(im1.mimeType)
            .then { MediaType type ->
                String sid = grailsApplication.flatConfig["textup.apiKeys.twilio.sid"],
                    authToken = grailsApplication.flatConfig["textup.apiKeys.twilio.authToken"]
                // We don't need to add subaccount support here because, as long as the subaccount account id
                // is in the URL, the master credentials will work for authentication
                HttpGet request = new HttpGet(im1.url)
                HttpUtils.executeBasicAuthRequest(sid, authToken, request) { HttpResponse resp ->
                    resp.entity.content.withStream { InputStream stream ->
                        MediaPostProcessor.process(type, IOUtils.toByteArray(stream))
                    }
                }
            }
            .then { Tuple<UploadItem, List<UploadItem>> processed ->
                Tuple.split(processed) { UploadItem sendVersion, List<UploadItem> altVersions ->
                    sendVersion.isPublic = im1.isPublic
                    altVersions.each { UploadItem uItem -> uItem.isPublic = im1.isPublic }
                    MediaElement.tryCreate(altVersions, sendVersion)
                        .curry(CollectionUtils.mergeUnique([altVersions, [sendVersion]]))
                }
            }
            .then { List<UploadItem> uItems, MediaElement el1 ->
                IOCUtils.resultFactory.success(uItems, el1)
            }
    }

    protected Result<Void> finishProcessingUploads(Collection<? extends IsIncomingMedia> incomingMediaList,
        Collection<UploadItem> itemsToUpload) {
        // step 1: upload our processed copies
        storageService.uploadAsync(itemsToUpload)
            .toEmptyResult(false)
            .then {
                // step 2: delete media only if no upload errors after a delay
                ResultGroup<Boolean> resGroup = new ResultGroup<>()
                if (itemsToUpload) {
                    // need a delay here because try to delete immediately results in an error saying
                    // that the incoming message hasn't finished delivering yet. For incoming message
                    // no status callbacks so we have to wait a fixed period of time then attempt to delete
                    TimeUnit.SECONDS.sleep(5)
                    incomingMediaList.each { IsIncomingMedia im1 -> resGroup << im1.delete() }
                }
                resGroup.toEmptyResult(false)
            }
    }
}
