package org.textup

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import org.joda.time.DateTime
import org.textup.annotation.*
import org.textup.rest.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
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
        List<IncomingSession> sess1 = IncomingSession.findAllByPhoneAndIsSubscribedToText(p1, true)
        ResultGroup
            .collect(sess1) { IncomingSession is1 ->
                textService.send(p1.number, [is1.number], msg, p1.customAccountId)
                    .then { TempRecordReceipt rpt1 -> Tuple.tryCreate(is1, rpt1) }
            }
            .logFail("sendTextAnnouncement: announcement `${fa1.id}`")
            .toResult(true)
            .then { List<Tuple<IncomingSession, TempRecordReceipt>> tup1 ->
                Tuple.split(tup1) { List<IncomingSession> sess2, List<TempRecordReceipt> rpts ->
                    tryStore(RecordItemType.TEXT, fa1, author1, msg, sess2, rpts)
                }
            }
    }

    protected Result<?> sendCallAnnouncement(FeaturedAnnouncement fa1, Author author1) {
        Phone p1 = fa1.phone
        Map<String, String> pickup = CallTwiml
            .infoForAnnouncementAndDigits(p1.owner.buildName(), fa1.message)
        List<IncomingSession> sess1 = IncomingSession.findAllByPhoneAndIsSubscribedToCall(p1, true)
        ResultGroup
            .collect(sess1) { IncomingSession is1 ->
                callService.start(p1.number, [is1.number], pickup, p1.customAccountId)
                    .then { TempRecordReceipt rpt1 -> Tuple.tryCreate(is1, rpt1) }
            }
            .logFail("sendCallAnnouncement: announcement `${fa1.id}`")
            .toResult(true)
            .then { List<Tuple<IncomingSession, TempRecordReceipt>> tup1 ->
                Tuple.split(tup1) { List<IncomingSession> sess2, List<TempRecordReceipt> rpts ->
                    tryStore(RecordItemType.CALL, fa1, author1, null, sess2, rpts)
                }
            }
    }

    protected Result<?> tryStore(RecordItemType type, FeaturedAnnouncement fa1, Author author1,
        String msg, Collection<IncomingSession> sess, Collection<TempRecordReceipt> rpts) {

        tryStoreForAnnouncement(type, fa1, sess)
            .then {
                Map<PhoneNumber, TempRecordReceipt> numToRpt = MapUtils
                    .buildObjectMap(rpts) { TempRecordReceipt tempRpt1 -> tempRpt1.contactNumber }
                tryStoreForRecords(type, fa1.phone, author1, msg, sess, numToRpt)
            }
            .then { List<IndividualPhoneRecord> iprList ->
                socketService.sendIndividualWrappers(iprList*.toWrapper())
                Result.void()
            }
    }

    protected Result<List<AnnouncementReceipt>> tryStoreForAnnouncement(RecordItemType type,
        FeaturedAnnouncement fa1, Collection<IncomingSession> sess) {

        ResultGroup
            .collect(sess) { IncomingSession is1 -> AnnouncementReceipt.tryCreate(fa1, is1, type) }
            .logFail("tryStoreForAnnouncement: $type, $fa1")
            .toResult(false)
    }

    protected Result<List<IndividualPhoneRecord>> tryStoreForRecords(RecordItemType type,
        Phone p1, Author author1, String msg, Collection<IncomingSession> sess,
        Map<PhoneNumber, TempRecordReceipt> numToRpt) {

        IndividualPhoneRecords.tryFindOrCreateNumToObjByPhoneAndNumbers(p1, sess*.number, true, false)
            .then { Map<PhoneNumber, Collection<IndividualPhoneRecord>> numToPhoneRecs ->
                ResultGroup<RecordItem> resGroup = new ResultGroup<>()
                numToPhoneRecs.each { PhoneNumber pNum, Collection<IndividualPhoneRecord> iprs ->
                    iprs*.record.each { Record rec1 ->
                        resGroup << tryStoreForRecordItem(rec1, type, author1, msg, numToRpt[pNum])
                    }
                }
                resGroup.logFail("tryStoreForRecords")
                IOCUtils.resultFactory.success(CollectionUtils.mergeUnique(numToPhoneRecs.values()))
            }
    }

    protected Result<? extends RecordItem> tryStoreForRecordItem(Record rec1, RecordItemType type,
        Author author1, String msg, TempRecordReceipt tempRpt1 = null) {

        rec1.storeOutgoing(type, author1, msg)
            .then { RecordItem rItem1 ->
                if (tempRpt1) {
                    rItem1.addReceipt(tempRpt1)
                }
                rItem1.isAnnouncement = true
                DomainUtils.trySave(rItem1)
            }
    }
}
