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

@GrailsTypeChecked
@Transactional
class VoicemailService {

    GrailsApplication grailsApplication
    ResultFactory resultFactory
    SocketService socketService
    StorageService storageService

    // [UNTESTED] due to mocking constraints
    Result<String> moveVoicemail(String callId, String recordingId, String voicemailUrl) {
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

    ResultGroup<RecordItemReceipt> storeVoicemail(String callId, int voicemailDuration) {
        ResultGroup<RecordItemReceipt> resGroup = new ResultGroup<>()
        List<RecordItemReceipt> receipts = RecordItemReceipt.findAllByApiId(callId)
        for (receipt in receipts) {
            RecordItem item = receipt.item
            if (item.instanceOf(RecordCall)) {
                RecordCall call = item as RecordCall
                call.hasAwayMessage = true // proxied to hasVoicemail in RecordCall
                call.voicemailInSeconds = voicemailDuration
                call.record.updateLastRecordActivity()
                if (call.save() && call.record.save()) {
                    resGroup << resultFactory.success(receipt)
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
        socketService.sendItems(resGroup.payload*.item)
        resGroup
    }

    String getVoicemailUrl(RecordItemReceipt receipt) {
        if (receipt) {
            Result<URL> res = storageService
                .generateAuthLink(receipt.apiId)
                .logFail("VoicemailService.getVoicemailUrl")
            res.success ? res.payload.toString() : ""
        }
        else { "" }
    }
}
