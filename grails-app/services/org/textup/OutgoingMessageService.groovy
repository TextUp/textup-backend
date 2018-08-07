package org.textup

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import org.apache.commons.lang3.tuple.Pair
import org.textup.type.*
import org.textup.validator.*

@GrailsTypeChecked
@Transactional
class OutgoingMessageService {

    CallService callService
    MediaService mediaService
    ResultFactory resultFactory
    TokenService tokenService

    // Outgoing calls
    // --------------

    Result<RecordCall> startBridgeCall(Phone p1, Contactable c1, Staff s1) {
        PhoneNumber fromNum = (c1 instanceof SharedContact) ? c1.sharedBy.number : p1.number,
            toNum = s1.personalPhoneNumber
        Map afterPickup = [contactId:c1.contactId, handle:CallResponse.FINISH_BRIDGE]
        callService.start(fromNum, toNum, afterPickup)
            .then(this.&afterBridgeCall.curry(c1, s1))
    }

    protected Result<RecordCall> afterBridgeCall(Contactable c1, Staff s1, TempRecordReceipt rpt) {
        c1.tryGetRecord()
            .then { Record rec1 -> rec1.storeOutgoingCall(s1.toAuthor()) }
            .then { RecordCall rCall1 ->
                rCall1.addReceipt(rpt)
                resultFactory.success(rCall1, ResultStatus.CREATED)
            }
    }

    // Outgoing message
    // ----------------

    ResultGroup<RecordItem> sendMessage(Phone phone, OutgoingMessage msg1, MediaInfo mInfo = null,
        Staff staff = null) {
        // step 1: initialize variables
        List<Contactable> recipients = msg1.toRecipients().toList()
        Author author1 = staff?.toAuthor()
        Map<Long, List<TempRecordReceipt>> contactIdToReceipts = [:]
            .withDefault { [] as List<TempRecordReceipt> }
        Closure<List<TempRecordReceipt>> getReceipts = contactIdToReceipts.&get
        Closure<Void> addReceipts = { Long contactId, TempRecordReceipt r1 ->
            contactIdToReceipts[contactId]?.add(r1)
            return
        }
        // step 2: perform actions
        Result<Map<Contactable, Result<List<TempRecordReceipt>>>> res = sendForContactables(phone,
            recipients, msg1, mInfo)
        if (res.success) {
            ResultGroup<RecordItem> resGroup = storeForContactables(msg1, mInfo, author1,
                addReceipts, res.payload)
            storeForTags(msg1, mInfo, author1, getReceipts, resGroup)
            resGroup
        }
        else { res.toGroup() }
    }

    protected Result<Map<Contactable, Result<List<TempRecordReceipt>>>> sendForContactables(Phone phone,
        List<Contactable> recipients, OutgoingMessage msg1, MediaInfo mInfo = null) {
        try {
            Map<Contactable, Result<List<TempRecordReceipt>>> resultMap = [:]
            // this returns a call token that has ALREADY BEEN SAVED. If a call token is returned
            // instead of a null value, then that means that we are sending out this message as a call
            // See `mediaService.sendWithMedia` to see how this is handled
            Token callToken = tokenService.tryBuildAndPersistCallToken(phone.name, msg1)
            List<Pair<Contactable, Result<List<TempRecordReceipt>>>> resList = Helpers
                .<Contactable>doAsyncInBatches(recipients, { Contactable c1 ->
                    Pair.of(c1, mediaService.sendWithMedia(c1.fromNum, c1.sortedNumbers,
                        msg1.message, mInfo, callToken))
                })
            resList.each { Pair<Contactable, Result<List<TempRecordReceipt>>> pair ->
                resultMap[pair.left] = pair.right
            }
            resultFactory.success(resultMap)
        }
        catch (Throwable e) {
            log.error("OutgoingMessageService.sendForContactables: ${e.class}, ${e.message}")
            e.printStackTrace()
            resultFactory.failWithThrowable(e)
        }
    }

    // Note: MediaInfo may be null
    protected ResultGroup<RecordItem> storeForContactables(OutgoingMessage msg1, MediaInfo mInfo,
        Author author1, Closure<Void> doWhenAddingReceipt,
        Map<Contactable, Result<List<TempRecordReceipt>>> contactableToReceiptResults) {

        ResultGroup<RecordItem> resGroup = new ResultGroup<>()
        contactableToReceiptResults.each { Contactable c1, Result<List<TempRecordReceipt>> res ->
            resGroup << res.then { List<TempRecordReceipt> receipts ->
                // do not curry for mocking during testing
                storeForSingleContactable(msg1, mInfo, author1, doWhenAddingReceipt, c1, receipts)
            }
        }
        resGroup
    }
    protected Result<RecordItem> storeForSingleContactable(OutgoingMessage msg1, MediaInfo mInfo,
        Author author1, Closure<Void> doWhenAddingReceipt, Contactable c1,
        List<TempRecordReceipt> receipts) {

        Result<Record> res = c1.tryGetRecord()
        if (!res.success) { return res }
        Record rec1 = res.payload
        Result<? extends RecordItem> storeRes = msg1.isText ?
            rec1.storeOutgoingText(msg1.message, author1, mInfo) :
            rec1.storeOutgoingCall(author1, msg1.message, mInfo)
        storeRes.then { RecordItem item1 ->
            receipts.each { TempRecordReceipt rpt ->
                item1.addReceipt(rpt)
                doWhenAddingReceipt(c1.contactId, rpt)
            }
            resultFactory.success(item1, ResultStatus.CREATED)
        }
    }

    // Note: MediaInfo may be null
    protected ResultGroup<RecordItem> storeForTags(OutgoingMessage msg1, MediaInfo mInfo, Author author1,
        Closure<List<TempRecordReceipt>> getReceiptsFromContactId, ResultGroup<RecordItem> resGroup) {
        if (resGroup.anySuccesses) {
            msg1.tags?.recipients?.each { ContactTag ct1 ->
                // do not curry for mocking during testing
                resGroup << storeForSingleTag(msg1, mInfo, author1, getReceiptsFromContactId, ct1)
            }
        }
        resGroup
    }
    protected Result<RecordItem> storeForSingleTag(OutgoingMessage msg1, MediaInfo mInfo, Author author1,
        Closure<List<TempRecordReceipt>> getReceiptsFromContactId, ContactTag ct1) {

        Result<? extends RecordItem> storeRes = msg1.isText ?
            ct1.record.storeOutgoingText(msg1.message, author1, mInfo) :
            ct1.record.storeOutgoingCall(author1, msg1.message, mInfo)
        storeRes.then { RecordItem tagItem ->
            ct1.members.each { Contact c1 ->
                // add contact msg's receipts to tag's msg
                getReceiptsFromContactId(c1.id)?.each { TempRecordReceipt rpt ->
                    tagItem.addReceipt(rpt)
                }
            }
            resultFactory.success(tagItem, ResultStatus.CREATED)
        }
    }
}
