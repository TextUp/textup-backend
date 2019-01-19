package org.textup

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import org.joda.time.DateTime
import org.textup.rest.*
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*

@GrailsTypeChecked
@Transactional
class OutgoingAnnouncementService {

    CallService callService
    SocketService socketService
    TextService textService

    Result<FeaturedAnnouncement> send(FeaturedAnnouncement fa1, Author author1) {
        sendTextAnnouncement(fa1, author1)
            .then { sendCallAnnouncement(fa1, author1) }
            .then { DomainUtils.trySave(fa1) }
    }

    // Helpers
    // -------

    protected Result<?> sendTextAnnouncement(FeaturedAnnouncement fa1, Author author1) {
        Phone p1 = fa1.phone
        String msg = TwilioUtils.formatAnnouncementForSend(p1.owner.buildName(), fa1.message)

        ResultGroup<Tuple<IncomingSession, TempRecordReceipt>> resGroup = new ResultGroup<>()
        IncomingSession.findAllByPhoneAndIsSubscribedToText(p1, true)
            .each { IncomingSession is1 ->
                resGroup << textService.send(p1.number, [is1.number], msg, p1.customAccountId)
                    .then { TempRecordReceipt rpt1 -> Tuple.create(is1, rpt1) }
            }
        Tuple.split(resGroup.payload) { List<IncomingSession> sess, List<TempRecordReceipt> rpts ->
            tryStore(RecordItemType.CALL, fa1, author1, msg, sess, rpts)
        }
    }

    protected Result<?> sendCallAnnouncement(FeaturedAnnouncement fa1, Author author1) {
        Phone p1 = fa1.phone
        Map<String, String> pickup = CallTwiml
            .infoForAnnouncementAndDigits(p1.owner.buildName(), fa1.message)

        ResultGroup<Tuple<IncomingSession, TempRecordReceipt>> resGroup = new ResultGroup<>()
        IncomingSession.findAllByPhoneAndIsSubscribedToCall(p1, true)
            .each { IncomingSession is1 ->
                resGroup << callService.start(p1.number, [is1.number], pickup, p1.customAccountId)
                    .then { TempRecordReceipt rpt1 -> Tuple.create(is1, rpt1) }
            }
        Tuple.split(resGroup.payload) { List<IncomingSession> sess, List<TempRecordReceipt> rpts ->
            tryStore(RecordItemType.CALL, fa1, author1, null, sess, rpts)
        }
    }

    protected Result<?> tryStore(RecordItemType type, FeaturedAnnouncement fa1, Author author1,
        String msg, Collection<IncomingSession> sess, Collection<TempRecordReceipt> rpts) {

        tryStoreForAnnouncement(type, fa1, sess)
            .then { tryStoreForRecords(type, fa1.phone, author, msg, sess, rpts) }
            .then { List<IndividualPhoneRecord> iprList ->
                socketService.sendIndividualWrappers(iprList)
                IOCUtils.resultFactory.success()
            }
    }

    protected Result<List<AnnouncementReceipt>> tryStoreForAnnouncement(RecordItemType type,
        FeaturedAnnouncement fa1, List<IncomingSession> sess) {

        ResultGroup<AnnouncementReceipt> resGroup = new ResultGroup<>()
        sess.each { IncomingSession is1 ->
            resGroup << AnnouncementReceipt.tryCreate(fa1, is1, RecordItemType.TEXT)
        }
        resGroup
            .logFail("tryStoreForAnnouncement: $type, $fa1")
            .toResult(false)
    }

    protected Result<List<IndividualPhoneRecord>> tryStoreForRecords(RecordItemType type,
        Phone p1, Author author1, String msg, Collection<IncomingSession> sess,
        Collection<TempRecordReceipt> rpts) {

        findPhoneRecords(p1, sess*.number, rpts)
            .logFail("tryStoreForRecords: finding phone records")
            .then { Map<TempRecordReceipt, List<IndividualPhoneRecord>> rptToPhoneRecords ->
                ResultGroup<? extends RecordItem> resGroup = new ResultGroup<>()
                rptToPhoneRecords.each { TempRecordReceipt rpt1, List<IndividualPhoneRecord> iprList ->
                    resGroup << tryCreateItems(iprList*.record, type, author1, msg, rpt1)
                }
                resGroup
                    .toResult(true)
                    .curry(CollectionUtils.mergeUnique(*rptToPhoneRecords.values()))
            }
            .logFail("tryStoreForRecords: creating record items")
            .then { List<IndividualPhoneRecord> iprList ->  IOCUtils.resultFactory.success(iprList) }
    }

    protected Result<Map<TempRecordReceipt, List<IndividualPhoneRecord>>> findPhoneRecords(Phone p1,
        List<BasePhoneNumber> bNums, Collection<TempRecordReceipt> rpts) {

        Map<TempRecordReceipt, List<IndividualPhoneRecord>> rptToPhoneRecords = [:]
            .withDefault { [] as List<IndividualPhoneRecord> }
        Map<PhoneNumber, TempRecordReceipt> numToReceipt = MapUtils
            .buildObjectMap(rpts) { TempRecordReceipt rpt1 -> rpt1.contactNumber }

        IndividualPhoneRecords.tryFindEveryByNumbers(p1, bNums, true)
            .then { Map<PhoneNumber, List<IndividualPhoneRecord>> numToPhoneRecords ->
                numToPhoneRecords.each { PhoneNumber pNum, List<IndividualPhoneRecord> iprList ->
                    if (numToReceipt.containsKey(pNum)) {
                        rptToPhoneRecords[numToReceipt[pNum]].addAll(iprList)
                    }
                }
                IOCUtils.resultFactory.success(rptToPhoneRecords)
            }
    }

    protected Result<Void> tryCreateItems(Collection<Record> records,
        RecordItemType type, Author author1, String msg, TempRecordReceipt rpt1) {

        ResultGroup<? extends RecordItem> resGroup = new ResultGroup<>()
        records.each { Record rec1 -> resGroup << rec1.storeOutgoing(type, author1, msg) }
        resGroup.toResult(true)
            .logFail("tryCreateItems: $type")
            .then { List<? extends RecordItem> rItems ->
                rItems.each {
                    rItem1.addReceipt(rpt1)
                    rItem1.isAnnouncement = true
                }
                DomainUtils.trySaveAll(rItems)
            }
    }
}
