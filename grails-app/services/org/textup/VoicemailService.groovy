package org.textup

import grails.compiler.GrailsCompileStatic
import grails.transaction.Transactional
import java.nio.charset.Charset
import org.apache.http.client.methods.HttpGet
import org.apache.http.HttpHeaders
import org.apache.http.HttpResponse
import org.apache.http.HttpStatus as ApacheHttpStatus
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients
import org.codehaus.groovy.grails.commons.GrailsApplication

@GrailsCompileStatic
@Transactional
class VoicemailService {

    GrailsApplication grailsApplication
    ResultFactory resultFactory
    SocketService socketService
    StorageService storageService

    Result<String> moveVoicemail(String callId, String recordingId, String voicemailUrl) {
        // build a HttpClient and execute the get request
        HttpClients.createDefault().withCloseable { CloseableHttpClient client ->
            try {
                HttpGet req = new HttpGet(voicemailUrl + ".mp3")
                req.setHeader(HttpHeaders.AUTHORIZATION, buildBasicAuth());
                client.execute(req).withCloseable { HttpResponse resp ->
                    int statusCode = resp.statusLine.statusCode
                    if (statusCode != ApacheHttpStatus.SC_OK) {
                        return resultFactory.failWithCodeAndStatus(
                            "voicemailService.moveVoicemail.couldNotRetrieveVoicemail",
                            ResultStatus.convert(statusCode),
                            [resp.statusLine.reasonPhrase])
                    }
                    resp.entity.content.withCloseable { InputStream stream ->
                        storageService
                            .upload(callId, "audio/mpeg", stream)
                            .then({ PutObjectResult putRes ->
                                boolean deleteOutcome = Recording.deleter(recordingId).delete()
                                if (deleteOutcome == false) {
                                    log.error("VoicemailService.moveVoicemail could not delete ${recordingId}")
                                }
                                resultFactory.success(putRes.getETag())
                            })
                    }
                }
            }
            catch (Throwable e) {
                log.error("VoicemailService.moveVoicemail throwable: ${e.message}")
                e.printStackTrace()
                resultFactory.failWithThrowable(e)
            }
        }
    }

    ResultGroup<RecordItemReceipt> storeVoicemail(String callId, int voicemailDuration) {
        ResultGroup<RecordItemReceipt> resGroup = new ResultGroup<>()
        List<RecordItemReceipt> receipts = RecordItemReceipt.findAllByApiId(callId)
        for (receipt in receipts) {
            RecordItem item = receipt.item
            if (item.instanceOf(RecordCall)) {
                RecordCall call = item as RecordCall
                call.hasVoicemail = true
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

    // Helpers
    // -------

    protected String buildBasicAuth() {
        String un = grailsApplication.flatConfig["textup.apiKeys.twilio.sid"],
            pwd = grailsApplication.flatConfig["textup.apiKeys.twilio.authToken"]
        // build basic authentication header string. If we used a CredentialProvider instead
        // we would need to configure pre-emptive basic authentication or else we
        // wcould make two requests each time
        // http://www.baeldung.com/httpclient-4-basic-authentication
        String auth = un + ":" + pwd;
        byte[] encodedAuth = Base64.encodeBase64(auth.getBytes(Charset.forName("ISO-8859-1")));
        "Basic " + new String(encodedAuth)
    }
}
