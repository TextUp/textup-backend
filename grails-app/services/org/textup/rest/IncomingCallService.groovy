package org.textup.rest

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import org.textup.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*

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
            .find { Staff staff -> staff.personalNumberAsString == is1.numberAsString }
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
            .then { PhoneNumber pNum, TempRecordReceipt rpt, List<IndividualPhoneRecordWrapper> wraps ->
                socketService.sendIndividualWrappers(wraps)
                ResultGroup
                    .collect(wraps) { IndividualPhoneRecordWrapper w1 ->
                        w1.tryGetRecord()
                            .then { Record rec1 ->
                                rec1.storeOutgoing(RecordItemType.CALL, Author.create(s1))
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
            .then { List<IndividualPhoneRecordWrapper> wraps ->
                socketService.sendIndividualWrappers(wraps)
                ResultGroup
                    .collect(wraps) { IndividualPhoneRecordWrapper w1 ->
                        // only store for owner because owner + shared all point to the same
                        // record object and we want to avoid duplicates in the record
                        w1.tryUnwrap().then { PhoneRecord pr1 ->
                            pr1.record.storeIncoming(RecordItemType.CALL, Author.create(is1),
                                is1.number, apiId)
                        }
                    }
                    .toResult(true)
            }
            .then { List<RecordCall> rCalls -> finishRelayCall(p1, is1, rCalls) }
    }

    protected Result<Closure> finishRelayCall(Phone p1, IncomingSession is1,
        List<RecordCall> rCalls) {

        if (rCalls) {
            NotificationUtils.tryBuildNotificationGroup(rCalls)
                .then { NotificationGroup notifGroup ->
                    buildCallResponse(p1, is1, rCalls, notifGroup)
                }
        }
        else { CallTwiml.blocked() } // if no calls saved, then was blocked
    }

    protected Result<Closure> buildCallResponse(Phone p1, IncomingSession is1,
        List<RecordCall> rCalls, NotificationGroup notifGroup) {

        Collection<? extends ReadOnlyOwnerPolicy> policies = notifGroup
            .buildCanNotifyReadOnlyPoliciesAllFrequencies()
        Collection<? extends ReadOnlyStaff> staffs = policies.collect { it.readOnlyStaff }
        Collection<? extends ReadOnlyStaff> withPersonalNumbers = staffs.findAll { it.hasPersonalNumber() }
        if (withPersonalNumbers) {
            Collection<PhoneNumber> pNums = withPersonalNumbers.collect { it.personalNumber }
            CallTwiml.connectIncoming(p1.number, is1.number, pNums)
        }
        else {
            rCalls.each { RecordCall rCall -> rCall.hasAwayMessage = true }
            DomainUtils.trySaveAll(rCalls)
                .then { CallTwiml.recordVoicemailMessage(p1, is1.number) }
        }
    }
}
