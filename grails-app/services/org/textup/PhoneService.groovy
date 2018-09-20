package org.textup

import com.twilio.rest.api.v2010.account.IncomingPhoneNumber
import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import org.apache.commons.lang3.tuple.Pair
import org.textup.type.*
import org.textup.validator.*
import org.textup.validator.action.*

@GrailsTypeChecked
@Transactional
class PhoneService {

    AnnouncementService announcementService
    AuthService authService
    IncomingMessageService incomingMessageService
    NotificationService notificationService
    NumberService numberService
    OutgoingMessageService outgoingMessageService
    ResultFactory resultFactory
    VoicemailService voicemailService

    Result<Staff> mergePhone(Staff s1, Map body, String timezone) {
        if (body.phone instanceof Map && Helpers.<Boolean>doWithoutFlush{ authService.isActive }) {
            Phone p1 = s1.phoneWithAnyStatus ?: new Phone([:])
            p1.updateOwner(s1)
            mergeHelper(p1, body.phone as Map, timezone)
                .then({ resultFactory.success(s1) })
        }
        else { resultFactory.success(s1) }
    }
    Result<Team> mergePhone(Team t1, Map body, String timezone) {
        if (body.phone instanceof Map && Helpers.<Boolean>doWithoutFlush{ authService.isActive }) {
            Phone p1 = t1.phoneWithAnyStatus ?: new Phone([:])
            p1.updateOwner(t1)
            mergeHelper(p1, body.phone as Map, timezone)
                .then({ resultFactory.success(t1) })
        }
        else { resultFactory.success(t1) }
    }

    // Updating helpers
    // ----------------

    protected Result<Phone> mergeHelper(Phone p1, Map body, String timezone) {
        handlePhoneActions(p1, body)
            .then { Phone p2 -> handleAvailability(p2, body, timezone) }
            .then { Phone p2 -> updateFields(p2, body) }
    }

    protected Result<Phone> updateFields(Phone p1, Map body) {
        if (body.awayMessage) {
            p1.awayMessage = body.awayMessage
        }
        if (body.voice) {
            p1.voice = Helpers.convertEnum(VoiceType, body.voice)
        }
        if (body.language) {
            p1.language = Helpers.convertEnum(VoiceLanguage, body.language)
        }
        if (p1.save()) {
            resultFactory.success(p1)
        }
        else { resultFactory.failWithValidationErrors(p1.errors) }
    }

    protected Result<Phone> handleAvailability(Phone p1, Map body, String timezone) {
        if (body.availability instanceof Map) {
            NotificationPolicy np1 = p1.owner.getOrCreatePolicyForStaff(authService.loggedIn.id)
            Result<?> res = notificationService.update(np1, body.availability as Map, timezone)
            if (!res.success) { return res }
        }
        resultFactory.success(p1)
    }

    // Phone actions
    // -------------

    protected Result<Phone> handlePhoneActions(Phone p1, Map body) {
        if (body.doPhoneActions) {
            ActionContainer ac1 = new ActionContainer(body.doPhoneActions)
            List<PhoneAction> actions = ac1.validateAndBuildActions(PhoneAction)
            if (ac1.hasErrors()) {
                return resultFactory.failWithValidationErrors(ac1.errors)
            }
            Collection<Result<?>> failResults = []
            for (PhoneAction a1 in actions) {
                Result<Phone> res
                switch (a1) {
                    case Constants.PHONE_ACTION_DEACTIVATE:
                        res = deactivatePhone(p1)
                        break
                    case Constants.PHONE_ACTION_TRANSFER:
                        res = transferPhone(p1, a1.id, a1.typeAsEnum)
                        break
                    case Constants.PHONE_ACTION_NEW_NUM_BY_NUM:
                        res = updatePhoneForNumber(p1, a1.phoneNumber)
                        break
                    default: // Constants.PHONE_ACTION_NEW_NUM_BY_ID
                        res = updatePhoneForApiId(p1, a1.numberId)
                }
                if (!res.success) { failResults << res }
            }
            if (failResults) {
                return resultFactory.failWithResultsAndStatus(failResults, ResultStatus.BAD_REQUEST)
            }
        }
        resultFactory.success(p1)
    }

    protected Result<Phone> deactivatePhone(Phone p1) {
        String oldApiId = p1.apiId
        p1.deactivate()
        if (!p1.validate()) {
            return resultFactory.failWithValidationErrors(p1.errors)
        }
        if (oldApiId) {
            numberService.freeExistingNumber(oldApiId).then({ resultFactory.success(p1) })
        }
        else { resultFactory.success(p1) }
    }

    protected Result<Phone> transferPhone(Phone p1, Long id, PhoneOwnershipType type) {
        p1.transferTo(id, type).then({ resultFactory.success(p1) })
    }

    protected Result<Phone> updatePhoneForNumber(Phone p1, PhoneNumber pNum) {
        if (!pNum.validate()) {
            return resultFactory.failWithValidationErrors(pNum.errors)
        }
        if (pNum.number == p1.numberAsString) {
            return resultFactory.success(p1)
        }
        if (Helpers.<Boolean>doWithoutFlush({ Phone.countByNumberAsString(pNum.number) > 0 })) {
            return resultFactory.failWithCodeAndStatus("phoneService.changeNumber.duplicate",
                ResultStatus.UNPROCESSABLE_ENTITY)
        }
        numberService.changeForNumber(pNum)
            .then({ IncomingPhoneNumber iNum -> numberService.updatePhoneWithNewNumber(iNum, p1) })
    }

    protected Result<Phone> updatePhoneForApiId(Phone p1, String apiId) {
        if (apiId == p1.apiId) {
            return resultFactory.success(p1)
        }
        if (Helpers.<Boolean>doWithoutFlush({ Phone.countByApiId(apiId) > 0 })) {
            return resultFactory.failWithCodeAndStatus("phoneService.changeNumber.duplicate",
                ResultStatus.UNPROCESSABLE_ENTITY)
        }
        numberService.changeForApiId(apiId)
            .then({ IncomingPhoneNumber iNum -> numberService.updatePhoneWithNewNumber(iNum, p1) })
    }

    // Outgoing
    // --------

    ResultGroup<RecordItem> sendMessage(Phone phone, OutgoingMessage msg1, MediaInfo mInfo = null,
        Staff staff = null) {
        outgoingMessageService.sendMessage(phone, msg1, mInfo, staff)
    }

    Result<RecordCall> startBridgeCall(Phone phone, Contactable c1, Staff staff) {
        outgoingMessageService.startBridgeCall(phone, c1, staff)
    }

    Map<String, Result<TempRecordReceipt>> sendTextAnnouncement(Phone phone, String message,
        String identifier, List<IncomingSession> sessions, Staff staff) {

        announcementService.sendTextAnnouncement(phone, message, identifier, sessions, staff)
    }
    Map<String, Result<TempRecordReceipt>> startCallAnnouncement(Phone phone, String message,
        String identifier, List<IncomingSession> sessions, Staff staff) {

        announcementService.startCallAnnouncement(phone, message, identifier, sessions, staff)
    }

	// Incoming
	// --------

    Result<Pair<Closure, List<RecordText>>> relayText(Phone phone, IncomingText text,
        IncomingSession session, MediaInfo mInfo = null) {

        incomingMessageService.relayText(phone, text, session, mInfo)
    }
    Result<Pair<Closure, List<RecordText>>> handleAnnouncementText(Phone phone, IncomingText text,
        IncomingSession session, MediaInfo mInfo = null) {

        announcementService.handleAnnouncementText(phone, text, session,
            { relayText(phone, text, session, mInfo) })
    }

    Result<Closure> relayCall(Phone phone, String apiId, IncomingSession session) {
        incomingMessageService.relayCall(phone, apiId, session)
    }
    Result<Closure> handleAnnouncementCall(Phone phone, String apiId, String digits,
        IncomingSession session) {

        announcementService.handleAnnouncementCall(phone, digits, session,
            { relayCall(phone, apiId, session) })
    }

    Result<Closure> screenIncomingCall(Phone phone, IncomingSession session) {
        incomingMessageService.screenIncomingCall(phone, session)
    }
    Result<Closure> handleSelfCall(Phone phone, String apiId, String digits, Staff staff) {
        incomingMessageService.handleSelfCall(phone, apiId, digits, staff)
    }

    Result<String> moveVoicemail(String callId, String recordingId, String voicemailUrl) {
        voicemailService.moveVoicemail(callId, recordingId, voicemailUrl)
    }
    ResultGroup<RecordCall> storeVoicemail(String callId, int voicemailDuration) {
        voicemailService.storeVoicemail(callId, voicemailDuration)
    }
}
