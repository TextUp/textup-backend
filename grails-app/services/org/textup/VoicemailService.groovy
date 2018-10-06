package org.textup

import com.amazonaws.services.s3.model.PutObjectResult
import com.twilio.rest.api.v2010.account.Recording
import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import org.apache.http.client.methods.HttpGet
import org.apache.http.HttpResponse
import org.apache.http.HttpStatus as ApacheHttpStatus
import org.apache.http.impl.client.CloseableHttpClient
import org.textup.rest.TwimlBuilder
import org.textup.type.*
import org.textup.validator.*

@GrailsTypeChecked
@Transactional
class VoicemailService {

    IncomingMediaService incomingMediaService
    ResultFactory resultFactory
    SocketService socketService
    TwimlBuilder twimlBuilder

    Result<Closure> tryStartVoicemail(Phone p1, BasePhoneNumber fromNum, ReceiptStatus status) {
        if (status == ReceiptStatus.SUCCESS) { // call already connected so no voicemail
            twimlBuilder.noResponse()
        }
        else {
            twimlBuilder.build(CallResponse.CHECK_IF_VOICEMAIL,
                [
                    voice: p1.voice,
                    awayMessage:p1.awayMessage,
                    // no-op for Record Twiml verb to call because recording might not be ready
                    linkParams:[handle:CallResponse.END_CALL],
                    // need to population From and To parameters to help in finding
                    // phone and session in the recording status hook
                    callbackParams:[handle:CallResponse.VOICEMAIL_DONE,
                        From:fromNum.e164PhoneNumber, To:p1.number.e164PhoneNumber]
                ])
        }
    }

    ResultGroup<RecordCall> processVoicemailMessage(IncomingRecordingInfo im1) {
        List<MediaElement> mediaElements = incomingMediaService.processElement([im1]).payload
        if (mediaElements) { return }
        RecordItemReceipt.findAllByApiId(callId)
            *.item
            .unique { RecordItem item -> item.id }
            .each { RecordItem item ->
                if (item.instanceOf(RecordCall)) {
                    resGroup << updateVoicemailForCall(item as RecordCall, mediaElements)
                }
            }
        // send updated items with receipts through socket
        socketService.sendItems(resGroup.payload)
        resGroup
    }
    protected Result<RecordCall> updateVoicemailForCall(RecordCall call, List<MediaElement> eList) {
        MediaInfo mInfo = call.media ?: new MediaInfo()
        mediaElements.each { MediaElement e1 -> mInfo.addToMediaElements(e1) }
        call.hasAwayMessage = true
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

    void processVoicemailGreeting(IncomingRecordingInfo im1) {
        // TODO
    }
}
