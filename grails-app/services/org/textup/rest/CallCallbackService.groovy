package org.textup.rest

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional

@GrailsTypeChecked
@Transactional
class CallCallbackService {

    Result<Closure> directMessageCall(String token) {
        tokenService.findDirectMessage(token).then({ Token tok1 ->
            TypeMap<String, ?> data = tok1.data
            List<URL> recordings = []
            if (data.mediaId) {
                MediaInfo.get(data.long("mediaId"))
                    ?.getMediaElementsByType(MediaType.AUDIO_TYPES)
                    ?.each { MediaElement el1 ->
                        if (el1.sendVersion) { recordings << el1.sendVersion.link }
                    }
            }
            CallTwiml.directMessage(data.string("identifier"), data.string("message"),
                data.enum(VoiceLanguage, "language"), recordings)
        }, { Result<Token> failRes ->
            failRes.logFail("OutgoingMessageService.directMessageCall")
            CallTwiml.error()
        })
    }

    Result<Closure> finishBridgeCall(TypeMap params) {
        Contact c1 = Contact.get(params.long("contactId"))
        CallTwiml.finishBridge(c1)
    }

    Result<Closure> screenIncomingCall(Phone p1, IncomingSession sess1) {
        IndividualPhoneRecords.tryFindEveryByNumbers(p1, [sess1.number], false)
            .then { Map<PhoneNumber, List<Contact>> numberToContacts ->
                HashSet<String> idents = new HashSet<>()
                numberToContacts.each { PhoneNumber pNum, List<Contact> contacts ->
                    contacts.each { Contact c1 ->
                        idents.add(c1.getNameOrNumber())
                    }
                }
                CallTwiml.screenIncoming(idents)
            }
    }
}
