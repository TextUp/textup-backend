package org.textup.rest

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import java.util.concurrent.TimeUnit
import org.textup.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*

@GrailsTypeChecked
@Transactional
class IncomingTextService {

    AnnouncementCallbackService announcementCallbackService
    IncomingMediaService incomingMediaService
    NotificationService notificationService
    SocketService socketService
    ThreadService threadService

    Result<Closure> process(Phone p1, IncomingSession is1, String apiId, String message,
        Integer numSegments, List<IncomingMediaInfo> medias = null) {
        // step 1: build texts
        buildTexts(p1, is1, apiId, message, numSegments)
            .then { List<RecordText> texts ->
                NotificationUtils.tryBuildNotificationGroup(texts).curry(texts)
            }
            .then { List<RecordText> texts, NotificationGroup notifGroup ->
                // step 2: in a new thread, handle long-running tasks. Delay required to allow texts
                // created in this thread to save. Otherwise, when we try to get texts with the
                // following ids, they will have be saved yet and we will get `null` in return
                threadService.delay(5, TimeUnit.SECONDS) {
                    DehydratedNotificationGroup.tryCreate(notifGroup)
                        .then { DehydratedNotificationGroup dng1 -> processMedia(medias, dng1) }
                        .logFail("process: finishing processing")
                }
                // step 3: return the appropriate response while long-running tasks still processing
                buildTextResponse(p1, is1, texts, notifGroup)
            }
    }

    protected Result<List<RecordText>> buildTexts(Phone p1, IncomingSession is1, String apiId,
        String message, Integer numSegments) {

        PhoneRecordUtils.tryMarkUnread(p1, is1.number)
            .then { List<IndividualPhoneRecordWrapper> wrappers ->
                socketService.sendIndividualWrappers(wrappers)
                ResultGroup
                    .collect(wrappers) { IndividualPhoneRecordWrapper w1 ->
                        w1.tryGetRecord().then { Record rec1 ->
                            rec1.storeIncoming(RecordItemType.TEXT, Author.create(is1), is1.number,
                                apiId, message, numSegments)
                        }
                    }
                    .logFail("buildTexts")
                    .toResult(true)
            }
    }

    protected Result<Closure> buildTextResponse(Phone p1, IncomingSession is1,
        List<RecordText> rTexts, NotificationGroup notifGroup) {

        if (rTexts) {
            List<String> responses = []
            if (!notifGroup.canNotifyAny(NotificationFrequency.IMMEDIATELY)) {
                rTexts.each { RecordText rText -> rText.hasAwayMessage = true }
                responses << p1.buildAwayMessage()
            }
            // remind about instructions if phone has announcements enabled
            announcementCallbackService
                .tryBuildTextInstructions(p1, is1)
                .then { List<String> instructions -> TextTwiml.build(responses + instructions) }
        }
        else { TextTwiml.blocked() }
    }

    protected Result<Void> processMedia(List<IncomingMediaInfo> medias,
        Rehydratable<NotificationGroup> dng1) {

        dng1.tryRehydrate()
            .then { NotificationGroup notifGroup ->
                if (medias) {
                    incomingMediaService.process(medias)
                        .then { List<MediaElement> els -> MediaInfo.tryCreate().curry(els) }
                        .then { List<MediaElement> els, MediaInfo mInfo ->
                            mInfo.tryAddAllElements(els)
                        }
                        .then { MediaInfo mInfo -> finishProcessing(notifGroup, mInfo) }
                }
                else { finishProcessing(notifGroup) }
            }
    }

    protected Result<Void> finishProcessing(NotificationGroup notifGroup, MediaInfo mInfo = null) {
        ResultGroup<RecordItem> resGroup = new ResultGroup<>()
        notifGroup.eachItem { RecordItem rItem1 ->
            rItem1.media = mInfo
            rItem1.numNotified = notifGroup
                .getNumNotifiedForItem(NotificationFrequency.IMMEDIATELY, rItem1)
            resGroup << DomainUtils.trySave(rItem1)
        }
        resGroup.toResult(false)
            .then { List<RecordItem> rItems ->
                // send out notifications
                notificationService.send(NotificationFrequency.IMMEDIATELY, notifGroup)
                    .logFail("finishProcessing: notifying staff")
                // For outgoing messages and all calls, we rely on status callbacks
                // to push record items to the frontend. However, for incoming texts
                // no status callback happens so we need to push the item here
                socketService.sendItems(rItems)
                Result.void()
            }
    }
}
