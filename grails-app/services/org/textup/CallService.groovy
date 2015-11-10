package org.textup

import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.ObjectMetadata
import com.amazonaws.services.s3.model.PutObjectResult
import com.twilio.sdk.resource.factory.CallFactory
import com.twilio.sdk.resource.instance.Call
import com.twilio.sdk.resource.list.RecordingList
import com.twilio.sdk.TwilioRestClient
import grails.transaction.Transactional
import org.codehaus.groovy.grails.web.mapping.LinkGenerator
import org.hibernate.FlushMode
import org.springframework.beans.factory.annotation.Autowired
import org.textup.rest.TwimlBuilder
import static org.springframework.http.HttpStatus.*

@Transactional
class CallService {

    @Autowired
    LinkGenerator linkGenerator
	def grailsApplication
	def resultFactory
    def twimlBuilder
    def recordService
    def s3Service
    def lockService

    //////////////////
    // Call methods //
    //////////////////

    Result<RecordCall> call(Phone fromPhone, Contactable toContact, RecordCall call) {
        if (call.validate()) {
            stopOnSuccessOrInternalError(call, fromPhone.number.e164PhoneNumber, toContact.numbers*.e164PhoneNumber)
        }
        else { resultFactory.failWithValidationErrors(call.error) }
	}

    Result<RecordCall> retry(Long recordCallId) {
        RecordCall call = RecordCall.get(recordCallId)
        if (call) {
            Contact contact = Contact.forRecord(call.record).get()
            if (contact) {
                lockService.retryCall(call, contact, this.&stopOnSuccessOrInternalError)
            }
            else {
                resultFactory.failWithMessage("callService.retry.contactNotFoundForCall", [call.id])
            }
        }
        else { resultFactory.failWithMessage("callService.retry.callNotFound", [recordCallId]) }
    }

    /////////////////////
    // Webhook methods //
    /////////////////////

    @Transactional(readOnly=true)
    boolean teamPhoneExistsForNum(String num) {
        TeamPhone.forTeamNumber(Helpers.cleanNumber(num)).get() != null
    }

    @Transactional(readOnly=true)
    boolean staffPhoneExistsForNum(String num) {
        StaffPhone.forStaffNumber(Helpers.cleanNumber(num)).get() != null
    }

    /*
    Voicemail
     */

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

    /*
    Determins if should connect incoming call to phone or redirect to voicemail
     */

    Result<Closure> connectToPhone(String from, String to, String apiId) {
        Phone phone = Phone.forNumber(Helpers.cleanNumber(to)).get()
        if (phone) { connectToPhone(from, phone, apiId) }
        else { twimlBuilder.buildXmlFor(TwimlBuilder.CALL_DEST_NOT_FOUND, [num:to]) }
    }
    Result<Closure> connectToPhone(String from, Phone toPhone, String apiId) {
        PhoneNumber fromNum = new PhoneNumber(number:from)
        Result res = recordService.createIncomingRecordCall(fromNum, toPhone, [apiId:apiId])
        if (res.success) {
            res = getNumbersToCallIfAvailable(toPhone)
            if (res.success && res.payload instanceof Collection) {
                List<String> numsToCall = res.payload
                res = twimlBuilder.buildXmlFor(TwimlBuilder.CALL_CONNECTING,
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
                else { twimlBuilder.buildXmlFor(TwimlBuilder.CALL_VOICEMAIL) }
            }
            else {
                log.error('''CallService.connectToPhone: staff not found for staff
                    phone $phone with ownerId ${phone.ownerId}.''')
                return twimlBuilder.buildXmlFor(TwimlBuilder.CALL_SERVER_ERROR)
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
                else { twimlBuilder.buildXmlFor(TwimlBuilder.CALL_VOICEMAIL) }
            }
            else {
                log.error("CallService.connectToPhone: team not found for team phone $phone.")
                return twimlBuilder.buildXmlFor(TwimlBuilder.CALL_SERVER_ERROR)
            }
        }
        else {
            log.error("CallService.connectToPhone: phone $phone is not a staff or team phone.")
            twimlBuilder.buildXmlFor(TwimlBuilder.CALL_SERVER_ERROR)
        }
    }

    /*
    Handles digit input when staff calls self
     */

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

    /*
    Calling a team phone
     */

    Result<Closure> handleCallToTeamPhone(String from, String to, String apiId) {
        TeamPhone phone = TeamPhone.forTeamNumber(Helpers.cleanNumber(to)).get()
        Result res = twimlBuilder.buildXmlFor(TwimlBuilder.CALL_DEST_NOT_FOUND, [num:to])
        if (phone) {
            Team t = Team.forPhone(phone).get()
            if (t) {
                //store record of this incoming call to team phone
                PhoneNumber fromNum = new PhoneNumber(number:from)
                res = recordService.createIncomingRecordCall(fromNum, phone, [apiId:apiId])
                if (res.success) {
                    List<String> directions = getTeamDirectionsFromTags(t.phone.tags)
                    res = twimlBuilder.buildXmlFor(TwimlBuilder.CALL_TEAM_GREETING,
                    [teamName:t.name, teamDirections:directions.join(", "),
                    numDigits:getNumDigits(directions)])
                }
            }
            else { log.error("CallService.handleCallToTeamPhone: No team found for $phone") }
        }
        res
    }
    @Transactional(readOnly=true)
    Result<Closure> handleDigitsToTeamPhone(String from, String to, String digits) {
        TeamPhone phone = TeamPhone.forTeamNumber(Helpers.cleanNumber(to)).get()
        Result res = twimlBuilder.buildXmlFor(TwimlBuilder.CALL_DEST_NOT_FOUND, [num:to])
        if (phone) {
            Team t = Team.forPhone(phone).get()
            if (t) {
                List<String> directions = getTeamDirectionsFromTags(t.phone.tags)
                int dIndex = digits.isInteger() ? digits.toInteger() : -1,
                    numDigits = getNumDigits(directions)
                dIndex = TwimlBuilder.convertOptionNumberToTagIndex(dIndex)
                String dString = directions.join(", ")
                if (dIndex >= 0 && dIndex < directions.size()) {
                    TeamContactTag tag = t.phone.tags[dIndex]
                    RecordText text = tag.record.getTexts(outgoing:true, futureText:false, max:1)[0]
                    if (text) {
                        res = twimlBuilder.buildXmlFor(TwimlBuilder.CALL_TEAM_TAG_MESSAGE,
                            [datePosted:text.dateCreated, message:text.contents,
                            teamDirections:dString, numDigits:numDigits])
                    }
                    else {
                        res = twimlBuilder.buildXmlFor(TwimlBuilder.CALL_TEAM_TAG_NONE,
                            [tagName:tag.name, teamDirections:dString, numDigits:numDigits])
                    }
                }
                else {
                    res = twimlBuilder.buildXmlFor(TwimlBuilder.CALL_TEAM_ERROR,
                        [digits:digits, teamDirections:dString, numDigits:getNumDigits(directions)])
                }
            }
            else { log.error("CallService.handleDigitsToTeamPhone: No team found for $phone") }
        }
        res
    }
    protected List<String> getTeamDirectionsFromTags(List<TeamContactTag> tagList) {
        List<String> directionList = []
        tagList.eachWithIndex { TeamContactTag tag, int index ->
            directionList << twimlBuilder.getMessage("twimlBuilder.teamDirection",
                [TwimlBuilder.convertTagIndexToOptionNumber(index), tag.name])
        }
        directionList
    }
    protected int getNumDigits(List<String> teamDirections) {
        "${teamDirections.size()}".size()
    }

    ////////////////////
    // Helper methods //
    ////////////////////

    protected Result<RecordCall> stopOnSuccessOrInternalError(RecordCall call, String from, List<String> toNums) {
        Result res = resultFactory.success(call)
        for (toNum in toNums) {
            res = this.tryCall(call, toNum, from)
            //return on first success or if 500-level error
            if (res.success || (!res.success && res.payload?.errorCode > 499)) {
                return res
            }
        }
        res //if we haven't already returned, return last obtained result
    }

    protected Result<RecordCall> tryCall(RecordCall call, String to, String from) {
        String callback = linkGenerator.link(namespace:"v1", resource:"publicRecord",
            action:"save", absolute:true, params:[handle:Constants.CALL_STATUS])
        RecordCall.withNewSession { session ->
            session.flushMode = FlushMode.MANUAL
            try {
                Result<Call> res = makeCallHelper(to, from, callback)
                if (res.success) {
                    Call c = res.payload
                    RecordItemReceipt receipt = new RecordItemReceipt(apiId:c.sid)
                    receipt.setReceivedByAsString = to
                    call.addToReceipts(receipt)
                    if (receipt.save()) {
                        if (call.save()) { resultFactory.success(call) }
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

    ////////////////////
    // Twilio methods //
    ////////////////////

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

    protected Result<Call> makeCallHelper(String to, String from, String callback) {
        def twilioConfig = grailsApplication.config.textup.apiKeys.twilio
        try {
            TwilioRestClient client = new TwilioRestClient(twilioConfig.sid, twilioConfig.authToken)
            CallFactory cFactory = client.account.callFactory
            cFactory.create(To:to, From:from, Url:callback)
        }
        catch (Throwable e) {
            log.error("CallService.makeCallHelper: ${e.message}")
            resultFactory.failWithThrowable(e)
        }
    }
}
