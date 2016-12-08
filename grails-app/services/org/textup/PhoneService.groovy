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
import org.textup.types.PhoneOwnershipType
import org.textup.types.RecordItemType
import org.textup.types.ResultType
import org.textup.types.TextResponse
import org.textup.validator.IncomingText
import org.textup.validator.OutgoingMessage
import org.textup.validator.PhoneNumber
import org.textup.validator.TempRecordReceipt
import static org.springframework.http.HttpStatus.*

@GrailsTypeChecked
@Transactional
class PhoneService {

    AmazonS3Client s3Service
    AuthService authService
    CallService callService
    GrailsApplication grailsApplication
    MessageSource messageSource
    ResultFactory resultFactory
    SocketService socketService
    TextService textService
    TokenService tokenService
    TwilioRestClient twilioService
    TwimlBuilder twimlBuilder

    // Update
    // ------

    Result<Staff> createOrUpdatePhone(Staff s1, Map body) {
        if (body.phone instanceof Map && checkIsActive()) {
            Phone p1 = s1.phoneWithAnyStatus ?: new Phone([:])
            p1.updateOwner(s1)
            this.update(p1, body.phone as Map).then({
                resultFactory.success(s1)
            }) as Result<Staff>
        }
        else { resultFactory.success(s1) }
    }
    Result<Team> createOrUpdatePhone(Team t1, Map body) {
        if (body.phone instanceof Map && checkIsActive()) {
            Phone p1 = t1.phoneWithAnyStatus ?: new Phone([:])
            p1.updateOwner(t1)
            this.update(p1, body.phone as Map).then({
                resultFactory.success(t1)
            }) as Result<Team>
        }
        else { resultFactory.success(t1) }
    }
    protected Result<Phone> update(Phone p1, Map body) {
        if (body.awayMessage) {
            p1.awayMessage = body.awayMessage
        }
        handlePhoneActions(p1, body).then({ Phone phone1 ->
            if (phone1.save()) {
                resultFactory.success(phone1)
            }
            else { resultFactory.failWithValidationErrors(phone1.errors) }
        }) as Result<Phone>
    }
    protected Result<Phone> handlePhoneActions(Phone p1, Map body) {
        if (!body.doPhoneActions) {
            return resultFactory.success(p1)
        }
        else if (!(body.doPhoneActions instanceof Collection)) {
            return resultFactory.failWithMessageAndStatus(BAD_REQUEST,
                "phoneService.update.phoneActionNotList")
        }
        for (item in body.doPhoneActions) {
            if (!(item instanceof Map)) { continue }
            Map pAction = item as Map
            Result<Phone> res
            switch(Helpers.toLowerCaseString(pAction.action)) {
                case Constants.PHONE_ACTION_DEACTIVATE:
                    res = deactivatePhone(p1)
                    break
                case Constants.PHONE_ACTION_TRANSFER:
                    res = transferPhone(p1, Helpers.toLong(pAction.id),
                        Helpers.<PhoneOwnershipType>convertEnum(PhoneOwnershipType,
                            pAction.type))
                    break
                case Constants.PHONE_ACTION_NEW_NUM_BY_NUM:
                    PhoneNumber pNum = new PhoneNumber(number:pAction.number as String)
                    res = this.updatePhoneForNumber(p1, pNum)
                    break
                case Constants.PHONE_ACTION_NEW_NUM_BY_ID:
                    res = this.updatePhoneForApiId(p1, pAction.numberId as String)
                    break
                default:
                    return resultFactory.failWithMessageAndStatus(BAD_REQUEST,
                        "phoneService.update.phoneActionInvalid",
                        [pAction.action])
            }
            if (!res.success) {
                Phone.withTransaction { TransactionStatus status ->
                    status.setRollbackOnly()
                }
                return res
            }
        }
        if (p1.save()) {
            resultFactory.success(p1)
        }
        else { resultFactory.failWithValidationErrors(p1.errors) }
    }

    protected Result<Phone> deactivatePhone(Phone p1) {
        String oldApiId = p1.apiId
        p1.deactivate()
        if (!p1.validate()) {
            return resultFactory.failWithValidationErrors(p1.errors)
        }
        if (oldApiId) {
            freeExistingNumber(oldApiId).then({ ->
                resultFactory.success(p1)
            }) as Result<Phone>
        }
        else { resultFactory.success(p1) }
    }
    protected Result<Phone> transferPhone(Phone p1, Long id, PhoneOwnershipType type) {
        Result<PhoneOwnership> res = p1.transferTo(id, type)
        res.success ? resultFactory.success(p1) : res
    }
    protected Result<Phone> updatePhoneForNumber(Phone p1, PhoneNumber pNum) {
        if (!pNum.validate()) {
            return resultFactory.failWithValidationErrors(pNum.errors)
        }
        if (pNum.number == p1.numberAsString) {
            return resultFactory.success(p1)
        }
        if (checkIsDuplicate({ -> Phone.countByNumberAsString(pNum.number) })) {
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
        if (checkIsDuplicate({ -> Phone.countByApiId(apiId) })) {
            return resultFactory.failWithMessageAndStatus(UNPROCESSABLE_ENTITY,
                'phoneService.changeNumber.duplicate')
        }
        Result.<Phone>waterfall(
            this.&changeForApiId.curry(apiId),
            this.&updatePhoneWithNewNumber.rcurry(p1)
        )
    }

    // Update helpers
    // --------------

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

    protected boolean checkIsActive() {
        boolean isActive = false
        Phone.withNewSession { Session session ->
            session.flushMode = FlushMode.MANUAL
            try {
                isActive = authService.isActive
            }
            finally { session.flushMode = FlushMode.AUTO }
        }
        isActive
    }
    protected boolean checkIsDuplicate(Closure doCheck) {
        boolean isDuplicate = false
        Phone.withNewSession { Session session ->
            session.flushMode = FlushMode.MANUAL
            try {
                isDuplicate = (Helpers.toInteger(doCheck()) > 0)
            }
            finally { session.flushMode = FlushMode.AUTO }
        }
        isDuplicate
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

    ResultList<RecordItem> sendMessage(Phone phone, OutgoingMessage msg, Staff staff = null) {
        ResultList<RecordItem> resList = new ResultList<>()
        HashSet<Contactable> recipients = msg.toRecipients()
        Map<Long, List<TempRecordReceipt>> contactIdToReceipts = [:]
            .withDefault { [] as List<TempRecordReceipt> }
        // send to each contactable
        recipients.each { Contactable c1 ->
            resList << sendToContactable(phone, c1, msg, staff, contactIdToReceipts)
        }
        // record all receipts on each tag, if applicable
        msg.tags.each { ContactTag ct1 ->
            resList << storeMessageInTag(ct1, msg, staff, contactIdToReceipts)
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

    protected Result<RecordItem> sendToContactable(Phone phone,
        Contactable c1, OutgoingMessage msg, Staff staff = null,
        Map<Long, List<TempRecordReceipt>> contactIdToReceipts = [:]) {

        boolean isContact = c1.instanceOf(Contact)
        PhoneNumber fromNum = isContact ? phone.number :
            ((c1 instanceof SharedContact) ? c1.sharedBy.number : null)
        Result<TempRecordReceipt> res = msg.isText ?
            textService.send(fromNum, c1.numbers, msg.message) :
            callService.start(fromNum, c1.numbers, [handle:CallResponse.DIRECT_MESSAGE,
                message:msg.message, identifier:staff?.name])
        res.then({ TempRecordReceipt receipt ->
            Result storeRes = msg.isText ?
                c1.storeOutgoingText(msg.message, receipt, staff) :
                c1.storeOutgoingCall(receipt, staff)
            if (isContact && storeRes.success) {
                (contactIdToReceipts[c1.contactId] as List<TempRecordReceipt>) << receipt
            }
            storeRes
        }) as Result<RecordItem>
    }
    protected Result<RecordItem> storeMessageInTag(ContactTag ct1, OutgoingMessage msg,
        Staff staff = null, Map<Long, List<TempRecordReceipt>> contactIdToReceipts = [:]) {
        // create a new msg on the tag's record
        ct1.addTextToRecord([contents:msg.message], staff).then({ RecordItem tagText ->
            ct1.members.each { Contact c1 ->
                // add contact msg's receipts to tag's msg
                contactIdToReceipts[c1.id]?.each { TempRecordReceipt r ->
                    tagText.addReceipt(r)
                    tagText.save()
                }
            }
            resultFactory.success(tagText)
        }) as Result<RecordItem>
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
                    .logFail("PhoneService.startAnnouncement: create contact")
                    .then({ Contact newC ->
                        newContacts << newC
                        contacts << newC
                    })
            }
            if (receipt) {
                contacts.each { Contact c1 ->
                    (addToRecordAction(c1, receipt) as Result<RecordItem>)
                        .logFail("PhoneService.startAnnouncement: add to record")
                        .then({ RecordItem item ->
                            item.isAnnouncement = true
                        })
                }
            }
            else {
                log.error("PhoneService.startAnnouncement: \
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
            // if contacts is empty, then return a message notifying the texter
            // that he or she has been blocked by the user 
            if (contacts.isEmpty()) {
                return twimlBuilder.build(TextResponse.BLOCKED)
            }
            // notify available staff members
            List<String> responses = [] // list of string and text resonses
            // if none of the staff for any of the phones are available
            if (!tryNotifyStaff(phone, text.message, contacts)) {
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
            // if contacts is empty, then return a message notifying the caller
            // that he or she has been blocked by the user 
            if (contacts.isEmpty()) {
                return twimlBuilder.build(CallResponse.BLOCKED)
            }
            // notify available staff members
            HashSet<String> numsToCall = new HashSet<>()
            (phone
                .getPhonesToAvailableNowForContactIds(contacts*.id)
                .values()
                .flatten() as Collection<Staff>)
                .each { Staff s1 ->
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
        List<Contact> contacts = Contact.listForPhoneAndNum(phone, pNum),
            notBlockedContacts = contacts.findAll { Contact c1 -> 
                c1.status != ContactStatus.BLOCKED 
            } as List<Contact>
        // only create new contact if no blocked contact either because 
        // we don't want to create a new contact if there is a blocked contact associated
        // with the incoming number 
        if (contacts.isEmpty()) {
            Result res = phone.createContact([:], [pNum.number])
            if (res.success) {
                notBlockedContacts = [res.payload]
            }
            else { return res }
        }
        //add text to contact records
        //note that blocked contacts will not even have the incoming message be stored 
        notBlockedContacts.each { Contact c1 ->
            contactStoreAction(c1)
            //only change status to unread
            //dont' have to worry about blocked contacts since we already filtered those out
            c1.status = ContactStatus.UNREAD
            if (!c1.save()) {
                return resultFactory.failWithValidationErrors(c1.errors)
            }
        }
        // socket notify of new contact and/or unread contacts
        socketService.sendContacts(notBlockedContacts)
        resultFactory.success(notBlockedContacts)
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
                String eTag = ""
                for (rec in recs) {
                    eTag = s3Service.putObject(bucketName, apiId,
                        rec.getMedia(".mp3"), metadata)?.getETag()
                    //only put the first recording if first one succeeds
                    //and there are multiple recordings
                    if (eTag) { break }
                }
                //delete all recordings on Twilio
                for (rec in recs) { rec.delete() }
                resultFactory.success(eTag)
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

    protected String getNameOrNumber(List<Contact> contacts, IncomingSession session) {
        for (contact in contacts) {
            if (contact.name) { return contact.name }
        }
        return Helpers.formatNumberForSay(session.numberAsString)
    }
    protected boolean tryNotifyStaff(Phone phone, String message,
        List<Contact> contacts) {
        Map<Phone, List<Staff>> phonesToAvailableNow =
            phone.getPhonesToAvailableNowForContactIds(contacts*.id)
        // if none of the staff for any of the phones are available
        if (!phonesToAvailableNow) {
            return false
        }
        // if multiple contacts from the same phone, then
        // this will take the last contact's record. In the future,
        // if we wanted to support showing all the records that received
        // the text, we can do so by exhaustively listing all record ids
        Map<Long, Long> phoneIdToRecordId = contacts
            .collectEntries { Contact c1 ->
                [(c1.phone.id):c1.record.id]
            }
        SharedContact
            .findEveryByContactIdsAndSharedBy(contacts*.id, phone)
            .each { SharedContact sc1 ->
                Long recordId = sc1.record?.id
                if (recordId) {
                    phoneIdToRecordId[sc1.sharedWith.id] = recordId
                }
            }
        // if a staff member is part of a team that has a TextUp phone
        // and also has an individual TextUp phone, then we don't want
        // to send multiple notifications. Therefore, this map associates
        // each staff member with his/her personal TextUp phone
        // if the staff member has a personal TextUp phone
        // FOR THE PHONES THAT HAVE ACCESS TO THIS RECORD. Since we
        // are looping through the phones that have access to this record
        // we don't have to worry about the case where the staff member
        // has a personal TextUp phone and is part of the team but is not
        // shared on the contact on the personal phone. If this were the case,
        // then the personal phone wouldn't even by a key in this map.
        Map<Long, Long> staffIdToPersonalPhoneId = [:]
        phonesToAvailableNow.each { Phone p1, List<Staff> staffs ->
            if (p1.owner.type == PhoneOwnershipType.INDIVIDUAL) {
                staffIdToPersonalPhoneId[p1.owner.ownerId] = p1.id
            }
        }
        // if you are concerned about several tokens generated
        // for one message, remember that we also send unique tokens
        // to those who are members of a team. So if a contact
        // from a team is shared with a staff and that staff is
        // also a member of that team, the staff member will
        // receive two notification texts each with a unique token
        // And these tokens will appear to be the same when you
        // look at the database because the token's data does not
        // specify the from and to numbers of the notification
        phonesToAvailableNow.each { Phone p1, List<Staff> staffs ->
            String instructions = messageSource.getMessage(
                "phoneService.notifyStaff.notification", null, LCH.getLocale())
            for (Staff s1 in staffs) {
                // if the staff member has a personal phone AND
                // this phone p1 is NOT the personal phone,
                // then skip notifying until we get to the personal phone
                if (staffIdToPersonalPhoneId.containsKey(s1.id) &&
                    staffIdToPersonalPhoneId[s1.id] != p1.id) {
                    continue
                }
                Long recordId = phoneIdToRecordId[p1.id]
                if (recordId) {
                    tokenService.notifyStaff(p1, s1, recordId, false,
                            message, instructions)
                        .logFail("PhoneService.relayText: calling notifyStaff")
                }
                else {
                    log.error("PhoneService.relayText: getPhonesToAvailableNowForContactIds \
                        called on phone ${phone.id} yielded a phone ${p1.id} \
                        that did not have a corresponding contact's record in map")
                }
            }
        }
        return true
    }
}
