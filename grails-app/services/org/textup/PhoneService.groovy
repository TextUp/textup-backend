package org.textup

import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.ObjectMetadata
import com.amazonaws.services.s3.model.PutObjectResult
import com.twilio.sdk.resource.factory.CallFactory
import com.twilio.sdk.resource.instance.Account
import com.twilio.sdk.resource.instance.Call
import com.twilio.sdk.resource.instance.IncomingPhoneNumber
import com.twilio.sdk.resource.list.RecordingList
import com.twilio.sdk.TwilioRestClient
import com.twilio.sdk.TwilioRestException
import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import org.apache.http.message.BasicNameValuePair
import org.apache.http.NameValuePair
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.textup.rest.TwimlBuilder
import org.textup.types.CallResponse
import org.textup.types.ContactStatus
import org.textup.types.RecordItemType
import org.textup.types.TextResponse
import org.textup.validator.IncomingText
import org.textup.validator.OutgoingText
import org.textup.validator.PhoneNumber
import org.textup.validator.TempRecordReceipt
import static org.springframework.http.HttpStatus.*

@GrailsTypeChecked
@Transactional
class PhoneService {

    GrailsApplication grailsApplication
	TwimlBuilder twimlBuilder
	SocketService socketService
    TextService textService
    CallService callService
    ResultFactory resultFactory
    AmazonS3Client s3Service
    TwilioRestClient twilioService

    // Numbers
    // -------

    Result<Phone> updatePhoneForNumber(Phone p1, PhoneNumber pNum) {
        if (!pNum.validate()) {
            return resultFactory.failWithValidationErrors(pNum.errors)
        }
        if (pNum.number == p1.numberAsString) {
            return resultFactory.success(p1)
        }
        Result.<Phone>waterfall(
            this.&changeForNumber.curry(pNum),
            this.&updatePhoneWithNewNumber.rcurry(p1)
        )
    }

    Result<Phone> updatePhoneForApiId(Phone p1, String apiId) {
        if (apiId == p1.apiId) {
            return resultFactory.success(p1)
        }
        Result.<Phone>waterfall(
            this.&changeForApiId.curry(apiId),
            this.&updatePhoneWithNewNumber.rcurry(p1)
        )
    }

    protected Result<IncomingPhoneNumber> changeForNumber(PhoneNumber pNum) {
        try {
            Account ac = twilioService.account
            List<NameValuePair> params = getBasicParams()
            params.add(new BasicNameValuePair("PhoneNumber", pNum.e164PhoneNumber)
                as NameValuePair)
            resultFactory.success(ac.incomingPhoneNumberFactory.create(params))
        }
        catch (TwilioRestException e) {
            return resultFactory.failWithThrowable(e)
        }
    }
    protected Result<IncomingPhoneNumber> changeForApiId(String newApiId) {
        try {
            Account ac = twilioService.account
            IncomingPhoneNumber newNum = ac.getIncomingPhoneNumber(newApiId as String)
            newNum.update(getBasicParams())
            resultFactory.success(newNum)
        }
        catch (TwilioRestException e) {
            return resultFactory.failWithThrowable(e)
        }
    }
    protected Result<Phone> updatePhoneWithNewNumber(IncomingPhoneNumber newNum,
        Phone p1) {
        p1.apiId = newNum.sid
        p1.numberAsString = newNum.phoneNumber
        if (p1.save()) {
            if (p1.apiId) {
                freeExistingNumber(p1.apiId).then({
                    resultFactory.success(p1)
                }) as Result<Phone>
            }
            else { resultFactory.success(p1) }
        }
        else {
            resultFactory.failWithValidationErrors(p1.errors)
        }
    }
    protected Result<IncomingPhoneNumber> freeExistingNumber(String oldApiId) {
        String available = grailsApplication.flatConfig["textup.apiKeys.twilio.available"]
        try {
            Account ac = twilioService.account
            List<NameValuePair> params = getBasicParams()
            IncomingPhoneNumber currNum = ac.getIncomingPhoneNumber(oldApiId)
            currNum.update([new BasicNameValuePair("FriendlyName", available)
                as NameValuePair])
            resultFactory.success(currNum)
        }
        catch (TwilioRestException e) {
            return resultFactory.failWithThrowable(e)
        }
    }
    protected List<NameValuePair> getBasicParams() {
        String unavailable = grailsApplication.flatConfig["textup.apiKeys.twilio.unavailable"],
            available = grailsApplication.flatConfig["textup.apiKeys.twilio.available"],
            appId = grailsApplication.flatConfig["textup.apiKeys.twilio.appId"]
        List<NameValuePair> params = []
        params.with {
            add(new BasicNameValuePair("FriendlyName", unavailable))
            add(new BasicNameValuePair("SmsApplicationSid", appId))
            add(new BasicNameValuePair("VoiceApplicationSid", appId))
        }
        params
    }

    // Outgoing
    // --------

    ResultList<RecordText> sendText(Phone phone, OutgoingText text, Staff staff) {
        ResultList<RecordText> resList = new ResultList<>()
        // define datastructures
        Map<Long, HashSet<Contact>> tagIdToContacts = [:]
        Map<Long, List<TempRecordReceipt>> contactIdToReceipts = [:]
        HashSet<Contactable> recipients = new HashSet<>()
        // build map of tags to members
        text.tags.each { ContactTag ct1 ->
            tagIdToContacts[ct1.id] = new HashSet<Contact>(ct1.members)
        }
        // add all contactables to a hashset to avoid duplication
        recipients.addAll(text.contacts)
        recipients.addAll(text.sharedContacts)
        tagIdToContacts.values().each { recipients.addAll(it) }
        Integer maxNumRecip = Helpers.toInteger(grailsApplication.flatConfig["textup.maxNumText"])
        if (recipients.size() > maxNumRecip) {
            return (resList << resultFactory.failWithMessage("phone.sendText.tooMany"))
        }
        // call sendText on each contactable
        recipients.each { Contactable c1 ->
            textService.send(phone.number, c1.numbers,
                text.message).then({ TempRecordReceipt receipt ->
                Result<RecordText> res = c1.storeOutgoingText(text.message, receipt, staff)
                // only contacts (NOT shared) can be member of a tag
                if (res.success && c1.instanceOf(Contact)) {
                    Contact cont = c1 as Contact
                    if (contactIdToReceipts.containsKey(cont)) {
                        (contactIdToReceipts[cont.id] as List<TempRecordReceipt>) << receipt
                    }
                    else { contactIdToReceipts[cont.id] = [receipt] }

                }
                resList << res
            })
        }
        // record all receipts on each tag, if applicable
        text.tags.each { ContactTag ct1 ->
            // create a new text on the tag's record
            Result<RecordText> res = ct1.addTextToRecord([contents:text.message], staff)
            if (res.success) {
                RecordText tagText = res.payload
                tagIdToContacts[ct1.id]?.each { Contact c1 ->
                    // add contact text's receipts to tag's text
                    contactIdToReceipts[c1.id]?.each { TempRecordReceipt r ->
                        tagText.addReceipt(r)
                        tagText.save()
                    }
                }
            }
            resList << res
        }
        resList
    }
    Result<RecordCall> startBridgeCall(Phone phone, Contactable c1, Staff staff) {
        PhoneNumber fromNum = phone.number,
            toNum = staff.personalPhoneNumber
        Map afterPickup = [contactId:c1.contactId, handle:CallResponse.CONFIRM_BRIDGE]
        callService.start(fromNum, toNum, afterPickup).then({ TempRecordReceipt receipt ->
            c1.storeOutgoingCall(receipt, staff)
        }) as Result
    }
    ResultMap<TempRecordReceipt> sendTextAnnouncement(Phone phone, String message,
        String identifier, List<IncomingSession> sessions, Staff staff) {
        String announcement = "$identifier: $message"
        startAnnouncement(phone, sessions, { IncomingSession s1 ->
            textService.send(phone.number, [s1.number], announcement)
        }, { Contact c1, TempRecordReceipt receipt ->
            c1.storeOutgoingText(announcement, receipt, staff)
        })
    }
    ResultMap<TempRecordReceipt> startCallAnnouncement(Phone phone, String message,
        String identifier, List<IncomingSession> sessions, Staff staff) {
        startAnnouncement(phone, sessions, { IncomingSession s1 ->
            callService.start(phone.number, s1.number,
                [handle:CallResponse.ANNOUNCEMENT_AND_DIGITS,
                    message:message, identifier:identifier])
        }, { Contact c1, TempRecordReceipt receipt ->
            c1.storeOutgoingCall(receipt, staff)
        })
    }
    protected ResultMap<TempRecordReceipt> startAnnouncement(Phone phone,
        List<IncomingSession> sessions, Closure receiptAction,
        Closure addToRecordAction) {
        ResultMap<TempRecordReceipt> resMap = new ResultMap<>()
        Map<String,TempRecordReceipt> numberAsStringToReceipt = [:]
        sessions.each { IncomingSession s1 ->
            Result<TempRecordReceipt> res = receiptAction(s1) as Result<TempRecordReceipt>
            if (res.success) {
                TempRecordReceipt receipt = res.payload
                numberAsStringToReceipt[s1.numberAsString] = receipt
            }
            resMap[s1.numberAsString] = res
        }
        // find contacts that have the same number as this session, if any
        // if no contacts share this number, we will create a new contact
        Map<String,List<Contact>> numAsStringToContacts =
            ContactNumber.getContactsForPhoneAndNumbers(phone,
                numberAsStringToReceipt.keySet())
        List<Contact> newContacts = []
        numAsStringToContacts.each { String numAsString, List<Contact> contacts ->
            TempRecordReceipt receipt = numberAsStringToReceipt[numAsString]
            if (!contacts) { // create new contact if none found for session
                phone.createContact([:], [numAsString])
                    .logFail("Phoneservice.sendTextAnnouncement: create contact")
                    .then({ Contact newC ->
                        newContacts << newC
                        contacts << newC
                    })
            }
            if (receipt) {
                contacts.each { Contact c1 ->
                    (addToRecordAction(c1, receipt) as Result<RecordItem>)
                        .logFail("PhoneService.sendTextAnnouncement: add to record")
                        .then({ RecordItem item ->
                            item.isAnnouncement = true
                        })
                }
            }
            else {
                log.error("PhoneService.sendTextAnnouncement: \
                    no receipt for $numAsString")
            }
        }
        // notify of new contacts
        socketService.sendContacts(newContacts)
        //return result map
        resMap
    }

	// Incoming
	// --------

    Result<Closure> relayText(Phone phone, IncomingText text, IncomingSession session) {
        List<RecordText> rTexts = []
        storeForNumber(phone, session.number, { Contact c1 ->
            Result<RecordText> res = c1.storeIncomingText(text, session)
                .logFail("PhoneService.relayText: store text for contact ${c1.id}")
            if (res.success) { rTexts << res.payload }
        }).then({ List<Contact> contacts ->
            // notify available staff members
            List responses = [] // list of string and text resonses
            String nameOrNumber = getNameOrNumber(contacts, session)
            List<Staff> availableNow = phone.availableNow
            if (availableNow) {
                availableNow.each { Staff s1 ->
                    this.notifyStaff(s1, text, nameOrNumber)
                        .logFail("PhoneService.relayText: notify staff ${s1.id}")
                }
            }
            else {
                rTexts.each { RecordText rText ->
                    rText.hasAwayMessage = true
                }
                responses << phone.awayMessage
            }
            // remind about instructions if phone has announcements enabled
            if (phone.announcements && session.shouldSendInstructions) {
                session.updateLastSentInstructions()
                TextResponse code = session.isSubscribedToText ?
                    TextResponse.INSTRUCTIONS_SUBSCRIBED :
                    TextResponse.INSTRUCTIONS_UNSUBSCRIBED
                Result<List<String>> res = twimlBuilder.translate(code)
                if (res.success) {
                    responses += res.payload
                }
                else { return res }
            }
            twimlBuilder.buildTexts(responses)
        }) as Result
    }
    Result<Closure> relayCall(Phone phone, String apiId, IncomingSession session) {
        List<RecordCall> rCalls = []
        storeForNumber(phone, session.number, { Contact c1 ->
            Result<RecordCall> res = c1.storeIncomingCall(apiId, session)
                .logFail("PhoneService.relayCall")
            if (res.success) {
                rCalls << res.payload
            }
        }).then({ List<Contact> contacts ->
            // notify available staff members
            List<Staff> availableNow = phone.availableNow
            List<String> numsToCall = []
            availableNow.each { Staff s1 ->
                if (s1.personalPhoneAsString) {
                    numsToCall << s1.personalPhoneNumber.e164PhoneNumber
                }
            }
            if (numsToCall) {
                String nameOrNumber = getNameOrNumber(contacts, session)
                twimlBuilder.build(CallResponse.CONNECT_INCOMING,
                    [nameOrNumber:nameOrNumber, numsToCall:numsToCall,
                        linkParams:[handle:CallResponse.VOICEMAIL]])
            }
            else {
                rCalls.each { RecordCall rCall ->
                    rCall.hasAwayMessage = true
                }
                twimlBuilder.build(CallResponse.VOICEMAIL,
                    [linkParams:[handle:CallResponse.VOICEMAIL]])
            }
        }) as Result
    }
    Result<List<Contact>> storeForNumber(Phone phone, PhoneNumber pNum,
        Closure contactStoreAction) {
        // create a new contact with this session's phone number if contact
        // does not already exist
        List<Contact> contacts = Contact.listForPhoneAndNum(phone, pNum)
        if (contacts.isEmpty()) {
            Result res = phone.createContact([:], [pNum.number])
            if (res.success) {
                contacts = [res.payload]
            }
            else { return res }
        }
        //add text to contact records
        contacts.each { Contact c1 ->
            contactStoreAction(c1)
            //only change status to unread if contact is NOT BLOCKED
            if (c1.status != ContactStatus.BLOCKED) {
                c1.status = ContactStatus.UNREAD
            }
            if (!c1.save()) {
                return resultFactory.failWithValidationErrors(c1.errors)
            }
        }
        // socket notify of new contact and/or unread contacts
        socketService.sendContacts(contacts)
        resultFactory.success(contacts)
    }
    Result<Closure> handleAnnouncementCall(Phone phone, String apiId, String digits,
        IncomingSession session) {
        if (digits) {
            switch(digits) {
                case Constants.CALL_HEAR_ANNOUNCEMENTS:
                    List<FeaturedAnnouncement> announces = phone.announcements
                    announces.each { FeaturedAnnouncement announce ->
                        announce.addToReceipts(RecordItemType.CALL, session)
                            .logFail("Phone.handleAnnouncementCall: add announce receipt")
                    }
                    twimlBuilder.build(CallResponse.HEAR_ANNOUNCEMENTS,
                        [announcements:announces, isSubscribed:session.isSubscribedToCall])
                    break
                case Constants.CALL_TOGGLE_SUBSCRIBE:
                    if (session.isSubscribedToCall) {
                        session.isSubscribedToCall = false
                        twimlBuilder.build(CallResponse.UNSUBSCRIBED)
                    }
                    else {
                        session.isSubscribedToCall = true
                        twimlBuilder.build(CallResponse.SUBSCRIBED)
                    }
                    break
                default:
                    this.relayCall(phone, apiId, session)
            }
        }
        else if (phone.announcements) {
            twimlBuilder.build(CallResponse.ANNOUNCEMENT_GREETING,
                [name:phone.owner.name, isSubscribed:session.isSubscribedToCall])
        }
        else {
            this.relayCall(phone, apiId, session)
        }
    }
    Result<Closure> handleSelfCall(Phone phone, String apiId, String digits, Staff staff) {
        if (digits) {
            PhoneNumber pNum = new PhoneNumber(number:digits)
            if (pNum.validate()) { //then is a valid phone number
                TempRecordReceipt receipt = new TempRecordReceipt(apiId:apiId)
                receipt.receivedBy = pNum
                if (receipt.validate()) {
                    storeForNumber(phone, pNum, { Contact c1 ->
                        c1.storeOutgoingCall(receipt, staff)
                            .logFail("PhoneService.handleSelfCall")
                    })
                    twimlBuilder.build(CallResponse.SELF_CONNECTING,
                        [numAsString:pNum.number])
                }
                else {
                    log.error("PhoneService.handleSelfCall: could not save \
                        receipt: ${receipt.errors}")
                    twimlBuilder.errorForCall()
                }
            }
            else {
                twimlBuilder.build(CallResponse.SELF_INVALID_DIGITS, [digits:digits])
            }
        }
        else {
            twimlBuilder.build(CallResponse.SELF_GREETING)
        }
    }
    Result<String> moveVoicemail(String apiId) {
        String bucketName = grailsApplication.flatConfig["textup.voicemailBucketName"]
        try {
            Call call = twilioService.account.getCall(apiId)
            if (call) {
                RecordingList recs = call.recordings
                ObjectMetadata metadata = new ObjectMetadata()
                metadata.setSSEAlgorithm(ObjectMetadata.AES_256_SERVER_SIDE_ENCRYPTION)
                metadata.setContentType("audio/mpeg")
                PutObjectResult putRes
                for (rec in recs) {
                    putRes = s3Service.putObject(bucketName, apiId,
                        rec.getMedia(".mp3"), metadata)
                    break //only put the first recording if multiple
                }
                //delete all recordings on Twilio
                for (rec in recs) { rec.delete() }
                resultFactory.success(putRes.getETag())
            }
            else {
                resultFactory.failWithMessageAndStatus(NOT_FOUND,
                    "phoneService.moveVoicemail.callNotFound")
            }
        }
        catch (e) {
            log.error("PhoneService.moveVoicemailToS3: ${e.message}")
            resultFactory.failWithThrowable(e)
        }
    }
    ResultList<RecordItemReceipt> storeVoicemail(String apiId, int voicemailDuration) {
        ResultList<RecordItemReceipt> resList = new ResultList<>()
        List<RecordItemReceipt> receipts = RecordItemReceipt.findAllByApiId(apiId)
        for (receipt in receipts) {
            RecordItem item = receipt.item
            if (item.instanceOf(RecordCall)) {
                RecordCall call = item as RecordCall
                call.hasVoicemail = true
                call.voicemailInSeconds = voicemailDuration
                call.record.updateLastRecordActivity()
                if (call.save() && call.record.save()) {
                    resList << resultFactory.success(receipt)
                }
                else if (!call.save()) {
                    resList << resultFactory.failWithValidationErrors(call.errors)
                }
                else {
                    resList << resultFactory.failWithValidationErrors(call.record.errors)
                }
            }
        }
        // send updated items with receipts through socket
        List<RecordItem> voicemailItems = resList.successes
            .collect { Result<RecordItemReceipt> res ->
                res.payload.item
            }
        socketService.sendItems(voicemailItems)

        resList
    }
    protected Result notifyStaff(Staff s1, IncomingText text, String identifier) {
        String notification = "${identifier}: ${text.message}"
        if (notification.size() >= Constants.TEXT_LENGTH) {
            int trimTo = Constants.TEXT_LENGTH - 4 // for the ellipses
            notification = "${notification[0..trimTo]}..."
        }
        textService.send(s1.phone.number, [s1.personalPhoneNumber], notification)
    }
    protected String getNameOrNumber(List<Contact> contacts, IncomingSession session) {
        for (contact in contacts) {
            if (contact.name) { return contact.name }
        }
        return Helpers.formatNumberForSay(session.numberAsString)
    }
}
