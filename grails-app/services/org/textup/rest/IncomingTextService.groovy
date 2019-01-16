package org.textup.rest

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional

@GrailsTypeChecked
@Transactional
class IncomingTextService {

    AnnouncementCallbackService announcementCallbackService
    IncomingMediaService incomingMediaService
    NotificationService notificationService
    OutgoingNotificationService outgoingNotificationService
    SocketService socketService
    ThreadService threadService

    Result<Closure> processText(Phone p1, IncomingText text, IncomingSession sess1,
        TypeMap params) {

        // step 1: build texts
        buildTexts(p1, text, sess1)
            .then { List<RecordText> texts -> notificationService.build(texts).curry(texts) }
            .then { List<RecordText> texts, List<OutgoingNotification> notifs ->
                // step 2: in a new thread, handle long-running tasks. Delay required to allow texts
                // created in this thread to save. Otherwise, when we try to get texts with the
                // following ids, they will have be saved yet and we will get `null` in return
                threadService.delay(5, TimeUnit.SECONDS) {
                    finishProcessingText(text, texts*.id, notifs, params)
                        .logFail("processText: finishing processing")
                }
                // step 3: return the appropriate response while long-running tasks still processing
                buildTextResponse(p1, sess1, texts, notifs)
            }
    }

    protected Result<Tuple<List<RecordText>, List<Contact>>> buildTexts(Phone p1, IncomingText text,
        IncomingSession is1) {

        PhoneRecordUtils.tryMarkUnread(p1, is1.number)
            .then { List<IndividualPhoneRecordWrapper> wrappers ->

                // socketService.sendContacts(wrappers) // TODO
                //     .logFail("PhoneRecordUtils.tryMarkUnread: sending via socket")

                ResultGroup.collect(wrappers) { IndividualPhoneRecordWrapper w1 ->
                        w1.tryGetRecord()
                            .then { Record rec1 -> rec1.storeIncomingText(text, is1) }
                            .then { RecordText rText1 ->
                                rText1.addReceipt(rpt)
                                DomainUtils.trySave(rText1)
                            }
                    }
                    .logFail("buildTexts")
                    .toResult(true)
            }
    }

    protected Result<Closure> buildTextResponse(Phone p1, IncomingSession is1,
        List<RecordText> rTexts, List<OutgoingNotification> notifs) {

        if (rTexts) {
            List<String> responses = []
            if (notifs.isEmpty()) {
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

    protected Result<Void> finishProcessingText(IncomingText text, List<Long> textIds,
        List<OutgoingNotification> notifs, TypeMap params) {

        Integer numMedia = params.int("NumMedia", 0)
        // if needed, process media, which includes generating versions, uploading versions,
        // and deleting copies stored by Twilio
        if (numMedia > 0) {
            ResultGroup<MediaElement> outcomes = incomingMediaService
                .process(TwilioUtils.buildIncomingMedia(numMedia, text.apiId, params))
            MediaInfo mInfo = new MediaInfo()
            for (MediaElement el1 in outcomes.payload) {
                mInfo.addToMediaElements(el1)
            }
            if (mInfo.save()) {
                finishProcessingTextHelper(text, textIds, notifs, mInfo)
            }
            else { IOCUtils.resultFactory.failWithValidationErrors(mInfo.errors) }
        }
        else { finishProcessingTextHelper(text, textIds, notifs) }
    }

    protected Result<Void> finishProcessingTextHelper(IncomingText text, List<Long> textIds,
        List<OutgoingNotification> notifs, MediaInfo mInfo = null) {

        Collection<RecordText> rTexts = rebuildRecordTexts(textIds)
        int numNotified = notifs.size()
        ResultGroup<RecordText> outcomes = new ResultGroup<>()
        rTexts.each { RecordText rText ->
            rText.media = mInfo
            rText.numNotified = numNotified
            if (!rText.save()) {
                outcomes << IOCUtils.resultFactory.failWithValidationErrors(rText.errors)
            }
        }
        if (!outcomes.anyFailures) {
            // send out notifications
            outgoingNotificationService.send(notifs, false, text.message)
                .logFail("finishProcessingTextHelper: notifying staff")
            // For outgoing messages and all calls, we rely on status callbacks
            // to push record items to the frontend. However, for incoming texts
            // no status callback happens so we need to push the item here
            socketService.sendItems(rTexts)
                .logFail("finishProcessingTextHelper: sending via socket")
            IOCUtils.resultFactory.success()
        }
        else { IOCUtils.resultFactory.failWithGroup(outcomes) }
    }

    protected Collection<RecordText> rebuildRecordTexts(Collection<Long> textIds) {
        Collection<RecordText> rTexts = []
        boolean didFindAll = true
        RecordText
            .getAll(textIds as Iterable<Serializable>)
            .each { RecordText rText ->
                if (rText) {
                    rTexts << rText
                }
                else { didFindAll = false }
            }
        if (!didFindAll) {
            log.error("rebuildRecordTexts: not all texts found from ids `${textIds}`")
        }
        rTexts
    }
}
