package org.textup

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import java.util.concurrent.*
import org.textup.annotation.*
import org.textup.rest.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*

@GrailsTypeChecked
@Transactional
class OutgoingMessageService {

    OutgoingMediaService outgoingMediaService
    TokenService tokenService
    ThreadService threadService

    Result<Tuple<List<? extends RecordItem>, Future<?>>> tryStart(RecordItemType type, Recipients r1,
        TempRecordItem temp1, Author author1, Future<Result<?>> mediaFuture = null) {
        // step 1: create initial domain classes
        ResultGroup<? extends RecordItem> resGroup = new ResultGroup<>()
        r1?.eachRecord { Record rec1 ->
            resGroup << rec1.storeOutgoing(type, author1, temp1?.text, temp1?.media)
        }
        resGroup.toResult(false)
            .then { List<? extends RecordItem> rItems ->
                // step 2: finish all other long-running tasks asynchronously
                // Spock integration tests are run inside of a transaction that is rolled back at the
                // end of the test. This means that test data in the db is not accessible from another
                // thread. This seeems to be a limitation of the integration testing environment only.
                if (rItems) {
                    Future<?> future = threadService.delay(10, TimeUnit.SECONDS) {
                        DehydratedRecipients.tryCreate(r1)
                            .then { DehydratedRecipients dr1 ->
                                DehydratedTempRecordItem.tryCreate(temp1).curry(dr1)
                            }
                            .thenEnd { DehydratedRecipients dr1, DehydratedTempRecordItem dTemp1 ->
                                waitForMedia(type, resGroup.payload*.id, dr1, dTemp1, mediaFuture)
                            }
                    }
                    IOCUtils.resultFactory.success(rItems, future)
                }
                else {
                    IOCUtils.resultFactory.success(rItems, AsyncUtils.noOpFuture())
                }
            }
    }

    // Helpers
    // -------

    protected void waitForMedia(RecordItemType type, Collection<Long> itemIds,
        Rehydratable<Recipients> dr1, Rehydratable<TempRecordItem> dTemp1, Future<Result<?>> fut) {

        if (fut) {
            Result<?> res = fut.get()
            if (res) {
                res.then { finishProcessing(type, itemIds, dr1, dTemp1) }
            }
            else {
                log.error("waitForMedia: no result from media future for `$itemIds`")
                finishProcessing(type, itemIds, dr1, dTemp1)
            }
        }
        else { finishProcessing(type, itemIds, dr1, dTemp1) }
    }

    protected void finishProcessing(RecordItemType type, Collection<Long> itemIds,
        Rehydratable<Recipients> dr1, Rehydratable<TempRecordItem> dTemp1) {

        dr1.tryRehydrate()
            .then { Recipients r1 -> dTemp1.tryRehydrate().curry(r1) }
            .then { Recipients r1, TempRecordItem temp1 ->
                // this returns a call token that has ALREADY BEEN SAVED. If a call token is returned
                // instead of a null value, then that means that we are sending out this message as
                // a call. See `outgoingMediaService.trySend` to see how this is handled
                tokenService.tryBuildAndPersistCallToken(type, r1, temp1).curry(r1, temp1)
            }
            .thenEnd { Recipients r1, TempRecordItem temp1, Token callToken ->
                Collection<RecordItem> rItems = AsyncUtils.getAllIds(RecordItem, itemIds)
                Map<Long, Collection<RecordItem>> recIdToItems = MapUtils
                    .buildManyUniqueObjectsMap(rItems) { RecordItem rItem1 -> rItem1.record.id }
                r1.eachIndividualWithRecords { IndividualPhoneRecordWrapper w1, Collection<Record> recs ->
                    sendAndStore(w1, recs, temp1, recIdToItems, callToken)
                        .logFail("finishProcessing: sending PhoneRecord `${w1.id}`")
                }
            }
    }

    // No need to send items through socket here. Status callbacks will send items.
    protected Result<Void> sendAndStore(IndividualPhoneRecordWrapper w1, Collection<Record> records,
        TempRecordItem temp1, Map<Long, Collection<RecordItem>> recIdToItems, Token callToken) {

        w1.tryGetOriginalPhone() // original phone so that sharing preserves client point of contact
            .then { Phone p1 -> w1.tryGetSortedNumbers().curry(p1) }
            .then { Phone p1, List<ContactNumber> toNums ->
                outgoingMediaService.trySend(p1.number, toNums, p1.customAccountId, temp1.text,
                    temp1.media, callToken)
            }
            .then { List<TempRecordReceipt> tempReceipts ->
                ResultGroup
                    .collect(records) { Record rec1 ->
                        Collection<RecordItem> rItems = recIdToItems[rec1.id]
                        if (rItems) {
                            rItems.each { RecordItem rItem1 -> rItem1.addAllReceipts(tempReceipts) }
                            DomainUtils.trySaveAll(rItems)
                        }
                        else {
                            IOCUtils.resultFactory.failWithCodeAndStatus(
                                "outgoingMessageService.itemsNotFound",
                                ResultStatus.NOT_FOUND, [rec1.id, tempReceipts*.apiId])
                        }
                    }
                    .logFail("sendAndStore")
                    .toEmptyResult(true)
            }
    }
}
