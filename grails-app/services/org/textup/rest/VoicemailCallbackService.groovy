package org.textup.rest

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import java.util.concurrent.*
import org.textup.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*

@GrailsTypeChecked
@Transactional
class VoicemailCallbackService {

    CallService callService
    IncomingMediaService incomingMediaService
    SocketService socketService
    ThreadService threadService

    Result<Void> processVoicemailMessage(String callId, int duration, IncomingRecordingInfo ir1) {
        ir1.isPublic = false
        incomingMediaService.process([ir1])
            .logFail("processVoicemailMessage: $callId")
            .then { List<MediaElement> elements ->
                ResultGroup<RecordCall> resGroup = new ResultGroup<>()
                RecordItems.findEveryForApiId(callId)
                    .each { RecordItem item ->
                        if (item.instanceOf(RecordCall)) {
                            resGroup << RecordCall
                                .tryUpdateVoicemail(item as RecordCall, duration, elements)
                        }
                    }
                // send updated items with receipts through socket
                socketService.sendItems(resGroup.payload)
                resGroup.toEmptyResult(false)
            }
    }

    Result<Void> finishProcessingVoicemailGreeting(Long phoneId, String callId,
        IncomingRecordingInfo ir1) {

        Phones.mustFindActiveForId(phoneId)
            .then { Phone p1 ->
                // voicemail greetings are public so that Twilio can cache the object and because
                // anyone who calls the number and gets sent to voicemail will hear this greeting
                ir1.isPublic = true
                incomingMediaService.process([ir1]).curry(p1)
            }
            .logFail("finishProcessingVoicemailGreeting: processing recording")
            .then { Phone p1, List<MediaElement> elements ->
                MediaInfo.tryCreate(p1.media).curry(p1, elements)
            }
            .then { Phone p1, List<MediaElement> elements, MediaInfo mInfo ->
                elements.each { MediaElement el1 -> mInfo.addToMediaElements(el1) }
                DomainUtils.trySave(p1)
            }
            .then { Phone p1 ->
                socketService.sendPhone(p1)
                // delay interrupting the call to give this current transaction enough time to finish
                // saving so that when this webhook is called, it will play the latest greeting instead
                // of occasionally the one before
                threadService.delay(3, TimeUnit.SECONDS) {
                    Map<String, String> afterPickup = CallTwiml.infoForPlayVoicemailGreeting()
                    callService.interrupt(callId, afterPickup, p1.customAccountId)
                        .logFail("finishProcessingVoicemailGreeting interrupt $callId")
                }
                Result.void()
            }
    }
}
