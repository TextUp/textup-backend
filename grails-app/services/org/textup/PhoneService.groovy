package org.textup

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import java.util.concurrent.Future
import org.textup.rest.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*
import org.textup.validator.action.*

@GrailsTypeChecked
@Transactional
class PhoneService {

    CallService callService
    MediaService mediaService
    NotificationSettingsService notificationSettingsService
    PhoneActionsService phoneActionsService

    Result<Phone> update(Phone p1, TypeMap body, String timezone) {
        Future<?> future
        Result<?> res = mediaService.tryProcess(p1, body, true)
            .then { Tuple<WithMedia, Future<?>> processed ->
                future = processed.second
                phoneActionsService.tryHandleActions(p1, body)
            }
            .then {
                tryUpdateNotificationPolicies(p1, body.typeMapNoNull("availability"), timezone)
            }
            .then { trySetFields(p1, body) }
            .then {
                tryRequestVoicemailGreetingCall(p1, body.string("requestVoicemailGreetingCall"))
            }
        res.then({
            DomainUtils.trySave(p1)
        }, { Result<?> failRes ->
            future?.cancel(true)
            failRes
        })
    }

    // Helpers
    // -------

    protected Result<Phone> trySetFields(Phone p1, TypeMap body) {
        p1.with {
            if (body.awayMessage) awayMessage = body.awayMessage
            if (body.voice) voice = body.enum(VoiceType, "voice")
            if (body.language) language = body.enum(VoiceLanguage, "language")
            if (body.useVoicemailRecordingIfPresent != null) {
                p1.useVoicemailRecordingIfPresent = body.bool("useVoicemailRecordingIfPresent")
            }
        }
        DomainUtils.trySave(p1)
    }

    protected Result<Phone> tryUpdateNotificationPolicies(Phone p1, TypeMap aInfo, String timezone) {
        if (aInfo) {
            AuthUtils.tryGetAuthId()
                .then { Long authId ->
                    NotificationPolicy np1 = p1.owner.getOrCreatePolicyForStaff(authId)
                    notificationSettingsService.update(np1, aInfo, timezone)
                }
        }
        IOCUtils.resultFactory.success(p1)
    }

    protected Result<?> tryRequestVoicemailGreetingCall(Phone p1, String numToCall) {
        if (numToCall) {
            AuthUtils.tryGetAuthUser()
                .then { Staff authUser ->
                    tryGetGreetingCallNum(numToCall, authUser.personalPhone)
                }
                .then { PhoneNumber toNum ->
                    callService.start(p1.number,
                        [toNum],
                        CallTwiml.infoForRecordVoicemailGreeting(),
                        p1.customAccountId)
                }
        }
        else { IOCUtils.resultFactory.success() }
    }

    protected Result<PhoneNumber> tryGetGreetingCallNum(String possibleNum, PhoneNumber authNum) {
        possibleNum == "true" ? authNum : PhoneNumber.createAndValidate(possibleNum)
    }
}
