package org.textup.rest

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional

@GrailsTypeChecked
@Transactional
class IncomingCallService {

    AnnouncementCallbackService announcementCallbackService
    NotificationService notificationService
    SocketService socketService
    ThreadService threadService

    Result<Closure> process(Phone p1, IncomingSession is1, String apiId, String digits) {
        //if staff member is calling from personal phone to TextUp phone
        Staff s1 = p1.owner.buildAllStaff()
            .find { Staff staff -> staff.personalPhoneAsString == is1.numberAsString }
        if (s1) {
            handleSelfCall(p1, apiId, digits, s1)
        }
        else if (FeaturedAnnouncements.anyForPhoneId(p1.id)) {
            announcementCallbackService.handleAnnouncementCall(p1, is1, digits) {
                relayCall(p1, is1, apiId)
            }
        }
        else { relayCall(p1, is1, apiId) }
    }

    // Helpers
    // -------

    protected Result<Closure> handleSelfCall(Phone p1, String apiId, String digits, Staff s1) {
        if (!digits) {
            return CallTwiml.selfGreeting()
        }
        PhoneNumber.tryCreate(digits)
            .ifFail { CallTwiml.selfInvalid(digits) }
            .then { PhoneNumber pNum -> TempRecordReceipt.tryCreate(apiId, pNum).curry(pNum) }
            .ifFail("handleSelfCall: creating receipt") { CallTwiml.error() }
            .then { PhoneNumber pNum, TempRecordReceipt rpt ->
                PhoneRecordUtils.tryMarkUnread(p1, pNum).curry(pNum, rpt)
            }
            .then { TempRecordReceipt rpt, List<IndividualPhoneRecordWrapper> wrappers ->
                socketService.sendIndividualWrappers(wrappers)
                ResultGroup
                    .collect(wrappers) { IndividualPhoneRecordWrapper w1 ->
                        w1.tryGetRecord()
                            .then { Record rec1 ->
                                rec1.storeOutgoing(RecordItemType.CALL, s1.toAuthor())
                            }
                            .then { RecordCall rCall1 ->
                                rCall1.addReceipt(rpt)
                                DomainUtils.trySave(rCall1)
                            }
                    }
                    .logFail("handleSelfCall: storing")
                CallTwiml.selfConnecting(p1.number.e164PhoneNumber, pNum.number)
            }
    }

    protected Result<Closure> relayCall(Phone p1, IncomingSession is1, String apiId) {
        PhoneRecordUtils.tryMarkUnread(p1, is1.number)
            .then { List<IndividualPhoneRecordWrapper> wrappers ->
                socketService.sendIndividualWrappers(wrappers)
                ResultGroup
                    .collect(wrappers) { IndividualPhoneRecordWrapper w1 ->
                        w1.tryGetRecord()
                            .then { Record rec1 ->
                                rec1.storeIncoming(RecordItemType.CALL, is1.toAuthor(), is1.number, apiId)
                            }
                            .then { RecordCall rCall1 ->
                                rCall1.addReceipt(rpt)
                                DomainUtils.trySave(rCall1)
                            }
                    }
                    .logFail("relayCall")
                    .toResult(true)
            }
            .then { List<RecordCall> rCalls -> finishRelayCall(p1, is1, rCalls) }
    }

    protected Result<Closure> finishRelayCall(Phone p1, IncomingSession is1,
        List<RecordCall> rCalls) {

        if (rCalls) {
            notificationService.build(rCalls)
                .then { NotificationGroup notifGroup ->
                    buildCallResponse(p1, is1, rCalls, notifGroup)
                }
        }
        else { CallTwiml.blocked() } // if no calls saved, then was blocked
    }

    protected Result<Closure> buildCallResponse(Phone p1, IncomingSession is1,
        List<RecordCall> rCalls, NotificationGroup notifGroup) {

        Collection<OwnerPolicy> policies = notifGroup
            .buildCanNotifyPolicies(NotificationFrequency.IMMEDIATELY)
        Collection<PhoneNumber> pNums = CollectionUtils.ensureNoNull(policies*.staff*.personalPhone)
        if (pNums) {
            CallTwiml.connectIncoming(p1.number, is1.number, pNums)
        }
        else {
            rCalls.each { RecordCall rCall -> rCall.hasAwayMessage = true }
            DomainUtils.trySaveAll(rCalls)
                .then { CallTwiml.recordVoicemailMessage(p1, is1.number) }
        }
    }
}
