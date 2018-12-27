package org.textup

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import java.util.concurrent.*
import org.codehaus.groovy.grails.web.util.TypeConvertingMap
import org.textup.rest.*
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*

@GrailsTypeChecked
@Transactional
class OutgoingMessageService {

    CallService callService
    OutgoingMediaService outgoingMediaService
    ResultFactory resultFactory
    TokenService tokenService
    ThreadService threadService

    // Outgoing calls
    // --------------

    Result<Closure> directMessageCall(String token) {
        tokenService.findDirectMessage(token).then({ Token tok1 ->
            Map<String, ?> data = tok1.data
            String ident = data.identifier as String
            String msg = data.message as String
            VoiceLanguage lang = TypeConversionUtils.convertEnum(VoiceLanguage, data.language)
            List<URL> recordings = []
            Long mediaId = TypeConversionUtils.to(Long, data.mediaId)
            if (mediaId) {
                MediaInfo.get(mediaId)
                    ?.getMediaElementsByType(MediaType.AUDIO_TYPES)
                    ?.each { MediaElement el1 ->
                        URL link = el1.sendVersion?.link
                        if (link) {
                            recordings << link
                        }
                    }
            }
            CallTwiml.directMessage(ident, msg, lang, recordings)
        }, { Result<Token> failRes ->
            failRes.logFail("OutgoingMessageService.directMessageCall")
            CallTwiml.error()
        })
    }

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
        callService
            .start(fromNum, toNum, CallTwiml.infoForFinishBridge(c1))
            .then(this.&afterBridgeCall.curry(c1, s1))
    }

    Result<Closure> finishBridgeCall(TypeConvertingMap params) {
        Contact c1 = Contact.get(params.long("contactId"))
        CallTwiml.finishBridge(c1)
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

    Tuple<ResultGroup<RecordItem>, Future<?>> processMessage(Phone phone, OutgoingMessage msg1,
        Staff staff, Future<Result<MediaInfo>> mediaFuture = null) {

        Future<?> future = AsyncUtils.noOpFuture()
        if (!phone.isActive) {
            Result<RecordItem> failRes = resultFactory
                .failWithCodeAndStatus("phone.isInactive", ResultStatus.NOT_FOUND)
            return Tuple.create(failRes.toGroup(), future)
        }
        // step 1: create initial domain classes
        ResultGroup<? extends RecordItem> resGroup = buildMessages(msg1, staff.toAuthor())
        if (!resGroup.anyFailures) {
            Map<Long, List<Long>> recordIdToItemIds = [:].withDefault { [] as List<Long> }
            resGroup.payload.each { RecordItem i1 -> recordIdToItemIds[i1.record.id] << i1.id }
            // step 2: finish all other long-running tasks asynchronously
            // Spock integration tests are run inside of a transaction that is rolled back at the
            // end of the test. This means that test data in the db is not accessible from another
            // thread, so we need to make sure that we store the phone name before starting new thread.
            // This seeems to be a limitation of the integration testing environment. We tested in
            // the Grails console and we were able to access all data no matter which session or thread
            String phoneName = phone.name
            future = threadService.delay(10, TimeUnit.SECONDS) {
                finishProcessingMessages(recordIdToItemIds, phoneName, msg1, mediaFuture)
                    .logFail("OutgoingMessageService.processMessage: finish processing")
            }
        }

        Tuple.create(resGroup, future)
    }

    protected ResultGroup<? extends RecordItem> buildMessages(OutgoingMessage msg1, Author author1) {
        ResultGroup<? extends RecordItem> resGroup = new ResultGroup<>()
        // need to build message for each Contactable recipient
        msg1.toRecipients().each { Contactable cont1 ->
            resGroup << buildMessageForRecordOwner(cont1, msg1, author1)
        }
        // also store a copy in any tags we are sending to as well
        msg1.tags.recipients.each { ContactTag tag1 ->
            resGroup << buildMessageForRecordOwner(tag1, msg1, author1)
        }
        resGroup
    }
    protected Result<? extends RecordItem> buildMessageForRecordOwner(WithRecord recordOwner,
        OutgoingMessage msg1, Author author1) {

        recordOwner
            .tryGetRecord()
            .then { Record rec1 ->
                msg1.isText ?
                    rec1.storeOutgoingText(msg1.message, author1, msg1.media) :
                    rec1.storeOutgoingCall(author1, msg1.message, msg1.media)
            }
    }

    protected ResultGroup<?> finishProcessingMessages(Map<Long, List<Long>> recordIdToItemIds,
        String phoneName, OutgoingMessage msg1, Future<Result<MediaInfo>> mediaFuture = null) {

        Map<Long, List<RecordItem>> recordIdToItems = rebuildRecordIdToItemsMap(recordIdToItemIds)
        if (mediaFuture) {
            Result<MediaInfo> mediaRes = mediaFuture.get()
            if (!mediaRes) {
                return resultFactory.failWithCodeAndStatus(
                    "outgoingMediaService.finishProcessingMessages.futureMissingPayload",
                    ResultStatus.INTERNAL_SERVER_ERROR,
                    [msg1.media?.id, recordIdToItems?.keySet()]).toGroup()
            }
            if (mediaRes.success) {
                msg1.media = mediaRes.payload
                sendAndStore(recordIdToItems, phoneName, msg1)
            }
            else { mediaRes.toGroup() }
        }
        else { sendAndStore(recordIdToItems, phoneName, msg1) }
    }
    protected Map<Long, List<RecordItem>> rebuildRecordIdToItemsMap(Map<Long, List<Long>> recordIdToItemIds) {
        // step 1: collect all ids to fetch in one call from db
        Iterable<Serializable> itemIds = []
        recordIdToItemIds.each { Long recordId, List<Long> thisIds -> itemIds.addAll(thisIds) }
        // step 2: after fetching from db, build a map of item id to object for efficient retrieval
        Map<Long, RecordItem> idToObject = AsyncUtils.idMap(RecordItem.getAll(itemIds))
        // step 3: replace item id with item object in the passed-in map
        Map<Long, List<RecordItem>> recordIdToItems = [:]
        recordIdToItemIds.each { Long recordId, List<Long> thisIds ->
            List<RecordItem> thisItems = []
            thisIds.every { Long thisId ->
                RecordItem item = idToObject[thisId]
                if (item) {
                    thisItems << item
                }
                else { log.error("rebuildRecordIdToItemsMap: item `${thisId}` not found") }
            }
            recordIdToItems[recordId] = thisItems
        }
        recordIdToItems
    }
    protected ResultGroup<?> sendAndStore(Map<Long, List<RecordItem>> recordIdToItems,
        String phoneName, OutgoingMessage msg1) {

        Map<Long, List<ContactTag>> contactIdToTags = msg1.getContactIdToTags()
        // this returns a call token that has ALREADY BEEN SAVED. If a call token is returned
        // instead of a null value, then that means that we are sending out this message as a call
        // See `mediaService.sendWithMedia` to see how this is handled
        Token callToken = tokenService.tryBuildAndPersistCallToken(phoneName, msg1)
        ResultGroup<?> resGroup = new ResultGroup<>()
        msg1.toRecipients().each { Contactable cont1 ->
            outgoingMediaService
                .send(cont1.fromNum, cont1.sortedNumbers, msg1.message, msg1.media, callToken)
                .thenEnd { List<TempRecordReceipt> tempReceipts ->
                    resGroup << tryStoreReceipts(recordIdToItems, cont1, tempReceipts)
                    contactIdToTags[cont1.contactId]?.each { ContactTag ct1 ->
                        resGroup << tryStoreReceipts(recordIdToItems, ct1, tempReceipts)
                    }
                }
        }
        // No need to send items through socket here. Status callbacks will send items.
        resGroup
    }

    protected Result<Void> tryStoreReceipts(Map<Long, List<RecordItem>> recordIdToItems,
        WithRecord owner, List<TempRecordReceipt> tempReceipts) {

        owner.tryGetRecord().then { Record rec1 ->
            Collection<RecordItem> items = recordIdToItems[rec1.id]
            ResultGroup<?> failures = new ResultGroup<>()
            if (items) {
                items.each { RecordItem item1 ->
                    item1.addAllReceipts(tempReceipts)
                    if (!item1.save()) {
                        failures << resultFactory.failWithValidationErrors(item1.errors)
                    }
                }
                failures.isEmpty ? resultFactory.success() : resultFactory.failWithGroup(failures)
            }
            else {
                resultFactory.failWithCodeAndStatus("outgoingMessageService.tryStoreReceipts.notFound",
                    ResultStatus.NOT_FOUND, [rec1.id, tempReceipts*.apiId])
            }
        }
    }
}
