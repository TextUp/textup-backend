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
import org.hibernate.FlushMode
import org.hibernate.Session
import org.springframework.context.i18n.LocaleContextHolder as LCH
import org.springframework.context.MessageSource
import org.springframework.transaction.TransactionStatus
import org.textup.rest.TwimlBuilder
import org.textup.types.CallResponse
import org.textup.types.ContactStatus
import org.textup.types.RecordItemType
import org.textup.types.ResultType
import org.textup.types.TextResponse
import org.textup.validator.IncomingText
import org.textup.validator.OutgoingText
import org.textup.validator.PhoneNumber
import org.textup.validator.TempRecordReceipt
import static org.springframework.http.HttpStatus.*

@GrailsTypeChecked
@Transactional
class PhoneService {

    SocketService socketService
    TwimlBuilder twimlBuilder
    AmazonS3Client s3Service
    AuthService authService
    CallService callService
    GrailsApplication grailsApplication
    MessageSource messageSource
    ResultFactory resultFactory
    TextService textService
    TwilioRestClient twilioService

    // Numbers
    // -------

    Result<Phone> update(Phone p1, Map body) {
        if (body.awayMessage) {
            p1.awayMessage = body.awayMessage
        }
        boolean isActive = false
        Phone.withNewSession { Session session ->
            session.flushMode = FlushMode.MANUAL
            try {
                isActive = authService.isActive
            }
            finally { session.flushMode = FlushMode.AUTO }
        }
        if (isActive && (body.number || body.newApiId)) {
            Result<Phone> res
            if (body.number) {
                PhoneNumber pNum = new PhoneNumber(number:body.number as String)
                res = this.updatePhoneForNumber(p1, pNum)
            }
            else {
                res = this.updatePhoneForApiId(p1, body.newApiId as String)
            }
            res.then({
                if (p1.save()) {
                    resultFactory.success(p1)
                }
                else { resultFactory.failWithValidationErrors(p1.errors) }
            }, { ResultType type, Object payload ->
                Phone.withTransaction { TransactionStatus status ->
                    status.setRollbackOnly()
                }
                resultFactory.duplicate(type, payload)
            }) as Result<Staff>
        }
        else { resultFactory.success(p1) }
    }

    protected Result<Phone> updatePhoneForNumber(Phone p1, PhoneNumber pNum) {
        if (!pNum.validate()) {
            return resultFactory.failWithValidationErrors(pNum.errors)
        }
        if (pNum.number == p1.numberAsString) {
            return resultFactory.success(p1)
        }
        boolean isDuplicate = false
        Phone.withNewSession { Session session ->
            session.flushMode = FlushMode.MANUAL
            try {
                isDuplicate = (Phone.countByNumberAsString(pNum.number) > 0)
            }
            finally { session.flushMode = FlushMode.AUTO }
        }
        if (isDuplicate) {
            return resultFactory.failWithMessageAndStatus(UNPROCESSABLE_ENTITY,
                "phoneService.changeNumber.duplicate")
        }
        Result.<Phone>waterfall(
            this.&changeForNumber.curry(pNum),
            this.&updatePhoneWithNewNumber.rcurry(p1)
        )
    }

    protected Result<Phone> updatePhoneForApiId(Phone p1, String apiId) {
        if (apiId == p1.apiId) {
            return resultFactory.success(p1)
        }
        boolean isDuplicate = false
        Phone.withNewSession { Session session ->
            session.flushMode = FlushMode.MANUAL
            try {
                isDuplicate = (Phone.countByApiId(apiId) > 0)
            }
            finally { session.flushMode = FlushMode.AUTO }
        }
        if (isDuplicate) {
            return resultFactory.failWithMessageAndStatus(UNPROCESSABLE_ENTITY,
                'phoneService.changeNumber.duplicate')
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
            List<NameValuePair> params = getBasicParams()
            newNum.update(params)
            resultFactory.success(newNum)
        }
        catch (TwilioRestException e) {
            return resultFactory.failWithThrowable(e)
        }
    }
    protected Result<Phone> updatePhoneWithNewNumber(IncomingPhoneNumber newNum,
        Phone p1) {
        String oldApiId = p1.apiId
        p1.apiId = newNum.sid
        p1.number = new PhoneNumber(number:newNum.phoneNumber as String)
        if (oldApiId) {
            freeExistingNumber(oldApiId).then({
                resultFactory.success(p1)
            }) as Result<Phone>
        }
        else { resultFactory.success(p1) }
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
        HashSet<Contactable> recipients = text.toRecipients()
        Map<Long, List<TempRecordReceipt>> contactIdToReceipts = [:]
            .withDefault { [] as List<TempRecordReceipt> }
        // call sendText on each contactable
        recipients.each { Contactable c1 ->
            if (c1.instanceOf(SharedContact)) {
                resList << sendTextToSharedContact(c1 as SharedContact, text, staff)
            }
            else if (c1.instanceOf(Contact)) {
                resList << sendTextToContact(phone, c1 as Contact, text, staff, contactIdToReceipts)
            }
            else {
                log.error("PhoneService.sendText: contactable '${c1}' not a SharedContact or a Contact")
            }
        }
        // record all receipts on each tag, if applicable
        text.tags.each { ContactTag ct1 ->
            resList << storeTextInTag(ct1, text, staff, contactIdToReceipts)
        }
        resList
    }
    Result<RecordCall> startBridgeCall(Phone phone, Contactable c1, Staff staff) {
        PhoneNumber fromNum = (c1 instanceof SharedContact) ? c1.sharedBy.number : phone.number,
            toNum = staff.personalPhoneNumber
        Map afterPickup = [contactId:c1.contactId, handle:CallResponse.CONFIRM_BRIDGE]
        callService.start(fromNum, toNum, afterPickup).then({ TempRecordReceipt receipt ->
            c1.storeOutgoingCall(receipt, staff)
        }) as Result
    }
    ResultMap<TempRecordReceipt> sendTextAnnouncement(Phone phone, String message,
        String identifier, List<IncomingSession> sessions, Staff staff) {
        Result<List<String>> res = twimlBuilder.translate(TextResponse.ANNOUNCEMENT,
            [identifier:identifier, message:message])
            .logFail("PhoneService.sendTextAnnouncement")
        String announcement = res.success ? res.payload[0] : "$identifier: $message"
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

    // Outgoing helper methods
    // -----------------------

    protected Result<RecordText> sendTextToContact(Phone phone, Contact c1, OutgoingText text,
        Staff staff, Map<Long, List<TempRecordReceipt>> contactIdToReceipts) {
        textService.send(phone.number, c1.numbers, text.message)
            .then({ TempRecordReceipt receipt ->
                Result<RecordText> res = c1.storeOutgoingText(text.message, receipt, staff)
                if (res.success) {
                    (contactIdToReceipts[c1.id] as List<TempRecordReceipt>) << receipt
                }
                res
            }) as Result<RecordText>
    }
    protected Result<RecordText> sendTextToSharedContact(SharedContact sc1,
        OutgoingText text, Staff staff) {
        textService.send(sc1.sharedBy.number, sc1.numbers, text.message)
            .then({ TempRecordReceipt receipt ->
                sc1.storeOutgoingText(text.message, receipt, staff)
            }) as Result<RecordText>
    }
    protected Result<RecordText> storeTextInTag(ContactTag ct1, OutgoingText text, Staff staff,
        Map<Long, List<TempRecordReceipt>> contactIdToReceipts) {
        // create a new text on the tag's record
        ct1.addTextToRecord([contents:text.message], staff).then({ RecordText tagText ->
            ct1.members.each { Contact c1 ->
                // add contact text's receipts to tag's text
                contactIdToReceipts[c1.id]?.each { TempRecordReceipt r ->
                    tagText.addReceipt(r)
                    tagText.save()
                }
            }
            resultFactory.success(tagText)
        }) as Result<RecordText>
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
            HashSet<Staff> availableNow = getAllAvailableForContacts(contacts, phone)
            // if none of the staff for any of the phones are available
            if (availableNow) {
                String nameOrNumber = getNameOrNumber(contacts, session)
                availableNow.each { Staff s1 ->
                    this.notifyStaff(s1, nameOrNumber)
                        .logFail("PhoneService.relayText: notify staff ${s1.id}")
                }
            }
            else {
                rTexts.each { RecordText rText -> rText.hasAwayMessage = true }
                responses << phone.awayMessage
            }
            // remind about instructions if phone has announcements enabled
            if (phone.getAnnouncements() && session.shouldSendInstructions) {
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
            // For outgoing messages and all calls, we rely on status callbacks
            // to push record items to the frontend. However, for incoming texts
            // no status callback happens so we need to push the item here
            socketService.sendItems(rTexts as List<RecordItem>)
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
            HashSet<Staff> availableNow = getAllAvailableForContacts(contacts, phone)
            HashSet<String> numsToCall = new HashSet<>()
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
                    List<FeaturedAnnouncement> announces = phone.getAnnouncements()
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
        else if (phone.getAnnouncements()) {
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

    // Incoming helper methods
    // -----------------------

    protected HashSet<Staff> getAllAvailableForContacts(List<Contact> contacts, Phone phone) {
        List<Long> cIds = contacts.collect { it.id }
        List<Phone> sharedWithPhones = SharedContact
            .findByContactIdsAndSharedBy(cIds, phone)
            .collect { it.sharedWith }
        HashSet<Staff> availableNow = new HashSet<>(phone.availableNow)
        sharedWithPhones.each { Phone p1 -> availableNow.addAll(p1.availableNow) }
        availableNow
    }
    protected Result notifyStaff(Staff s1, String identifier) {
        String msg = messageSource.getMessage('phoneService.notifyStaff.notification',
            [Helpers.formatNumberForRead(identifier)] as Object[], LCH.getLocale())
        textService.send(s1.phone.number, [s1.personalPhoneNumber], msg)
    }
    protected String getNameOrNumber(List<Contact> contacts, IncomingSession session) {
        for (contact in contacts) {
            if (contact.name) { return contact.name }
        }
        return Helpers.formatNumberForSay(session.numberAsString)
    }
}
