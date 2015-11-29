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
            String personalAsString = staff.personalPhoneNumber?.e164PhoneNumber
            if (personalPhoneAsString) {
                tryCall(personalAsString, call, fromPhone.number.e164PhoneNumber, toContact, 
                    [contactToBridge:toContact.contactId, handle:Constants.CALL_BRIDGE])
            }
            else {
                resultFactory.failWithMessageAndStatus("callService.startBridgeCall.noPersonalNumber")
            }
        }
        else { resultFactory.failWithValidationErrors(call.error) }
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
    
    Result<RecordCall> startCallAnnouncement(Phone fromPhone, Contactable toContact, RecordCall call, Long teamContactTagId, Long recordTextId) {
        if (call.validate()) {
            tryCall(toContact.numbers[0]?.e164PhoneNumber, call, fromPhone.number.e164PhoneNumber, toContact, 
                [handle:Constants.CALL_ANNOUNCEMENT, teamContactTagId:teamContactTagId, recordTextId:recordTextId])
        }
        else { resultFactory.failWithValidationErrors(call.error) }
    }
    Result<Closure> completeCallAnnouncement(Long teamContactTagId, Long recordTextId) {
        RecordText rt1 = RecordText.get(recordTextId)
        TeamContactTag ct1 = TeamContactTag.get(teamContactTagId)
        if (rt1 && ct1) {
            Team t1 = Team.forPhone(ct1.phone).get()
            twimlBuilder.buildXmlFor(CallResponse.ANNOUNCEMENT, 
                [contents:rt1.contents, teamName:t1.name, tagName:ct1.name, tagId:ct1.id, textId:rt1.id])
        }
        else {
            log.error("CallService.completeCallAnnouncement: RecordText ${recordTextId} not found.")
            resultFactory.failWithMessageAndStatus(NOT_FOUND, "callService.completeCallAnnouncement.notFound", [rt1?.id, ct1?.id])
        }
    }
    Result<Closure> handleCallAnnouncementUnsubscribeOne(String contactNum, String phoneNum, Long teamContactTagId) {
        TeamPhone p1 = TeamPhone.forTeamNumber(phoneNum)
        TeamContactTag ct1 = TeamContactTag.get(teamContactTagId)
        if (p1 && ct1) {
            List<Contact> contacts = Contact.forPhoneAndNum(p1, fromNum.number).list()
            contacts.each { Contact c1 -> c1.quietUnsubscribeFromTag(ct1) }
            twimlBuilder.buildXmlFor(CallResponse.ANNOUNCEMENT_UNSUBSCRIBE_ONE, [tagName:ct1.name])
        }
        else {
            resultFactory.failWithMessageAndStatus(NOT_FOUND, "callService.handleCallAnnouncementUnsubscribeOne.notFound", [p1?.id, ct1?.id])
        }
    }
    Result<Closure> handleCallAnnouncementUnsubscribeAll(String contactNum, String phoneNum) {
        TeamPhone p1 = TeamPhone.forTeamNumber(phoneNum)
        if (p1) {
            List<Contact> contacts = Contact.forPhoneAndNum(p1, fromNum.number).list()
            List<ContactTag> tags = p1.tags
            contacts.each { Contact c1 ->
                tags.each { ContactTag t1 ->
                    c1.quietUnsubscribeFromTag(t1)
                }
            }
            twimlBuilder.buildXmlFor(CallResponse.ANNOUNCEMENT_UNSUBSCRIBE_ALL)
        }
        else {
            resultFactory.failWithMessageAndStatus(NOT_FOUND, "callService.handleCallAnnouncementUnsubscribeAll.notFound", [p1?.id])
        }
    }

    protected Result<RecordCall> tryCall(String to, RecordCall call, String from, Map afterPickupParams) {

        String afterPickup = grailsLinkGenerator.link(namespace:"v1", resource:"publicRecord",
            action:"save", absolute:true, params:afterPickupParams)

        println "REAL AFTER PICKUP IN TRYCALL: $afterPickup"

        //TODO: remove this
        String fakeAfterPickup = "https://08a91b1b.ngrok.io/v1/public/records?"
        afterPickupParams.each { k, v -> fakeAfterPickup += "$k=$v&" }


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
    boolean teamPhoneExistsForNum(String num) {
        TeamPhone.forTeamNumber(Helpers.cleanNumber(num)).get() != null
    }

    @Transactional(readOnly=true)
    boolean staffPhoneExistsForNum(String num) {
        StaffPhone.forStaffNumber(Helpers.cleanNumber(num)).get() != null
    }

    ///////////////
    // Voicemail //
    ///////////////

    Result<List<RecordCall>> storeVoicemail(String apiId, String callStatus,
        Integer callDuration, String voicemailUrl, int voicemailDuration) {
        Result<List<RecordItemReceipt>> res = recordService.updateCallStatus(apiId,
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

    Result<Closure> connectToPhone(String from, String to, String apiId) {
        Phone phone = Phone.forNumber(Helpers.cleanNumber(to)).get()
        if (phone) { connectToPhone(from, phone, apiId) }
        else { twimlBuilder.buildXmlFor(CallResponse.DEST_NOT_FOUND, [num:to]) }
    }
    Result<Closure> connectToPhone(String from, Phone toPhone, String apiId) {
        PhoneNumber fromNum = new PhoneNumber(number:from)
        Result res = recordService.createIncomingRecordCall(fromNum, toPhone, [apiId:apiId])
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

    //////////////////////////////////////////////////////////////////
    // Incoming call from staff from personal phone to TextUp phone //
    //////////////////////////////////////////////////////////////////

    Result<String> handleOutgoingCallOrContactCode(String apiId, String workNum, String numOrCode) {
        Phone phone = Phone.forNumber(Helpers.cleanNumber(workNum)).get()
        if (phone) {
            PhoneNumber pNum = new PhoneNumber(number:numOrCode)
            if (pNum.validate()) { //then is a valid phone number
                Result res = recordService.createOutgoingRecordCall(phone, pNum, [apiId:apiId])
                if (res.success) {  resultFactory.success(pNum.number) }
                else { res }
            }
            else if (numOrCode.isLong() && Contact.exists(numOrCode.toLong())) {
                Contact contact = Contact.forPhoneAndContactId(phone, numOrCode.toLong()).get()
                if (contact) {
                    String to = contact.numbers[0]?.number
                    Result res = recordService.createRecordCallForContact(contact.id, workNum, to,
                        null, [apiId:apiId])
                    if (res.success) {  resultFactory.success(to) }
                    else { res }
                }
                else {
                    resultFactory.failWithMessageAndStatus(FORBIDDEN,
                        "callService.handleOutgoingCallOrContactCode.contactForbidden",
                        [phone.id, contact.id])
                }
            }
            else {
                resultFactory.failWithMessageAndStatus(BAD_REQUEST,
                    "callService.handleOutgoingCallOrContactCode.neitherNumNorCode", [numOrCode])
            }
        }
        else {
            resultFactory.failWithMessageAndStatus(NOT_FOUND,
                "callService.handleOutgoingCallOrContactCode.phoneNotFound", [workNum])
        }
    }

    //////////////////////////
    // Calling a team phone //
    //////////////////////////

    Result<Closure> handleCallToTeamPhone(String from, String to, String apiId) {
        TeamPhone p1 = TeamPhone.forTeamNumber(Helpers.cleanNumber(to)).get()
        Result res = twimlBuilder.buildXmlFor(CallResponse.DEST_NOT_FOUND, [num:to])
        if (p1) {
            Team t = Team.forPhone(p1).get()
            if (t) {
                //store record of this incoming call to team phone
                PhoneNumber fromNum = new PhoneNumber(number:from)
                res = recordService.createIncomingRecordCall(fromNum, p1, [apiId:apiId])
                if (res.success) {
                    ClientSession ts1 = ClientSession.findOrCreateForTeamPhoneAndNumber(p1, from)
                    if (ts1) { 
                        res = twimlBuilder.buildXmlFor(CallResponse.TEAM_GREETING, [teamName:t.name, isSubscribed:ts1.hasCallSubscriptions()])
                    }
                    else {
                        res = twimlBuilder.buildXmlFor(CallResponse.SERVER_ERROR)
                    }
                }
            }
            else { log.error("CallService.handleCallToTeamPhone: No team found for $p1") }
        }
        res
    }
    Result<Closure> handleDigitsToTeamPhone(String from, String to, String digits) {
        TeamPhone phone = TeamPhone.forTeamNumber(Helpers.cleanNumber(to)).get()
        Result res = twimlBuilder.buildXmlFor(CallResponse.DEST_NOT_FOUND, [num:to])
        if (phone) {
            ClientSession ts1 = ClientSession.findOrCreateForTeamPhoneAndNumber(p1, from)
            if (!ts1) { return twimlBuilder.buildXmlFor(CallResponse.SERVER_ERROR) }
            List<Contact> contacts = Contact.findOrCreateForPhoneAndNum(p1, cleanFrom)
            if (!contacts) { return twimlBuilder.buildXmlFor(CallResponse.SERVER_ERROR) }
            List<ContactTag> tags = p1.tags
            //connect to staff case redirected in PublicRecordController
            switch(digits) {
                case Constants.CALL_GREETING_HEAR_ANNOUNCEMENTS:
                    List<FeaturedAnnouncement> features = p1.currentFeatures
                    if (features) {
                        twimlBuilder.buildXmlFor(CallResponse.TEAM_ANNOUNCEMENTS, [features:features])
                    }
                    else { twimlBuilder.buildXmlFor(CallResponse.TEAM_NO_ANNOUNCEMENTS) }
                    break
                case Constants.CALL_GREETING_SUBSCRIBE_ALL:
                    contacts.each { Contact c1 ->
                        tags.each { ContactTag t1 ->
                            c1.subscribeToTag(t1, Constants.SUBSCRIPTION_CALL)
                        }
                    }
                    res = twimlBuilder.buildXmlFor(CallResponse.TEAM_SUBSCRIBE_ALL)
                    break
                case Constants.CALL_GREETING_UNSUBSCRIBE_ALL:
                    contacts.each { Contact c1 ->
                        tags.each { ContactTag t1 ->
                            c1.quietUnsubscribeFromTag(t1)
                        }
                    }
                    res = twimlBuilder.buildXmlFor(CallResponse.TEAM_UNSUBSCRIBE_ALL)
                    break
                default:
                    res = twimlBuilder.buildXmlFor(CallResponse.TEAM_ERROR, [digits:digits])
                    break
            }
        }
        res
    }
}
