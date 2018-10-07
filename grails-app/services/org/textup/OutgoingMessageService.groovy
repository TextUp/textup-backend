package org.textup

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import java.util.concurrent.Future
import org.codehaus.groovy.grails.web.util.TypeConvertingMap
import org.textup.rest.*
import org.textup.type.*
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
            VoiceLanguage lang = Helpers.convertEnum(VoiceLanguage, data.language)
            CallTwiml.directMessage(data.identifier as String, lang, data.message  as String)
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

        Future<?> future = Helpers.noOpFuture()
        if (!phone.isActive) {
            Result<RecordItem> failRes = resultFactory
                .failWithCodeAndStatus("phone.isInactive", ResultStatus.NOT_FOUND)
            return Tuple.create(failRes.toGroup(), future)
        }
        // step 1: create initial domain classes
        ResultGroup<RecordItem> resGroup = buildMessages(msg1, staff.toAuthor())
        if (!resGroup.anyFailures) {
            Map<Long, List<RecordItem>> recordIdToItems = [:].withDefault { [] as List<RecordItem> }
            resGroup.payload.each { RecordItem i1 -> recordIdToItems[i1.record.id] << i1 }
            // step 2: finish all other long-running tasks asynchronously
            future = threadService.submit {
                finishProcessingMessages(recordIdToItems, phone.name, msg1, mediaFuture)
                    .logFail("OutgoingMessageService.processMessage: finish processing")
            }
        }
        Tuple.create(resGroup, future)
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

    protected ResultGroup<?> finishProcessingMessages(Map<Long, List<RecordItem>> recordIdToItems,
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
                    msg1.media = mInfo2
                    sendAndStore(recordIdToItems, phoneName, msg1)
                }
        }
        else { sendAndStore(recordIdToItems, phoneName, msg1) }
    }

    protected ResultGroup<?> sendAndStore(Map<Long, List<RecordItem>> recordIdToItems,
        String phoneName, OutgoingMessage msg1) {

        Map<Long, List<ContactTag>> contactIdToTags = msg1.getContactIdToTags()
        // this returns a call token that has ALREADY BEEN SAVED. If a call token is returned
        // instead of a null value, then that means that we are sending out this message as a call
        // See `mediaService.sendWithMedia` to see how this is handled
        Token callToken = tokenService.tryBuildAndPersistCallToken(phoneName, msg1)
        ResultGroup<?> resGroup = new ResultGroup<>()
        msg1.toRecipients().each { Contactable c1 ->
            resGroup << outgoingMediaService
                .send(c1.fromNum, c1.sortedNumbers, msg1.message, msg1.media, callToken)
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
        resGroup
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
                    ResultStatus.NOT_FOUND, [rec1.id, tempReceipts*.apiId])
            }
        }
    }
}
