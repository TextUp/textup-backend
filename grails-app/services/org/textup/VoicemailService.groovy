package org.textup

import com.amazonaws.services.s3.model.PutObjectResult
import com.twilio.rest.api.v2010.account.Recording
import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import org.apache.http.client.methods.HttpGet
import org.apache.http.HttpResponse
import org.apache.http.HttpStatus as ApacheHttpStatus
import org.apache.http.impl.client.CloseableHttpClient
import org.textup.rest.*
import org.textup.type.*
import org.textup.validator.*

@GrailsTypeChecked
@Transactional
class VoicemailService {

    CallService callService
    IncomingMediaService incomingMediaService
    ResultFactory resultFactory
    SocketService socketService

    // Voicemail message
    // -----------------

    ResultGroup<RecordCall> processVoicemailMessage(String callId, int duration,
        IncomingRecordingInfo ir1) {

        ir1.isPublic = false
        ResultGroup<MediaElement> processResultGroup = incomingMediaService.process([ir1])
        if (processResultGroup.anyFailures) {
            return processResultGroup
        }
        ResultGroup<RecordCall> resGroup = new ResultGroup<>()
        List<MediaElement> mediaElements = processResultGroup.payload
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

    Result<Void> finishedProcessingVoicemailGreeting(Phone p1, String callId,
        IncomingRecordingInfo ir1) {

        // voicemail greetings are public so that Twilio can cache the object and because anyone
        // who calls the number and gets sent to voicemail will hear this greeting
        ir1.isPublic = true
        ResultGroup<MediaElement> resGroup = incomingMediaService.process([ir1])
        if (resGroup.anyFailures) {
            return resultFactory.failWithGroup(resGroup)
        }
        MediaInfo mInfo = p1.media ?: new MediaInfo()
        resGroup.payload.each { MediaElement e1 -> mInfo.addToMediaElements(e1) }
        p1.media = mInfo
        if (p1.save()) {
            callService.interrupt(callId, CallTwiml.infoForPlayVoicemailGreeting())
        }
        else { resultFactory.failWithValidationErrors(p1.errors) }
    }
}
