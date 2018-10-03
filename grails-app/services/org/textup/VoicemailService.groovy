package org.textup

import com.amazonaws.services.s3.model.PutObjectResult
import com.twilio.rest.api.v2010.account.Recording
import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import org.apache.http.client.methods.HttpGet
import org.apache.http.HttpResponse
import org.apache.http.HttpStatus as ApacheHttpStatus
import org.apache.http.impl.client.CloseableHttpClient
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.textup.rest.TwimlBuilder
import org.textup.type.*
import org.textup.validator.*

@GrailsTypeChecked
@Transactional
class VoicemailService {

    GrailsApplication grailsApplication
    ResultFactory resultFactory
    SocketService socketService
    StorageService storageService
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

    ResultGroup<RecordCall> completeVoicemail(String callId, String recordingId,
        String voicemailUrl, Integer voicemailDuration) {
        // move the encrypted voicemail to s3 and delete recording at Twilio
        Result<String> res = moveVoicemail(callId, recordingId, voicemailUrl)
            .logFail("Phone.moveVoicemail: call: ${callId}, recording: ${recordingId}")
        if (!res.success) {
            return res.toGroup()
        }
        storeVoicemail(callId, voicemailDuration)
            .logFail("Phone.storeVoicemail: call: ${callId}, recording: ${recordingId}")
    }

    String getVoicemailUrl(String voicemailKey) {
        if (voicemailKey) {
            Result<URL> res = storageService
                .generateAuthLink(voicemailKey)
                .logFail("VoicemailService.getVoicemailUrl")
            res.success ? res.payload.toString() : ""
        }
        else { "" }
    }

    // Helpers
    // -------

    // [UNTESTED] due to mocking constraints
    protected Result<String> moveVoicemail(String callId, String recordingId, String voicemailUrl) {
        try {
            String sid = grailsApplication.flatConfig["textup.apiKeys.twilio.sid"],
                authToken = grailsApplication.flatConfig["textup.apiKeys.twilio.authToken"]
            CloseableHttpClient client = Helpers.buildBasicAuthHttpClient(sid, authToken)
            client.withCloseable {
                HttpResponse resp = client.execute(new HttpGet(voicemailUrl))
                resp.withCloseable {
                    int statusCode = resp.statusLine.statusCode
                    if (statusCode != ApacheHttpStatus.SC_OK) {
                        return resultFactory.failWithCodeAndStatus(
                            "voicemailService.moveVoicemail.couldNotRetrieveVoicemail",
                            ResultStatus.convert(statusCode),
                            [resp.statusLine.reasonPhrase])
                    }
                    resp.entity.content.withStream { InputStream stream ->
                        storageService
                            .upload(callId, "audio/mpeg", stream)
                            .then({ PutObjectResult putRes ->
                                boolean deleteOutcome = Recording.deleter(recordingId).delete()
                                if (deleteOutcome == false) {
                                    log.error("VoicemailService.moveVoicemail could not delete ${recordingId}")
                                }
                                resultFactory.success(putRes.getETag())
                            })
                    } as Result<String>
                } as Result<String>
            } as Result<String>
        }
        catch (Throwable e) {
            log.error("VoicemailService.moveVoicemail throwable: ${e.message}")
            e.printStackTrace()
            resultFactory.failWithThrowable(e)
        }
    }

    protected ResultGroup<RecordCall> storeVoicemail(String callId, int voicemailDuration) {
        ResultGroup<RecordCall> resGroup = new ResultGroup<>()
        List<RecordItemReceipt> receipts = RecordItemReceipt.findAllByApiId(callId)
        Collection<RecordItem> rItems = receipts*.item.unique { RecordItem rItem -> rItem.id }
        for (RecordItem item in rItems) {
            if (item.instanceOf(RecordCall)) {
                RecordCall call = item as RecordCall
                call.hasAwayMessage = true // proxied to hasVoicemail in RecordCall
                call.voicemailInSeconds = voicemailDuration
                call.voicemailKey = callId
                call.record.updateLastRecordActivity()
                if (call.save() && call.record.save()) {
                    resGroup << resultFactory.success(call)
                }
                else if (!call.save()) {
                    resGroup << resultFactory.failWithValidationErrors(call.errors)
                }
                else {
                    resGroup << resultFactory.failWithValidationErrors(call.record.errors)
                }
            }
        }
        // send updated items with receipts through socket
        socketService.sendItems(resGroup.payload)
        resGroup
    }
}
