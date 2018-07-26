package org.textup

import grails.async.Promise
import grails.async.Promises
import grails.compiler.GrailsCompileStatic
import grails.transaction.Transactional
import org.textup.type.*
import org.textup.validator.*

@GrailsCompileStatic
@Transactional
class OutgoingMessageService {

    CallService callService
    MediaService mediaService
    ResultFactory resultFactory
    TokenService tokenService

    // Sending
    // -------

    Result<RecordCall> startBridgeCall(Phone phone, Contactable c1, Staff staff) {
        PhoneNumber fromNum = (c1 instanceof SharedContact) ? c1.sharedBy.number : phone.number,
            toNum = staff.personalPhoneNumber
        Map afterPickup = [contactId:c1.contactId, handle:CallResponse.FINISH_BRIDGE]
        callService.start(fromNum, toNum, afterPickup)
            .then { TempRecordReceipt receipt ->
                c1.tryGetRecord()
                    .then { Record rec1 -> rec1.storeOutgoingCall(staff.toAuthor()) }
                    .then { RecordCall rCall1 ->
                        rCall1.addReceipt(receipt)
                        resultFactory.success(rCall1)
                    }
            }
    }

    Result<Map<Contactable, Result<List<TempRecordReceipt>>>> sendForContactables(Phone phone,
        List<Contactable> recipients, OutgoingMessage msg1, MediaInfo mInfo = null) {

        try {
            Map<Contactable, Result<List<TempRecordReceipt>>> resultMap = [:]
            // this returns a call token that has ALREADY BEEN SAVED. If a call token is returned
            // instead of a null value, then that means that we are sending out this message as a call
            // See `mediaService.sendWithMedia` to see how this is handled
            Token callToken = tokenService.tryBuildAndPersistCallToken(phone.name, msg1)
            List<Map<Contactable, Result<List<TempRecordReceipt>>>> resList = Helpers
                .<Contactable, Map<Contactable, Result<List<TempRecordReceipt>>>>doAsyncInBatches(
                    recipients, { List<Contactable> batchSoFar ->
                        sendContactableBatch(phone, batchSoFar, msg1.message, mInfo, callToken)
                    })
            resList.each { Map<Contactable, Result<List<TempRecordReceipt>>> batchMap ->
                resultMap << batchMap
            }
            resultFactory.success(resultMap)
        }
        catch (Throwable e) {
            log.error("OutgoingMessageService.sendForContactables: ${e.class}, ${e.message}")
            e.printStackTrace()
            resultFactory.failWithThrowable(e)
        }
    }

    protected Promise<Map<Contactable, Result<List<TempRecordReceipt>>>> sendContactableBatch(
        Phone phone, List<Contactable> batchSoFar, String contents1,
        MediaInfo mInfo = null, Token callToken = null) {

        // store needed data outside of the promise closure to prevent accidentally making
        // any db calls inside of the closure and triggering a `no session` error
        Map<Long, PhoneNumber> contactIdToFromNum = [:]
        Map<Long, List<ContactNumber>> contactIdToNums = [:]
        batchSoFar.each { Contactable c1 ->
            Long cId = c1.contactId
            contactIdToFromNum[cId] = c1.fromNum
            contactIdToNums[cId] = c1.sortedNumbers
        }
        // NO HIBERNATE SESSION WITHIN NEW THREAD!!
        // Any calls that will make a db call needs to be made outside of the task closure
        Promises.task {
            Map<Contactable, Result<List<TempRecordReceipt>>> contactableToRes = [:]
            batchSoFar.each { Contactable c1 ->
                Long cId = c1.contactId
                PhoneNumber fromNum = contactIdToFromNum[cId]
                List<ContactNumber> toNums = contactIdToNums[cId]
                contactableToRes[c1] = mediaService.sendWithMedia(fromNum, toNums, contents1,
                    mInfo, callToken)
            }
            contactableToRes
        }
    }

    // Storing
    // -------

    // Note: MediaInfo may be null
    ResultGroup<RecordItem> storeForContactables(OutgoingMessage msg1, MediaInfo mInfo,
        Author author1, Closure<Void> doWhenAddingReceipt,
        Map<Contactable, Result<List<TempRecordReceipt>>> contactableToReceiptResults) {

        ResultGroup<RecordItem> resGroup = new ResultGroup<>()
        contactableToReceiptResults.each { Contactable c1, Result<List<TempRecordReceipt>> res ->
            resGroup << res.then { List<TempRecordReceipt> receipts ->
                c1.tryGetRecord().then { Record rec1 ->
                    Result<? extends RecordItem> storeRes = msg1.isText ?
                        rec1.storeOutgoingText(msg1.message, author1, mInfo) :
                        rec1.storeOutgoingCall(author1, msg1.message, mInfo)
                    storeRes.then { RecordItem item1 ->
                        receipts.each { TempRecordReceipt receipt ->
                            item1.addReceipt(receipt)
                            doWhenAddingReceipt(c1.contactId, receipt)
                        }
                        resultFactory.success(item1, ResultStatus.CREATED)
                    }
                }
            }
        }
        resGroup
    }

    // Note: MediaInfo may be null
    ResultGroup<RecordItem> storeForTags(OutgoingMessage msg1, MediaInfo mInfo, Author author1,
        Closure<List<TempRecordReceipt>> getReceiptsFromContactId, ResultGroup<RecordItem> resGroup) {
        if (resGroup.anySuccesses) {
            msg1.tags.each { ContactTag ct1 ->
                Result<? extends RecordItem> storeRes = msg1.isText ?
                    ct1.record.storeOutgoingText(msg1.message, author1, mInfo) :
                    ct1.record.storeOutgoingCall(author1, msg1.message, mInfo)
                resGroup << storeRes.then { RecordItem tagItem ->
                    ct1.members.each { Contact c1 ->
                        // add contact msg's receipts to tag's msg
                        getReceiptsFromContactId(c1.id)?.each { TempRecordReceipt r ->
                            tagItem.addReceipt(r)
                        }
                    }
                    resultFactory.success(tagItem, ResultStatus.CREATED)
                }
            }
        }
        resGroup
    }
}
