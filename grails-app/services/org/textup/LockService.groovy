package org.textup

import grails.transaction.Transactional
import org.hibernate.StaleObjectStateException

@Transactional
class LockService {

    def resultFactory

    /////////////////
    // CallService //
    /////////////////

    Result<RecordCall> retryCall(RecordCall call, Contact contact, Closure stopOnSuccessOrInternalError, int attemptNum=0) {
        try {
            call.lock()
            contact.lock()
            List<String> cNums = contact.numbers*.e164PhoneNumber,
                alreadySent = call.receipts*.receivedBy*.e164PhoneNumber,
                numsRemaining = cNums - alreadySent
            stopOnSuccessOrInternalError(call, contact.phone.number.e164PhoneNumber, numsRemaining)
        }
        catch (StaleObjectStateException e) {
            if (attemptNum < Constants.LOCK_RETRY_MAX) {
                retryCall(call, contact, attemptNum++)
            }
            else { resultFactory.failWithThrowable(e) }
        }
    }

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

    ///////////////////
    // RecordService //
    ///////////////////

    Result<List<RecordCall>> updateCallStatus(List<RecordItemReceipt> receipts, String status, Integer duration, int attemptNum=0) {
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
                updateCallStatus(receipts, status, duration, attemptNum++)
            }
            else { resultFactory.failWithThrowable(e) }
        }
    }

    Result<RecordCall> addRecordCallWithReceipt(Contact contact, Map callParams, Map receiptParams) {
        Result<List<RecordCall>> res = addRecordCallWithReceipt([contact], callParams, receiptParams)
        if (res.success) {
            resultFactory.success(res.payload[0])
        }
        else { res }
    }

    Result<List<RecordCall>> addRecordCallWithReceipt(List<Contact> contacts, Map callParams,
        Map receiptParams, int attemptNum=0) {
        try {
            List<RecordCall> calls = []
            contacts*.lock()
            for (contact in contacts) {
                Record rec = contact.record
                List<RecordCall> cList = RecordCall.forRecordAndApiId(rec, receiptParams.apiId).list()
                //add call and receipt to record if receipt with apiId doesn't already exist
                if (cList) { calls += cList }
                else {
                    rec.lock()
                    Result res = rec.addCall(callParams, null)
                    if (res.success) {
                        RecordCall call = res.payload
                        call.lock()
                        RecordItemReceipt receipt = new RecordItemReceipt(receiptParams)
                        call.addToReceipts(receipt)
                        if (receipt.save()) {
                            if (call.save()) {
                                if (contact.save()) {
                                    contact.updateLastRecordActivity()
                                    calls << call
                                }
                                else { resultFactory.failWithValidationErrors(contact.errors) }
                            }
                            else { resultFactory.failWithValidationErrors(call.errors) }
                        }
                        else { resultFactory.failWithValidationErrors(receipt.errors) }
                    }
                    else { res }
                }
            }
            resultFactory.success(calls)
        }
        catch (StaleObjectStateException e) {
            if (attemptNum < Constants.LOCK_RETRY_MAX) {
                updateCallStatus(receipts, status, duration, attemptNum++)
            }
            else { resultFactory.failWithThrowable(e) }
        }
    }
}
