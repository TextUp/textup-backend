package org.textup.rest

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import org.textup.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*

@GrailsTypeChecked
@Transactional
class CallCallbackService {

    TokenService tokenService

    Result<Closure> checkIfVoicemail(Phone p1, IncomingSession is1, TypeMap params) {
        // Changes to the Dial verb in Nov 2021 changed the `DialCallStatus` from
        // `no-answer` to `completed` if the child call connects but is not bridged, New `DialBridged`
        // parameter added to distinguish the case when the child call is connected but does not bridge
        // see https://support.twilio.com/hc/en-us/articles/4406841868699-TwiML-Dial-Verb-Enhancements
        Boolean isBridged = params.boolean(TwilioUtils.DIAL_BRIDGED)
        if (isBridged) { // call already connected so no voicemail
            TwilioUtils.noResponseTwiml()
        }
        else { CallTwiml.recordVoicemailMessage(p1, is1.number) }
    }

    Result<Closure> directMessageCall(String token) {
        tokenService.buildDirectMessageCall(token)
            .ifFail("directMessageCall") { CallTwiml.error() }
    }

    Result<Closure> screenIncomingCall(Phone p1, IncomingSession is1) {
        IndividualPhoneRecords.tryFindOrCreateNumToObjByPhoneAndNumbers(p1, is1 ? [is1.number] : null, false, false)
            .then { Map<PhoneNumber, Collection<IndividualPhoneRecord>> numberToPhoneRecords ->
                Collection<String> names = CollectionUtils
                    .mergeUnique(numberToPhoneRecords.values())*.secureName
                CallTwiml.screenIncoming(names)
            }
    }
}
