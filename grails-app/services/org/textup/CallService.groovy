package org.textup

import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.ObjectMetadata
import com.amazonaws.services.s3.model.PutObjectResult
import com.twilio.sdk.resource.factory.CallFactory
import com.twilio.sdk.resource.instance.Call
import com.twilio.sdk.resource.list.RecordingList
import com.twilio.sdk.TwilioRestClient
import grails.transaction.Transactional
import org.hibernate.FlushMode
import org.textup.rest.TwimlBuilder
import static org.springframework.http.HttpStatus.*

@Transactional
class CallService {

    def grailsLinkGenerator
	def grailsApplication
	def resultFactory
    def twimlBuilder
    def recordService
    def s3Service
    def lockService

    //////////////////
    // Call methods //
    //////////////////

    Result<RecordCall> startBridgeCall(Staff staffMakingCall, Phone fromPhone, Contactable toContact, RecordCall call) {
        if (call.validate()) {
            String personalAsString = staffMakingCall.personalPhoneNumber?.e164PhoneNumber
            if (personalAsString) {
                tryCall(personalAsString, call, fromPhone.number.e164PhoneNumber,
                    [contactToBridge:toContact.contactId, handle:Constants.CONFIRM_CALL_BRIDGE])
            }
            else {
                resultFactory.failWithMessageAndStatus("callService.startBridgeCall.noPersonalNumber")
            }
        }
        else { resultFactory.failWithValidationErrors(call.error) }
	}
    //staff must pick up and press any number to start the bridge
    Result<Closure> confirmBridgeCallForContact(Long cId) {
        Contact c1 = Contact.get(cId)
        if (c1) { twimlBuilder.buildXmlFor(CallResponse.BRIDGE_CONFIRM_CONNECT, [contactToBridge:c1]) }
        else {
            log.error("CallService.confirmBridgeCallForContact: Contact ${cId} not found.")
            resultFactory.failWithMessageAndStatus(NOT_FOUND, "callService.completeBridgeCallForContact.contactNotFound", [cId])
        }
    }
    //the call method above initiates a bridge call and this method
    //completes the bridge call in the webhook callback
    Result<Closure> completeBridgeCallForContact(Long cId) {
        Contact c1 = Contact.get(cId)
        if (c1) { twimlBuilder.buildXmlFor(CallResponse.BRIDGE_CONNECT, [contactToBridge:c1]) }
        else {
            log.error("CallService.completeBridgeCallForContact: Contact ${cId} not found.")
            resultFactory.failWithMessageAndStatus(NOT_FOUND, "callService.completeBridgeCallForContact.contactNotFound", [cId])
        }
    }


    protected Result<RecordCall> tryCall(String to, RecordCall call, String from, Map afterPickupParams) {

        String afterPickup = grailsLinkGenerator.link(namespace:"v1", resource:"publicRecord",
            action:"save", absolute:true, params:afterPickupParams)



        //TODO: remove this
        String fakeAfterPickup = "https://c12266e7.ngrok.io/v1/public/records?"
        afterPickupParams.each { k, v -> fakeAfterPickup += "$k=$v&" }

        println "afterPickupParams: $afterPickupParams"
        println "REAL AFTER PICKUP IN TRYCALL: $afterPickup"
        println "\t FAKE IS: ${fakeAfterPickup}"

        RecordCall.withNewSession { session ->
            session.flushMode = FlushMode.MANUAL
            try {
                Result<Call> res = makeCallHelper(to, from, fakeAfterPickup)
                if (res.success) {
                    Call c = res.payload
                    RecordItemReceipt receipt = new RecordItemReceipt(apiId:c.sid)
                    receipt.receivedByAsString = to
                    call.addToReceipts(receipt)
                    if (receipt.save()) {
                        //if not merge, we get org.hibernate.NonUniqueObjectException
                        if (call.merge()) { resultFactory.success(call) }
                        else { resultFactory.failWithValidationErrors(call.errors) }
                    }
                    else { resultFactory.failWithValidationErrors(receipt.errors) }
                }
                else { res }
            }
            catch (Throwable e) {
                log.error("CallService.tryCall: ${e.message}")
                resultFactory.failWithThrowable(e)
            }
            finally { session.flushMode = FlushMode.AUTO }
        }
    }
    protected Result<Call> makeCallHelper(String to, String from, String afterPickup) {
        def twilioConfig = grailsApplication.config.textup.apiKeys.twilio
        try {
            TwilioRestClient client = new TwilioRestClient(twilioConfig.sid, twilioConfig.authToken)
            CallFactory cFactory = client.account.callFactory
            resultFactory.success(cFactory.create(To:to, From:from, Url:afterPickup))
        }
        catch (Throwable e) {
            log.error("CallService.makeCallHelper: ${e.message}")
            resultFactory.failWithThrowable(e)
        }
    }

    /////////////////////////
    // Existence of phones //
    /////////////////////////

    @Transactional(readOnly=true)
    boolean teamPhoneExistsForNum(TransientPhoneNumber num) {
        TeamPhone.forTeamNumber(num).get() != null
    }

    @Transactional(readOnly=true)
    boolean staffPhoneExistsForNum(TransientPhoneNumber num) {
        StaffPhone.forStaffNumber(num).get() != null
    }

    ///////////////
    // Voicemail //
    ///////////////

    Result<List<RecordCall>> storeVoicemail(String apiId, String callStatus,
        Integer callDuration, String voicemailUrl, int voicemailDuration) {
        Result<List<RecordItemReceipt>> res = recordService.updateStatus(apiId,
            callStatus, callDuration)
        if (res.success) {
            List<RecordItemReceipt> receipts = res.payload
            //store the encrypted voicemail in s3 and delete recording from Twilio
            res = moveVoicemailToS3(apiId)
            if (res.success) {
                lockService.updateVoicemailItemsAndContacts(receipts, voicemailDuration)
            }
        }
        res
    }
    protected Result moveVoicemailToS3(String apiId, int attemptNum=1) {
        def tConfig = grailsApplication.config.textup,
            awsConf = tConfig.apiKeys.aws
        String bucketName = tConfig.voicemailBucketName
        try {
            Result<Call> res = getCallFromSid(apiId)
            if (res.success) {
                Call call = res.payload
                RecordingList recs = call.recordings

                ObjectMetadata metadata = new ObjectMetadata()
                metadata.setSSEAlgorithm(ObjectMetadata.AES_256_SERVER_SIDE_ENCRYPTION)
                metadata.setContentType("audio/mpeg")
                for (rec in recs) {
                    s3Service.putObject(bucketName, apiId, rec.getMedia(".mp3"), metadata)
                    break //only put the first recording if multiple
                }
                //delete all recordings on Twilio
                for (rec in recs) { rec.delete() }
                resultFactory.success()
            }
            else { res }
        }
        catch (e) {
            log.error("callService.moveVoicemailToS3: ${e.message}")
            if (attemptNum > 3) { resultFactory.failWithThrowable(e) }
            else { moveVoicemailToS3(apiId, attemptNum + 1) }
        }
    }
    protected Result<Call> getCallFromSid(String apiId) {
        def twilioConfig = grailsApplication.config.textup.apiKeys.twilio
        try {
            TwilioRestClient client = new TwilioRestClient(twilioConfig.sid, twilioConfig.authToken)
            Call call = client.account.getCall(apiId)
            if (call) { resultFactory.success(call) }
            else {
                resultFactory.failWithMessageAndStatus(NOT_FOUND,
                    "callService.getCallFromSid.callNotFound", [apiId])
            }
        }
        catch (e) {
            log.error("CallService.getCallFromSid: ${e.message}")
            resultFactory.failWithThrowable(e)
        }
    }

    ////////////////////////////////////
    // Connect incoming call to phone //
    ////////////////////////////////////

    Result<Closure> connectToPhone(TransientPhoneNumber from, TransientPhoneNumber to, String apiId) {
        Phone phone = Phone.forNumber(to).get()
        if (phone) { connectToPhone(from, phone, apiId) }
        else { twimlBuilder.buildXmlFor(CallResponse.DEST_NOT_FOUND, [num:to]) }
    }
    Result<Closure> connectToPhone(TransientPhoneNumber from, Phone toPhone, String apiId) {        
        Result res = recordService.createIncomingRecordCall(from, toPhone, [apiId:apiId])
        if (res.success) {
            res = getNumbersToCallIfAvailable(toPhone)
            if (res.success && res.payload instanceof Collection) {
                List<String> numsToCall = res.payload
                res = twimlBuilder.buildXmlFor(CallResponse.CONNECTING,
                    [numsToCall:numsToCall])
            }
        }
        res
    }
    protected Result getNumbersToCallIfAvailable(Phone phone) {
        if (phone.instanceOf(StaffPhone)) {
            Staff s = Staff.get(phone.ownerId)
            if (s) {
                if (s.isAvailableNow()) { resultFactory.success([s.phone.numberAsString]) }
                else { twimlBuilder.buildXmlFor(CallResponse.VOICEMAIL) }
            }
            else {
                log.error('''CallService.connectToPhone: staff not found for staff
                    phone $phone with ownerId ${phone.ownerId}.''')
                return twimlBuilder.buildXmlFor(CallResponse.SERVER_ERROR)
            }
        }
        else if (phone.instanceOf(TeamPhone)) {
            Team t = Team.forPhone(phone).list()[0]
            if (t) {
                List<String> numsToCall = []
                for (s in t.activeMembers) {
                    if (s.isAvailableNow()) { numsToCall << s.phone.numberAsString }
                }
                if (!numsToCall.isEmpty()) { resultFactory.success(numsToCall) }
                else { twimlBuilder.buildXmlFor(CallResponse.VOICEMAIL) }
            }
            else {
                log.error("CallService.connectToPhone: team not found for team phone $phone.")
                return twimlBuilder.buildXmlFor(CallResponse.SERVER_ERROR)
            }
        }
        else {
            log.error("CallService.connectToPhone: phone $phone is not a staff or team phone.")
            twimlBuilder.buildXmlFor(CallResponse.SERVER_ERROR)
        }
    }
}
