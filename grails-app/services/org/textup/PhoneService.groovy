package org.textup

import com.amazonaws.services.s3.model.PutObjectResult
import com.twilio.exception.TwilioException
import com.twilio.http.HttpMethod
import com.twilio.rest.api.v2010.account.Call
import com.twilio.rest.api.v2010.account.IncomingPhoneNumber
import com.twilio.rest.api.v2010.account.Recording
import grails.async.Promise
import grails.async.PromiseList
import grails.async.Promises
import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit
import java.util.UUID
import org.apache.commons.codec.binary.Base64
import org.apache.commons.lang3.tuple.Pair
import org.apache.http.client.methods.HttpGet
import org.apache.http.HttpHeaders
import org.apache.http.HttpResponse
import org.apache.http.HttpStatus as ApacheHttpStatus
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.springframework.context.i18n.LocaleContextHolder as LCH
import org.springframework.context.MessageSource
import org.textup.rest.TwimlBuilder
import org.textup.type.CallResponse
import org.textup.type.ContactStatus
import org.textup.type.PhoneOwnershipType
import org.textup.type.ReceiptStatus
import org.textup.type.RecordItemType
import org.textup.type.TextResponse
import org.textup.type.VoiceType
import org.textup.validator.action.ActionContainer
import org.textup.validator.action.PhoneAction
import org.textup.validator.BasicNotification
import org.textup.validator.IncomingText
import org.textup.validator.OutgoingMessage
import org.textup.validator.PhoneNumber
import org.textup.validator.TempRecordReceipt

@GrailsTypeChecked
@Transactional
class PhoneService {

    AuthService authService
    CallService callService
    GrailsApplication grailsApplication
    MessageSource messageSource
    NotificationService notificationService
    ResultFactory resultFactory
    SocketService socketService
    StorageService storageService
    TextService textService
    TokenService tokenService
    TwimlBuilder twimlBuilder

    // Update
    // ------

    Result<Staff> mergePhone(Staff s1, Map body) {
        if (body.phone instanceof Map && Helpers.<Boolean>doWithoutFlush{ authService.isActive }) {
            Phone p1 = s1.phoneWithAnyStatus ?: new Phone([:])
            p1.updateOwner(s1)
            this.update(p1, body.phone as Map).then({ resultFactory.success(s1) })
        }
        else { resultFactory.success(s1) }
    }
    Result<Team> mergePhone(Team t1, Map body) {
        if (body.phone instanceof Map && Helpers.<Boolean>doWithoutFlush{ authService.isActive }) {
            Phone p1 = t1.phoneWithAnyStatus ?: new Phone([:])
            p1.updateOwner(t1)
            this.update(p1, body.phone as Map).then({ resultFactory.success(t1) })
        }
        else { resultFactory.success(t1) }
    }
    protected Result<Phone> update(Phone p1, Map body) {
        if (body.awayMessage) {
            p1.awayMessage = body.awayMessage
        }
        if (body.voice) {
            p1.voice = Helpers.convertEnum(VoiceType, body.voice)
        }
        if (body.doPhoneActions) {
            ActionContainer ac1 = new ActionContainer(body.doPhoneActions)
            List<PhoneAction> actions = ac1.validateAndBuildActions(PhoneAction)
            if (ac1.hasErrors()) {
                return resultFactory.failWithValidationErrors(ac1.errors)
            }
            Collection<Result<?>> failResults = []
            for (PhoneAction a1 in actions) {
                Result<Phone> res
                switch (a1) {
                    case Constants.PHONE_ACTION_DEACTIVATE:
                        res = deactivatePhone(p1)
                        break
                    case Constants.PHONE_ACTION_TRANSFER:
                        res = transferPhone(p1, a1.id, a1.typeAsEnum)
                        break
                    case Constants.PHONE_ACTION_NEW_NUM_BY_NUM:
                        res = updatePhoneForNumber(p1, a1.phoneNumber)
                        break
                    default: // Constants.PHONE_ACTION_NEW_NUM_BY_ID
                        res = updatePhoneForApiId(p1, a1.numberId)
                }
                if (!res.success) { failResults << res }
            }
            if (failResults) {
                return resultFactory.failWithResultsAndStatus(failResults, ResultStatus.BAD_REQUEST)
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
            freeExistingNumber(oldApiId).then({ resultFactory.success(p1) })
        }
        else { resultFactory.success(p1) }
    }
    protected Result<Phone> transferPhone(Phone p1, Long id, PhoneOwnershipType type) {
        p1.transferTo(id, type).then({ resultFactory.success(p1) })
    }
    protected Result<Phone> updatePhoneForNumber(Phone p1, PhoneNumber pNum) {
        if (!pNum.validate()) {
            return resultFactory.failWithValidationErrors(pNum.errors)
        }
        if (pNum.number == p1.numberAsString) {
            return resultFactory.success(p1)
        }
        if (Helpers.<Boolean>doWithoutFlush({ Phone.countByNumberAsString(pNum.number) > 0 })) {
            return resultFactory.failWithCodeAndStatus("phoneService.changeNumber.duplicate",
                ResultStatus.UNPROCESSABLE_ENTITY)
        }
        this.changeForNumber(pNum).then({ IncomingPhoneNumber iNum ->
            this.updatePhoneWithNewNumber(iNum, p1)
        })
    }
    protected Result<Phone> updatePhoneForApiId(Phone p1, String apiId) {
        if (apiId == p1.apiId) {
            return resultFactory.success(p1)
        }
        if (Helpers.<Boolean>doWithoutFlush({ Phone.countByApiId(apiId) > 0 })) {
            return resultFactory.<Phone>failWithCodeAndStatus("phoneService.changeNumber.duplicate",
                ResultStatus.UNPROCESSABLE_ENTITY)
        }
        this.changeForApiId(apiId).then({ IncomingPhoneNumber iNum ->
            this.updatePhoneWithNewNumber(iNum, p1)
        })
    }

    // Update helpers
    // --------------

    protected Result<IncomingPhoneNumber> changeForNumber(PhoneNumber pNum) {
        try {
            String unavailable = grailsApplication.flatConfig["textup.apiKeys.twilio.unavailable"],
                appId = grailsApplication.flatConfig["textup.apiKeys.twilio.appId"]
            IncomingPhoneNumber iNum = IncomingPhoneNumber
                .creator(pNum.toApiPhoneNumber())
                .setFriendlyName(unavailable)
                .setSmsApplicationSid(appId)
                .setSmsMethod(HttpMethod.POST)
                .setVoiceApplicationSid(appId)
                .setVoiceMethod(HttpMethod.POST)
                .create()
            resultFactory.success(iNum)
        }
        catch (TwilioException e) {
            resultFactory.failWithThrowable(e)
        }
    }
    protected Result<IncomingPhoneNumber> changeForApiId(String newApiId) {
        try {
            String unavailable = grailsApplication.flatConfig["textup.apiKeys.twilio.unavailable"],
                appId = grailsApplication.flatConfig["textup.apiKeys.twilio.appId"]
            IncomingPhoneNumber uNum = IncomingPhoneNumber
                .updater(newApiId)
                .setFriendlyName(unavailable)
                .setSmsApplicationSid(appId)
                .setSmsMethod(HttpMethod.POST)
                .setVoiceApplicationSid(appId)
                .setVoiceMethod(HttpMethod.POST)
                .update()
            resultFactory.success(uNum)
        }
        catch (TwilioException e) {
            resultFactory.failWithThrowable(e)
        }
    }
    protected Result<Phone> updatePhoneWithNewNumber(IncomingPhoneNumber newNum, Phone p1) {
        String oldApiId = p1.apiId
        p1.apiId = newNum.sid
        p1.number = new PhoneNumber(number:newNum.phoneNumber as String)
        if (oldApiId) {
            freeExistingNumber(oldApiId).then({ resultFactory.success(p1) })
        }
        else { resultFactory.success(p1) }
    }
    protected Result<IncomingPhoneNumber> freeExistingNumber(String oldApiId) {
        try {
            String available = grailsApplication.flatConfig["textup.apiKeys.twilio.available"]
            IncomingPhoneNumber uNum = IncomingPhoneNumber
                .updater(oldApiId)
                .setFriendlyName(available)
                .update()
            resultFactory.success(uNum)
        }
        catch (TwilioException e) {
            resultFactory.failWithThrowable(e)
        }
    }

    // Outgoing
    // --------

    ResultGroup<RecordItem> sendMessage(Phone phone, OutgoingMessage msg, Staff staff = null) {
        HashSet<Contactable> recipients = msg.toRecipients()
        Map<Long, List<TempRecordReceipt>> contactIdToReceipts = [:]
            .withDefault { [] as List<TempRecordReceipt> }
        // send to each contactable
        ResultGroup<RecordItem> resGroup =
            sendToContactable(phone, recipients.toList(), msg, staff, contactIdToReceipts)
        // record all receipts on each tag, if applicable
        if (resGroup.anySuccesses) {
            msg.tags.each { ContactTag ct1 ->
                resGroup << storeMessageInTag(ct1, msg, staff, contactIdToReceipts)
            }
        }
        resGroup
    }
    Result<RecordCall> startBridgeCall(Phone phone, Contactable c1, Staff staff) {
        PhoneNumber fromNum = (c1 instanceof SharedContact) ? c1.sharedBy.number : phone.number,
            toNum = staff.personalPhoneNumber
        Map afterPickup = [contactId:c1.contactId, handle:CallResponse.FINISH_BRIDGE]
        callService.start(fromNum, toNum, afterPickup)
            .then({ TempRecordReceipt receipt -> c1.storeOutgoingCall(receipt, staff) })
            .then({ RecordCall call1 -> resultFactory.success(call1, ResultStatus.CREATED) })
    }
    Map<String, Result<TempRecordReceipt>> sendTextAnnouncement(Phone phone, String message,
        String identifier, List<IncomingSession> sessions, Staff staff) {
        Result<List<String>> res = twimlBuilder
            .translate(TextResponse.ANNOUNCEMENT, [
                identifier:identifier,
                message:message
            ])
            .logFail("PhoneService.sendTextAnnouncement")
        String announcement = res.success ? res.payload[0] : "$identifier: $message"
        startAnnouncement(phone, sessions, { IncomingSession s1 ->
            textService.send(phone.number, [s1.number], announcement)
        }, { Contact c1, TempRecordReceipt receipt ->
            c1.storeOutgoingText(announcement, receipt, staff)
        })
    }
    Map<String, Result<TempRecordReceipt>> startCallAnnouncement(Phone phone, String message,
        String identifier, List<IncomingSession> sessions, Staff staff) {
        startAnnouncement(phone, sessions, { IncomingSession s1 ->
            callService.start(phone.number, s1.number, [
                handle:CallResponse.ANNOUNCEMENT_AND_DIGITS,
                message:message,
                identifier:identifier
            ])
        }, { Contact c1, TempRecordReceipt receipt ->
            c1.storeOutgoingCall(receipt, staff)
        })
    }

    // Outgoing helper methods
    // -----------------------

    protected ResultGroup<RecordItem> sendToContactable(Phone phone,
        List<Contactable> recipients, OutgoingMessage msg, Staff staff = null,
        Map<Long, List<TempRecordReceipt>> contactIdToReceipts = [:]) {
        ResultGroup<RecordItem> resGroup = new ResultGroup<>()
        try {
            PromiseList<Map<Contactable, Result<TempRecordReceipt>>> pList1 = new PromiseList<>()
            recipients
                .collate(Constants.CONCURRENT_SEND_BATCH_SIZE)
                .each { List<Contactable> contactables ->
                    pList1 << sendToContactableAsyncHelper(phone, contactables, msg)
                }
            (pList1.get(1, TimeUnit.MINUTES) as List<Map<Contactable, Result<TempRecordReceipt>>>)
                .each { Map<Contactable, Result<TempRecordReceipt>> contactableToRes ->
                    contactableToRes.each { Contactable c1, Result<TempRecordReceipt> res ->
                        resGroup << res.then({ TempRecordReceipt receipt ->
                            Result<? extends RecordItem> storeRes = msg.isText ?
                                c1.storeOutgoingText(msg.message, receipt, staff) :
                                c1.storeOutgoingCall(receipt, staff, msg.message)
                            if (c1.instanceOf(Contact) && storeRes.success) {
                                contactIdToReceipts[c1.contactId]?.add(receipt)
                            }
                            resultFactory.success(storeRes.payload, ResultStatus.CREATED)
                        })
                    }
                }
            resGroup
        }
        catch (Throwable e) { // an async error and SHOULD rollback transaction
            log.error("PhoneService.sendToContactable: ${e.class}, ${e.message}")
            e.printStackTrace()
            resGroup << resultFactory.failWithThrowable(e)
        }
    }

    protected Promise<Map<Contactable, Result<TempRecordReceipt>>> sendToContactableAsyncHelper(
        Phone phone, List<Contactable> contactables, OutgoingMessage msg) {
        // store needed data outside of the promise closure to prevent accidentally making
        // any db calls inside of the closure and triggering a `no session` error
        Map<Long, PhoneNumber> contactIdToFromNum = [:]
        Map<Long, List<ContactNumber>> contactIdToNums = [:]
        contactables.each { Contactable c1 ->
            Long cId = c1.contactId
            contactIdToFromNum[cId] = c1.fromNum
            contactIdToNums[cId] = c1.sortedNumbers
        }
        String phoneName = phone.name // this makes a db call
        // NO HIBERNATE SESSION WITHIN NEW THREAD!!
        // Any calls that will make a db call needs to be made outside of the task closure
        Promises.task {
            Map<Contactable, Result<TempRecordReceipt>> contactableToRes = [:]
            contactables.each { Contactable c1 ->
                Long cId = c1.contactId
                PhoneNumber fromNum = contactIdToFromNum[cId]
                List<ContactNumber> sortedNums = contactIdToNums[cId]
                contactableToRes[c1] = msg.isText ?
                    textService.send(fromNum, sortedNums, msg.message) :
                    callService.start(fromNum, sortedNums, [handle:CallResponse.DIRECT_MESSAGE,
                        message:msg.message, identifier:phoneName])
            }
            contactableToRes
        }
    }
    protected Result<RecordItem> storeMessageInTag(ContactTag ct1, OutgoingMessage msg,
        Staff staff = null, Map<Long, List<TempRecordReceipt>> contactIdToReceipts = [:]) {
        // create a new msg on the tag's record
        Result<? extends RecordItem> res = msg.isText ?
            ct1.record.addText([contents:msg.message], staff?.toAuthor()) :
            ct1.record.addCall([callContents:msg.message], staff?.toAuthor())
        res.then({ RecordItem tagItem ->
            ct1.members.each { Contact c1 ->
                // add contact msg's receipts to tag's msg
                contactIdToReceipts[c1.id]?.each { TempRecordReceipt r ->
                    tagItem.addReceipt(r)
                    tagItem.save()
                }
            }
            resultFactory.success(tagItem, ResultStatus.CREATED)
        })
    }
    protected Map<String, Result<TempRecordReceipt>> startAnnouncement(Phone phone,
        List<IncomingSession> sessions, Closure<Result<TempRecordReceipt>> receiptAction,
        Closure<Result<RecordItem>> addToRecordAction) {
        Map<String, Result<TempRecordReceipt>> resMap = new HashMap<>()
        Map<String,TempRecordReceipt> numberAsStringToReceipt = [:]
        sessions.each { IncomingSession s1 ->
            Result<TempRecordReceipt> res = receiptAction(s1)
                .logFail("PhoneService.startAnnouncement: sending and returning receipt")
            if (res.success) {
                TempRecordReceipt receipt = res.payload
                numberAsStringToReceipt[s1.numberAsString] = receipt
            }
            resMap[s1.numberAsString] = res
        }
        // find contacts that have the same number as this session, if any
        // if no contacts share this number, we will create a new contact
        List<Contact> newContacts = []
        ContactNumber
            .getContactsForPhoneAndNumbers(phone, numberAsStringToReceipt.keySet())
            .each { String numAsString, List<Contact> contacts ->
                TempRecordReceipt receipt = numberAsStringToReceipt[numAsString]
                if (!contacts) { // create new contact if none found for session
                    phone.createContact([:], [numAsString])
                        .logFail("PhoneService.startAnnouncement: create contact")
                        .thenEnd({ Contact newC ->
                            newContacts << newC
                            contacts << newC
                        })
                }
                if (receipt) {
                    contacts.each { Contact c1 ->
                        addToRecordAction(c1, receipt)
                            .logFail("PhoneService.startAnnouncement: add to record")
                            .thenEnd({ RecordItem item -> item.isAnnouncement = true })
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
            Result<RecordText> res = c1
                .storeIncomingText(text, session)
                .logFail("PhoneService.relayText: store text for contact ${c1.id}")
            if (res.success) {
                rTexts << res.payload
            }
        }).then({ List<Contact> contacts ->
            // if contacts is empty, then return a message notifying the texter
            // that he or she has been blocked by the user
            if (contacts.isEmpty()) {
                return twimlBuilder.build(TextResponse.BLOCKED)
            }
            // notify available staff members
            List<String> responses = [] // list of string and text resonses
            List<BasicNotification> notifs = notificationService.build(phone, contacts)
            if (notifs) {
                String instructions = messageSource.getMessage("phoneService.notifyStaff.notification",
                    null, LCH.getLocale())
                notifs.each { BasicNotification bn1 ->
                    tokenService
                        .notifyStaff(bn1, false, text.message, instructions)
                        .logFail("PhoneService.relayText: calling notifyStaff")
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
            socketService.sendItems(rTexts)
            twimlBuilder.buildTexts(responses)
        })
    }
    Result<Closure> relayCall(Phone phone, String apiId, IncomingSession session) {
        List<RecordCall> rCalls = []
        storeForNumber(phone, session.number, { Contact c1 ->
            Result<RecordCall> res = c1
                .storeIncomingCall(apiId, session)
                .logFail("PhoneService.relayCall")
            if (res.success) {
                rCalls << res.payload
            }
        }).then({ List<Contact> contacts ->
            // if contacts is empty, then he or she has been blocked by the user
            if (contacts.isEmpty()) {
                return twimlBuilder.build(CallResponse.BLOCKED)
            }
            HashSet<String> numsToCall = new HashSet<>()
            notificationService.build(phone, contacts).each { BasicNotification bn1 ->
                Staff s1 = bn1.staff
                if (s1?.personalPhoneAsString) {
                    numsToCall << s1.personalPhoneNumber.e164PhoneNumber
                }
            }
            if (numsToCall) {
                twimlBuilder.build(CallResponse.CONNECT_INCOMING, [
                    displayedNumber:phone.number.e164PhoneNumber,
                    numsToCall:numsToCall,
                    linkParams:[handle:CallResponse.CHECK_IF_VOICEMAIL],
                    screenParams:[
                        handle:CallResponse.SCREEN_INCOMING,
                        originalFrom: session.number.e164PhoneNumber
                    ]
                ])
            }
            else {
                rCalls.each { RecordCall rCall -> rCall.hasAwayMessage = true }
                // must pass in a non-success status because we will not start voicemail
                // if the call has already completed successfully
                phone.tryStartVoicemail(session.number, phone.number, ReceiptStatus.PENDING)
            }
        })
    }
    Result<Closure> screenIncomingCall(Phone phone, IncomingSession session) {
        List<Contact> notBlockedContacts = getDeliverableContacts(phone, session.number).right
        List<String> uniques = []
        notBlockedContacts.each { Contact c1 -> uniques << c1.getNameOrNumber() }
        uniques.unique() // in-place modification
        twimlBuilder.build(CallResponse.SCREEN_INCOMING, [
            callerId: Helpers.joinWithDifferentLast(uniques, ", ", " or "),
            linkParams:[handle:CallResponse.DO_NOTHING]
        ])
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
                        [displayedNumber:phone.number.e164PhoneNumber, numAsString:pNum.number])
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
    Result<String> moveVoicemail(String callId, String recordingId, String voicemailUrl) {
        // build a HttpClient and execute the get request
        CloseableHttpClient client = HttpClients.createDefault()
        try {
            HttpGet req = new HttpGet(voicemailUrl + ".mp3")
            req.setHeader(HttpHeaders.AUTHORIZATION, buildBasicAuth());
            HttpResponse resp = client.execute(req)
            try {
                int statusCode = resp.statusLine.statusCode
                if (statusCode != ApacheHttpStatus.SC_OK) {
                    return resultFactory.failWithCodeAndStatus(
                        "phoneService.moveVoicemail.couldNotRetrieveVoicemail",
                        ResultStatus.convert(statusCode),
                        [resp.statusLine.reasonPhrase])
                }
                InputStream stream = resp.entity.content
                try {
                    storageService
                        .upload(callId, "audio/mpeg", stream)
                        .then({ PutObjectResult putRes ->
                            boolean deleteOutcome = Recording.deleter(recordingId).delete()
                            if (deleteOutcome == false) {
                                log.error("PhoneService.moveVoicemail could not delete ${recordingId}")
                            }
                            resultFactory.success(putRes.getETag())
                        })
                }
                finally { stream.close() }
            }
            finally { resp.close() }
        }
        catch (Throwable e) {
            log.error("PhoneService.moveVoicemail throwable: ${e.message}")
            e.printStackTrace()
            resultFactory.failWithThrowable(e)
        }
        finally { client.close() }
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

    // Incoming helper methods
    // -----------------------

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
    protected Pair<List<Contact>, List<Contact>> getDeliverableContacts(Phone phone, PhoneNumber pNum) {
        List<Contact> contacts = Contact.listForPhoneAndNum(phone, pNum),
            notBlockedContacts = contacts.findAll { Contact c1 ->
                c1.status != ContactStatus.BLOCKED
            } as List<Contact>
        Pair.of(contacts, notBlockedContacts)
    }
    protected Result<List<Contact>> storeForNumber(Phone phone, PhoneNumber pNum,
        Closure contactStoreAction) {
        Pair<List<Contact>, List<Contact>> deliverables = getDeliverableContacts(phone, pNum)
        List<Contact> contacts = deliverables.left,
            notBlockedContacts = deliverables.right
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
            // only change status to unread
            // dont' have to worry about blocked contacts since we already filtered those out
            c1.status = ContactStatus.UNREAD
            // NOTE: because we've already screened out all contacts that have been blocked
            // by the owner of the contact, this effectively means that blocking also effectively
            // stops all sharing relationships because we do not even attempt to deliver
            // message to shared contacts that have had their original contact blocked by
            // the original owner
            List<SharedContact> sharedContacts = c1.sharedContacts
            for (SharedContact sc1 in sharedContacts) {
                // only marked the shared contact's status as unread IF the shared contact's
                // status is NOT blocked. If the collaborator has blocked this contact then
                // we want to respect that decision.
                if (sc1.status != ContactStatus.BLOCKED) {
                    sc1.status = ContactStatus.UNREAD
                    if (!sc1.save()) {
                        return resultFactory.failWithValidationErrors(sc1.errors)
                    }
                }
            }
            if (!c1.save()) {
                return resultFactory.failWithValidationErrors(c1.errors)
            }
        }
        // socket notify of new contact and/or unread contacts
        socketService.sendContacts(notBlockedContacts)
        resultFactory.success(notBlockedContacts)
    }
}
