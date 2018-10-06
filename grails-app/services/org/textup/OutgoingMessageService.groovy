package org.textup

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import org.apache.commons.lang3.tuple.Pair
import org.textup.rest.TwimlBuilder
import org.textup.type.*
import org.textup.validator.*

@GrailsTypeChecked
@Transactional
class OutgoingMessageService {

    CallService callService
    MediaService mediaService
    ResultFactory resultFactory
    TokenService tokenService
    TwimlBuilder twimlBuilder

    // Outgoing calls
    // --------------

    Result<RecordCall> startBridgeCall(Phone p1, Contactable c1, Staff s1) {
        if (!p1.isActive) {
            return resultFactory.failWithCodeAndStatus("phone.isInactive", ResultStatus.NOT_FOUND)
        }
        if (!s1.personalPhoneAsString) {
            return resultFactory.failWithCodeAndStatus(
                "outgoingMessageService.startBridgeCall.noPersonalNumber",
                ResultStatus.UNPROCESSABLE_ENTITY)
        }
        PhoneNumber fromNum = (c1 instanceof SharedContact) ? c1.sharedBy.number : p1.number,
            toNum = s1.personalPhoneNumber
        Map afterPickup = [contactId:c1.contactId, handle:CallResponse.FINISH_BRIDGE]
        callService.start(fromNum, toNum, afterPickup)
            .then(this.&afterBridgeCall.curry(c1, s1))
    }

    Result<Closure> finishBridgeCall(Contact c1) {
        twimlBuilder.build(CallResponse.FINISH_BRIDGE, [contact:c1])
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

    ResultGroup<RecordItem> processMessage(Phone phone, OutgoingMessage msg1, Staff staff,
        Future<Result<MediaInfo>> mediaFuture = null) {
        if (!phone.isActive) {
            resultFactory.failWithCodeAndStatus("phone.isInactive", ResultStatus.NOT_FOUND).toGroup()
        }
        // step 1: create initial domain classes
        ResultGroup<RecordItem> resGroup = buildMessages(msg1, staff.toAuthor())
        if (!resGroup.isAnyFailures) {
            Map<Long, List<RecordItem>> recordIdToItems = [:].withDefault { [] as List<RecordItem> }
            resGroup.payload.each { RecordItem i1 -> recordIdToItems[i1.record.id] << i1 }
            // step 2: finish all other long-running tasks asynchronously
            threadService.submit {
                finishProcessingMessages(recordIdToItems, msg1, mediaFuture)
            }
        }
        resGroup
    }

    protected ResultGroup<RecordItem> buildMessages(OutgoingMessage msg1, Author author1) {
        ResultGroup<RecordItem> resGroup = new ResultGroup<>()
        msg1.toRecordOwners().each { WithRecord recordOwner ->
            resGroup << recordOwner
                .tryGetRecord()
                .then { Record rec1 ->
                    msg1.isText ?
                        rec1.storeOutgoingText(msg1.message, author1, msg1.media) :
                        rec1.storeOutgoingCall(author1, msg1.message, msg1.media)
                }
        }
        resGroup
    }

    protected void finishProcessingMessages(Map<Long, List<RecordItem>> recordIdToItems,
        String phoneName, OutgoingMessage msg1, Future<Result<MediaInfo>> mediaFuture = null) {

        if (mediaFuture) {
            Result<MediaInfo> mediaRes = mediaFuture.get()
            if (!mediaRes) {
                return log.error("OutgoingMessageService.finishProcessingMessages: could not fetch media info \
                    for media with id `${msg1.media?.id}`")
            }
            mediaRes
                .logFail("OutgoingMessageService.finishProcessingMessages: processing media")
                .thenEnd { MediaInfo mInfo2 ->
                    outgoingMessageService.sendAndStore(recordIdToItems, phoneName, msg1, mInfo2)
                }
        }
        else { outgoingMessageService.sendAndStore(recordIdToItems, phoneName, msg1) }
    }

    protected void sendAndStore(Map<Long, List<RecordItem>> recordIdToItems,
        String phoneName, OutgoingMessage msg1, MediaInfo mInfo = null) {

        Map<Long, List<ContacTag>> contactIdToTags = msg1.getContactIdToTags()
        // this returns a call token that has ALREADY BEEN SAVED. If a call token is returned
        // instead of a null value, then that means that we are sending out this message as a call
        // See `mediaService.sendWithMedia` to see how this is handled
        Token callToken = tokenService.tryBuildAndPersistCallToken(phoneName, msg1)
        ResultGroup<?> resGroup = new ResultGroup<>()
        msg1.toRecipients().each { Contactable c1 ->
            resGroup << outgoingMediaService
                .send(c1.fromNum, c1.sortedNumbers, msg1.message, mInfo, callToken)
                .then { List<TempRecordReceipt> tempReceipts ->
                    tryStoreReceipts(recordIdToItems, c1, tempReceipts)
                        .logFail("OutgoingMessageService.finishProcessingMessages: contactable")
                    contactIdToTags[c1.contactId]?.each { ContactTag ct1 ->
                        tryStoreReceipts(recordIdToItems, ct1, tempReceipts)
                            .logFail("OutgoingMessageService.finishProcessingMessages: tag")
                    }
                }
        }
        // No need to send items through socket here. Status callbacks will send items.
        resGroup.logFail("OutgoingMessageService.finishProcessingMessages: sending")
    }

    protected Result<Void> tryStoreReceipts(Map<Long, List<RecordItem>> recordIdToItems,
        WithRecord owner, List<TempRecordReceipt> tempReceipts) {

        owner.tryGetRecord().then { Record rec1 ->
            Collection<RecordItem> items = recordIdToItems[rec1.id]
            if (items) {
                items.each { RecordItem item1 -> item1.addAllReceipts(tempReceipts) }
                resultFactory.success()
            }
            else {
                resultFactory.failWithCodeAndStatus("outgoingMessageService.tryStoreReceipts.notFound",
                    ResultFactory.NOT_FOUND, [rec1.id, tempReceipts*.apiId])
            }
        }
    }
}
