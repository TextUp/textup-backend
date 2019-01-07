package org.textup

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import java.util.concurrent.Future
import org.textup.rest.*
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*
import org.textup.validator.action.*

@GrailsTypeChecked
@Transactional
class PhoneService {

    AuthService authService
    CallService callService
    MediaService mediaService
    NotificationSettingsService notificationSettingsService
    PhoneStateService phoneStateService

    Result<Phone> merge(Phone p1, Map body, String timezone) {
        if (!body) {
            return IOCUtils.resultFactory.success(p1)
        }
        Future<?> future
        Result<Phone> res = mediaService.tryProcess(p1, body, true)
            .then { Tuple<WithMedia, Future<?>> processed ->
                future = processed.second
                phoneStateService.handleActions(p1, body)
            }
            .then { handleAvailability(p1, body, timezone) }
            .then {
                Phones.update(p1, body.awayMessage, body.voice, body.language,
                    body.useVoicemailRecordingIfPresent)
            }
        // try to initiate voicemail greeting call at very end if successful so far
        if (res.success) {
            requestVoicemailGreetingCall(p1, body)
                .logFail("PhoneService.mergeHelper: trying to start voicemail greeting call")
        }
        else { // cancel media processing if this request is unsuccessful
            if (future) { future.cancel(true) }
        }
        res
    }

    // Helpers
    // -------

    protected Result<Phone> handleAvailability(Phone p1, Map body, String timezone) {
        if (body.availability instanceof Map) {
            NotificationPolicy np1 = p1.owner.getOrCreatePolicyForStaff(authService.loggedIn.id)
            Result<?> res = notificationSettingsService.update(np1, body.availability as Map, timezone)
            if (!res.success) { return res }
        }
        IOCUtils.resultFactory.success(p1)
    }

    protected Result<?> requestVoicemailGreetingCall(Phone p1, Map body) {
        String num = body.requestVoicemailGreetingCall
        if (!num) {
            return IOCUtils.resultFactory.success()
        }
        PhoneNumber
            .createAndValidate(getNumberToCallForVoicemailGreeting(num))
            .then { PhoneNumber toNum ->
                callService.start(p1.number, [toNum], CallTwiml.infoForRecordVoicemailGreeting(),
                    p1.customAccountId)
            }
    }

    protected String getNumberToCallForVoicemailGreeting(String possibleNum) {
        possibleNum == "true" ? authService.loggedInAndActive?.personalPhoneAsString : possibleNum
    }
}
