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

    Result<Closure> process(Phone p1, String apiId, String digits, IncomingSession session) {
        //if staff member is calling from personal phone to TextUp phone
        Staff s1 = p1.owner.buildAllStaff().find { Staff staff ->
            staff.personalPhoneAsString == session.numberAsString
        }
        if (s1) {
            handleSelfCall(p1, apiId, digits, s1)
        }
        else if (FeaturedAnnouncements.anyForPhoneId(p1.id)) {
            announcementCallbackService.handleAnnouncementCall(p1, digits, session) {
                relayCall(p1, apiId, session)
            }
        }
        else { relayCall(p1, apiId, session) }
    }

    // Helpers
    // -------

    protected Result<Closure> handleSelfCall(Phone p1, String apiId, String digits, Staff staff) {
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

                // socketService.sendContacts(wrappers) // TODO
                //     .logFail("PhoneRecordUtils.tryMarkUnread: sending via socket")

                ResultGroup.collect(wrappers) { IndividualPhoneRecordWrapper w1 ->
                    w1.tryGetRecord()
                        .then { Record rec1 ->
                            rec1.storeOutgoing(RecordItemType.CALL, staff.toAuthor())
                        }
                        .then { RecordCall rCall1 ->
                            rCall1.addReceipt(rpt)
                            DomainUtils.trySave(rCall1)
                        }
                }
                CallTwiml.selfConnecting(p1.number.e164PhoneNumber, pNum.number)
            }
    }

    protected Result<Closure> relayCall(Phone p1, String apiId, IncomingSession is1) {
        PhoneRecordUtils.tryMarkUnread(p1, is1.number)
            .then { List<IndividualPhoneRecordWrapper> wrappers ->

                // socketService.sendContacts(wrappers) // TODO
                //     .logFail("PhoneRecordUtils.tryMarkUnread: sending via socket")

                ResultGroup.collect(wrappers) { IndividualPhoneRecordWrapper w1 ->
                        w1.tryGetRecord()
                            .then { Record rec1 -> rec1.storeIncomingCall(apiId, is1) }
                            .then { RecordCall rCall1 ->
                                rCall1.addReceipt(rpt)
                                DomainUtils.trySave(rCall1)
                            }
                    }
                    .logFail("relayCall")
                    .toResult(true)
                    .curry(wrappers)
            }
            .then { List<IndividualPhoneRecordWrapper> wrappers, List<RecordCall> calls ->
                afterStoreForCall(p1, is1, calls, wrappers)
            }
    }

    // TODO
    protected Result<Closure> afterStoreForCall(Phone p1, IncomingSession is1,
        List<RecordCall> rCalls, List<Contact> notBlockedContacts) {

        // if contacts is empty, then he or she has been blocked by the user
        if (notBlockedContacts.isEmpty()) {
            return CallTwiml.blocked()
        }
        // try notify available staff members
        notificationService.build(rCalls)
            .then { List<OutgoingNotification> notifs ->
                if (notifs) {
                    handleNotificationsForIncomingCall(p1, is1, notifs)
                }
                else { handleAwayForIncomingCall(p1, is1, rCalls) }
            }
    }

    protected Result<Closure> handleNotificationsForIncomingCall(Phone p1, IncomingSession is1,
        List<OutgoingNotification> notifs) {

        // TODO restore
        // HashSet<String> numsToCall = new HashSet<>()
        // notifs.each { OutgoingNotification bn1 ->
        //     Staff s1 = bn1.staff
        //     if (s1?.personalPhoneAsString) {
        //         numsToCall << s1.personalPhoneNumber.e164PhoneNumber
        //     }
        // }
        // CallTwiml.connectIncoming(p1.number, is1.number, numsToCall)
    }

    protected Result<Closure> handleAwayForIncomingCall(Phone p1, IncomingSession is1,
        List<RecordCall> rCalls) {

        rCalls.each { RecordCall rCall -> rCall.hasAwayMessage = true }
        CallTwiml.recordVoicemailMessage(p1, is1.number)
    }
}
