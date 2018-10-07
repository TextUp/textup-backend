package org.textup

import com.amazonaws.services.s3.model.PutObjectResult
import com.twilio.rest.api.v2010.account.Recording
import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import org.apache.http.client.methods.HttpGet
import org.apache.http.HttpResponse
import org.apache.http.HttpStatus as ApacheHttpStatus
import org.apache.http.impl.client.CloseableHttpClient
import org.textup.type.*
import org.textup.validator.*

@GrailsTypeChecked
@Transactional
class VoicemailService {

    IncomingMediaService incomingMediaService
    ResultFactory resultFactory
    SocketService socketService

    // Voicemail message
    // -----------------

    ResultGroup<RecordCall> processVoicemailMessage(String callId, int duration,
        IncomingRecordingInfo im1) {

        List<MediaElement> mediaElements = incomingMediaService.process([im1]).payload
        if (mediaElements) { return }
        ResultGroup<RecordCall> resGroup = new ResultGroup<>()
        Collection<RecordItemReceipt> receipts = RecordItemReceipt.findAllByApiId(callId)
        receipts*.item
            .unique { RecordItem item -> item.id }
            .each { RecordItem item ->
                if (item.instanceOf(RecordCall)) {
                    resGroup << updateVoicemailForCall(item as RecordCall, duration, mediaElements)
                }
            }
        // send updated items with receipts through socket
        socketService.sendItems(resGroup.payload)
        resGroup
    }
    protected Result<RecordCall> updateVoicemailForCall(RecordCall call, int duration,
        List<MediaElement> eList) {

        MediaInfo mInfo = call.media ?: new MediaInfo()
        eList.each { MediaElement e1 -> mInfo.addToMediaElements(e1) }
        call.hasAwayMessage = true
        call.voicemailInSeconds = duration
        call.record.updateLastRecordActivity()
        call.media = mInfo

        if (!call.record.save()) {
            resultFactory.failWithValidationErrors(call.record.errors)
        }
        else if (!call.save()) {
            resultFactory.failWithValidationErrors(call.errors)
        }
        else { resultFactory.success(call) }
    }

    // Voicemail greeting
    // ------------------

    Result<Closure> recordVoicemailGreeting() {
        // TODO CallResponse.VOICEMAIL_GREETING_RECORD

    }

    Result<Closure> processingVoicemailGreeting() {
        // TODO CallResponse.VOICEMAIL_GREETING_PROCESSING
    }

    Result<Phone> processVoicemailGreeting(IncomingRecordingInfo im1) {
        // TODO CallResponse.VOICEMAIL_GREETING_PROCESSED
        // Uploading via storage service needs to be able to upload PUBLIC ASSETS so Twilio can cache the URL


    }

    Result<Closure> playVoicemailGreeting() {
        // TODO CallResponse.VOICEMAIL_GREETING_PLAY
    }
}
