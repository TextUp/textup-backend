package org.textup

import grails.transaction.Transactional
import org.hibernate.StaleObjectStateException

@Transactional
class LockService {

    def resultFactory

    ////////////////////////////////
    // CallService or TextService //
    ////////////////////////////////

    Result<RecordItem> retry(RecordItem item, Contact contact, Closure stopOnSuccessOrInternalError, int attemptNum=0) {
        try {
            item.lock()
            contact.lock()
            List<String> cNums = contact.numbers*.e164PhoneNumber,
                alreadySent = item.receipts*.receivedBy*.e164PhoneNumber,
                numsRemaining = cNums - alreadySent
            stopOnSuccessOrInternalError(item, contact.phone.number.e164PhoneNumber, numsRemaining)
        }
        catch (StaleObjectStateException e) {
            if (attemptNum < Constants.LOCK_RETRY_MAX) {
                retry(item, contact, attemptNum++)
            }
            else { resultFactory.failWithThrowable(e) }
        }
    }

    /////////////////
    // CallService //
    /////////////////

    Result<List<RecordCall>> updateVoicemailItemsAndContacts(List<RecordItemReceipt> receipts, int voicemailDuration, int attemptNum=0) {
        try {
            //update RecordCalls with voicemail info
            List<RecordCall> calls = []
            List<Record> records = []
            for (receipt in receipts) {
                RecordItem item = receipt.item
                if (item.instanceOf(RecordCall)) {
                    item.lock()
                    item.hasVoicemail = true
                    item.voicemailInSeconds = voicemailDuration
                    if (!item.save()) {
                        return resultFactory.failWithValidationErrors(item.errors)
                    }
                    calls << item
                    records << item.record
                }
            }
            //update associated contact timestamps
            List<Contact> contacts = Contact.forRecords(records).list { lock(true) }
            for (contact in contacts) {
                contact.updateLastRecordActivity()
                if (!contact.save()) {
                    return resultFactory.failWithValidationErrors(contact.errors)
                }
            }
            resultFactory.success(calls)
        }
        catch (StaleObjectStateException e) {
            if (attemptNum < Constants.LOCK_RETRY_MAX) {
                updateVoicemailItemsAndContacts(receipts, voicemailDuration, attemptNum++)
            }
            else { resultFactory.failWithThrowable(e) }
        }
    }

    /////////////////
    // TextService //
    /////////////////



    ///////////////////
    // RecordService //
    ///////////////////

    Result<List<RecordItemReceipt>> updateStatus(List<RecordItemReceipt> receipts,
        String status, Integer duration, int attemptNum=0) {
        try {
            receipts*.lock()
            for (receipt in receipts) {
                RecordItem item = receipt.item
                item.lock()
                receipt.status = status
                if (duration && item.instanceOf(RecordCall)) { item.durationInSeconds = duration }
                if (!receipt.save()) { return resultFactory.failWithValidationErrors(receipt.errors) }
                if (!item.save()) { return resultFactory.failWithValidationErrors(item.errors) }
            }
            resultFactory.success(receipts)
        }
        catch (StaleObjectStateException e) {
            if (attemptNum < Constants.LOCK_RETRY_MAX) {
                updateStatus(receipts, status, duration, attemptNum++)
            }
            else { resultFactory.failWithThrowable(e) }
        }
    }

    Result<RecordItem> addToRecordWithReceipt(RecordItemType type, boolean outgoing, Contact contact,
        Map itemParams, Map receiptParams) {

        Result<List<RecordItem>> res = addToRecordWithReceipt(type, outgoing, [contact], itemParams, receiptParams)
        if (res.success) { resultFactory.success(res.payload[0]) }
        else { res }
    }

    Result<List<RecordItem>> addToRecordWithReceipt(RecordItemType type, boolean outgoing, List<Contact> contacts,
        Map itemParams, Map receiptParams, int attemptNum=0) {
        try {
            List<RecordItem> items = []
            contacts*.lock()
            for (contact in contacts) {
                if (contact.status != Constants.CONTACT_BLOCKED) {
                    if (outgoing == false) { //if incoming
                        contact.updateLastRecordActivity()
                        contact.status = Constants.CONTACT_UNREAD
                        if (!contact.save()) {
                            return resultFactory.failWithValidationErrors(contact.errors)
                        }
                    }
                    Record rec = contact.record
                    List<RecordItem> iList = listForRecordItemType(type, rec, receiptParams.apiId)
                    //add call and receipt to record if receipt with apiId already exists
                    if (iList) { items += iList }
                    else {
                        rec.lock()
                        Result res = createForRecordItemType(type, rec, itemParams)
                        if (res.success) {
                            RecordItem item = res.payload
                            item.lock()
                            RecordItemReceipt receipt = new RecordItemReceipt(receiptParams)
                            item.addToReceipts(receipt)
                            if (receipt.save()) {
                                if (item.save()) {
                                    if (contact.save()) {
                                        contact.updateLastRecordActivity()
                                        items << item
                                    }
                                    else { 
                                        return resultFactory.failWithValidationErrors(contact.errors)
                                    }
                                }
                                else {
                                    return resultFactory.failWithValidationErrors(item.errors)
                                }
                            }
                            else { 
                                return resultFactory.failWithValidationErrors(receipt.errors) 
                            }
                        }
                        else { return res }
                    }
                }
            }
            if (items) { resultFactory.success(items) }
            else { resultFactory.failWithMessage(BAD_REQUEST, "lockService.addToRecordWithReceipt.allBlocked") }
        }
        catch (StaleObjectStateException e) {
            if (attemptNum < Constants.LOCK_RETRY_MAX) {
                addToRecordWithReceipt(type, contacts, callParams, receiptParams, attemptNum++)
            }
            else { resultFactory.failWithThrowable(e) }
        }
    }

    protected Result<RecordItem> createForRecordItemType(RecordItemType type, Record rec, Map createParams) {
        switch(type) {
            case RecordItemType.RECORD_CALL:
                rec.addCall(createParams, null)
                break
            case RecordItemType.RECORD_TEXT:
                rec.addText(createParams, null)
                break
            case RecordItemType.RECORD_NOTE:
                resultFactory.failWithMessage("lockService.createForRecordItemType.noteNoReceipt")
                break
        }
    }

    protected List listForRecordItemType(RecordItemType type, Record rec, String apiId) {
        switch(type) {
            case RecordItemType.RECORD_CALL:
                RecordCall.forRecordAndApiId(rec, apiId).list()
                break
            case RecordItemType.RECORD_TEXT:
                RecordText.forRecordAndApiId(rec, apiId).list()
                break
            case RecordItemType.RECORD_NOTE:
                []
                break
        }
    }
}
