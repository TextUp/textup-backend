package org.textup

import grails.transaction.Transactional
import com.amazonaws.services.s3.model.ObjectMetadata
import com.amazonaws.services.s3.model.PutObjectResult
import com.twilio.sdk.resource.factory.CallFactory
import com.twilio.sdk.resource.instance.Call
import com.twilio.sdk.resource.list.RecordingList

@Transactional
class PhoneService {

    def grailsApplication
	def twimlBuilder
	def socketService
    def textService
    def callService

    // Outgoing
    // --------

    ResultList<RecordText> sendText(Phone phone, OutgoingText text, Staff staff) {
        ResultList resList = new ResultList()
        // define datastructures
        Map<ContactTag, HashSet<Contactable>> tagsToContacts = [:]
        Map<Contactable, RecordItem> contactToText = [:]
        HashSet<Contactable> recipients = new HashSet<>()
        // build map of tags to members
        text.tags.each { ContactTag ct1 ->
            tagsToContacts[ct1] = new HashSet<Contactable>(ct1.allMembers)
        }
        // add all contactables to a hashset to avoid duplication
        recipients.addAll(tags.contacts)
        recipients.addAll(tags.sharedContacts)
        tagsToContacts.values().each { recipients.addAll(it) }
        if (recipients.size() > grailsApplication.config.textup.maxNumText) {
            return (resList << resultFactory.failWithMessage('phone.sendText.tooMany'))
        }
        // call sendText on each contactable
        recipients.each { Contactable c1 ->
            textService.send(phone.number, c1.numbers, message).then({ RecordItemReceipt receipt ->
                Result<RecordText> res = c1.storeOutgoingText(message, receipt, staff)
                if (res.success && c1.instanceOf(Contact)) {
                    contactToText[c1] = item
                }
                resList << res
            })
        }
        // record all receipts on each tag, if applicable
        tags.each { ContactTag ct1 ->
            // create a new text on the tag's record
            Result<RecordText> res = tag.addTextToRecord([contents:message], staff)
            if (res.success) {
                RecordText tagText = res.payload
                tagsToContacts[ct1]?.each { Contact c1 ->
                    // add contact text's receipts to tag's text
                    contactToText[c1]?.receipts.each { RecordItemReceipt r ->
                        RecordItemReceipt newR = r.copy()
                        tagItem.addToReceipts(newR)
                        newR.save()
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
        callService.start(fromNum, toNum, afterPickup).then({ RecordItemReceipt receipt ->
            c1.storeOutgoingCall(receipt, staff)
        })
    }
    ResultMap<String,RecordItemReceipt> sendTextAnnouncement(Phone phone, String message,
        String identifier, List<IncomingSession> sessions, Staff staff) {
        String announcement = "$identifier: $message"
        startAnnouncement(phone, sessions, { IncomingSession s1 ->
            textService.send(phone.number, [s1.number], announcement)
        }, { Contact c1, RecordItemReceipt receiptCopy ->
            c1.storeOutgoingText(announcement, receiptCopy, staff)
        })
    }
    ResultMap<String,RecordItemReceipt> startCallAnnouncement(Phone phone, String message,
        String identifier, List<IncomingSession> sessions, Staff staff) {
        startAnnouncement(phone, sessions, { IncomingSession s1 ->
            callService.start(phone.number, s1.number,
                [handle:CallResponse.ANNOUNCEMENT_AND_DIGITS,
                    message:message, identifier:identifier])
        }, { Contact c1, RecordItemReceipt receiptCopy ->
            c1.storeOutgoingCall(receiptCopy, staff)
        })
    }
    protected ResultMap<String,RecordItemReceipt> startAnnouncement(Phone phone,
        List<IncomingSession> sessions, Closure receiptAction,
        Closure addToRecordAction) {
        ResultMap<String,RecordItemReceipt> resMap = new ResultMap<>()
        Map<String,RecordItemReceipt> numberAsStringToReceipt = [:]
        sessions.each { IncomingSession s1 ->
            Result<RecordItemReceipt> res = receiptAction(s1)
            if (res.success) {
                RecordItemReceipt receipt = res.payload
                receipt.discard() // don't save this original, we will save copies
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
            RecordItemReceipt receipt = numberAsStringToReceipt[numberAsString]
            if (!contacts) { // create new contact if none found for session
                p1.createContact([:], [numberAsString])
                    .logFail("Phoneservice.sendTextAnnouncement: create contact")
                    .then({ Contact newC ->
                        newContacts << newC
                        contacts << newC
                    })
            }
            if (receipt) {
                contacts.each { Contact c1 ->
                    addToRecordAction(c1, receipt.copy())
                        .logFail("PhoneService.sendTextAnnouncement: add to record")
                        .then({ RecordItem item ->
                            item.isAnnouncement = true
                        })
                }
            }
            else {
                log.error("PhoneService.sendTextAnnouncement: \
                    no receipt for $numberAsString")
            }
        }
        // notify of new contacts
        socketService.sendContacts(newContacts)
        //return result list
        resList
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
        })
    }

    Result<Closure> relayCall(Phone phone, String apiId, IncomingSession session) {
        List<RecordCall> rCalls = []
        storeForNumber(phone, session.number, { Contact c1 ->
            Result res = c1.storeIncomingCall(apiId, session)
                .logFail("PhoneService.relayCall")
            if (res.success) { rCalls << res.payload }
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
                twimlBuilder.buildXmlFor(CallResponse.CONNECT_INCOMING,
                    [nameOrNumber:nameOrNumber, numsToCall:numsToCall,
                        linkParams:[handle:CallResponse.VOICEMAIL]])
            }
            else {
                rCalls.each { RecordCall rCall ->
                    rCall.hasAwayMessage = true
                }
                twimlBuilder.buildXmlFor(CallResponse.VOICEMAIL)
            }
        })
    }
    Result<List<Contact>> storeForNumber(Phone phone, PhoneNumber pNum,
        Closure contactStoreAction) {
        // create a new contact with this session's phone number if contact
        // does not already exist
        List<Contact> contacts = Contact.notBlockedForPhoneAndNum(phone, pNum).list()
        if (contacts.isEmpty()) {
            Result res = p1.createContact([:], [pNum.number])
            if (res.success) {
                contacts = [res.payload]
            }
            else { return res }
        }
        //add text to contact records
        contacts.each { Contact c1 ->
            if (c1.status != ContactStatus.BLOCKED) {
                contactStoreAction(c1)
                c1.status = ContactStatus.UNREAD
                if (!c1.save()) {
                    return resultFactory.failWithValidationErrors(c1.errors)
                }
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
                    List<FeaturedAnnouncement> announces = this.announcements
                    announces.each { FeaturedAnnouncement announce ->
                        announce.addToReceipts(RecordItemType.CALL, session)
                            .logFail("Phone.handleAnnouncementCall: add announce receipt")
                    }
                    twimlBuilder.buildXmlFor(CallResponse.HEAR_ANNOUNCEMENTS,
                        [announcements:announces, identifier:phone.owner.name])
                    break
                case Constants.CALL_SUBSCRIBE:
                    session.isSubscribedToCall = true
                    twimlBuilder.buildXmlFor(CallResponse.SUBSCRIBED)
                    break
                case Constants.CALL_GREETING_UNSUBSCRIBE:
                    session.isSubscribedToCall = false
                    twimlBuilder.buildXmlFor(CallResponse.UNSUBSCRIBED)
                    break
                default:
                    this.relayCall(phone, apiId, session)
            }
        }
        else if (this.announcements) {
            twimlBuilder.buildXmlFor(CallResponse.ANNOUNCEMENT_GREETING,
                [name:phone.owner.name, isSubscribed:session.isSubscribedToCall])
        }
        else { this.relayCall(phone, apiId, session) }
    }
    Result<Closure> handleSelfCall(String apiId, String digits, Staff staff) {
        if (digits) {
            PhoneNumber pNum = new PhoneNumber(number:digits)
            if (pNum.validate()) { //then is a valid phone number
                RecordItemReceipt receipt = new RecordItemReceipt(apiId:apiId)
                receipt.receivedBy = pNum
                if (receipt.validate()) {
                    receipt.discard() // will save copies
                    storeForNumber(this, pNum, { Contact c1 ->
                        c1.storeOutgoingCall(receipt.copy(), staff)
                            .logFail("PhoneService.handleSelfCall")
                    })
                    twimlBuilder.buildXmlFor(CallResponse.SELF_CONNECTING,
                        [numAsString:pNum.number])
                }
                else {
                    log.error("PhoneService.handleSelfCall: could not save \
                        receipt: ${receipt.errors}")
                    twimlBuilder.errorForCall()
                }
            }
            else {
                twimlBuilder.buildXmlFor(CallResponse.SELF_INVALID_DIGITS, [digits:digits])
            }
        }
        else {
            twimlBuilder.buildXmlFor(CallResponse.SELF_GREETING)
        }
    }
    Result<String> moveVoicemail(String apiId) {
        String bucketName = grailsApplication.config.textup.voicemailBucketName
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
                resultFactory.success(putRes.eTag)
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
        ResultList<RecordCall> resList = new ResultList<>()
        List<RecordItemReceipt> receipts = RecordItemReceipt.findAllByApiId(apiId)
        for (receipt in receipts) {
            RecordItem item = receipt.item
            if (item.instanceOf(RecordCall)) {
                item.hasVoicemail = true
                item.voicemailInSeconds = voicemailDuration
                item.record.updateLastRecordActivity()
                if (item.save() && item.record.save()) {
                    resList << resultFactory.success(receipt)
                }
                else if (!item.save()) {
                    resList << resultFactory.failWithValidationErrors(item.errors)
                }
                else {
                    resList << resultFactory.failWithValidationErrors(item.record.errors)
                }
            }
        }
        // send updated items with receipts through socket
        socketService.sendItems(resList.successes.collect { Result<RecordItemReceipt> res ->
            res.payload.item
        })
        resList
    }
    protected Result notifyStaff(Staff s1, IncomingText text, String identifier) {
        String notification = "${identifier}: ${text.message}"
        if (notification.size() >= Constants.TEXT_LENGTH) {
            int trimTo = Constants.TEXT_LENGTH - 4 // for the ellipses
            notification = "${notification[0..trimTo]}..."
        }
        textService.send(s1.phone.number, [s1.personalPhoneNumber],
            notification).then({ RecordItemReceipt receipt ->
            receipt.discard()
            resultFactory.success()
        })
    }
    protected String getNameOrNumber(List<Contact> contacts, IncomingSession session) {
        for (contact in contacts) {
            if (contact.name) { return contact.name }
        }
        return session.numberAsString
    }
}
