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
        ReceiptStatus stat = ReceiptStatus.translate(params.string(TwilioUtils.STATUS_DIALED_CALL))
        if (stat == ReceiptStatus.SUCCESS) { // call already connected so no voicemail
            TwilioUtils.noResponseTwiml()
        }
        else { CallTwiml.recordVoicemailMessage(p1, is1.number) }
    }

    Result<Closure> directMessageCall(String token) {
        tokenService.buildDirectMessageCall(token)
            .ifFail("directMessageCall") { CallTwiml.error() }
    }

    Result<Closure> screenIncomingCall(Phone p1, IncomingSession is1) {
        IndividualPhoneRecords.tryFindEveryByNumbers(p1, [is1.number], false)
            .then { Map<PhoneNumber, List<IndividualPhoneRecord>> numberToPhoneRecords ->
                Collection<String> names = CollectionUtils
                    .mergeUnique(*numberToPhoneRecords.values())*.secureName
                CallTwiml.screenIncoming(names)
            }
    }
}
