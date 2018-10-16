package org.textup

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import grails.test.runtime.DirtiesRuntime
import grails.test.runtime.FreshRuntime
import grails.validation.ValidationErrors
import java.util.concurrent.*
import org.apache.http.HttpStatus as ApacheHttpStatus
import org.joda.time.DateTime
import org.textup.media.*
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*
import spock.lang.*

@Unroll
@TestFor(MediaService)
@Domain([MediaInfo, MediaElement, MediaElementVersion])
@TestMixin(HibernateTestMixin)
class MediaServiceSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
        audioUtils(AudioUtils,
            TestHelpers.config.textup.media.audio.executableDirectory,
            TestHelpers.config.textup.media.audio.executableName,
            TestHelpers.config.textup.tempDirectory)
    }

    def setup() {
        Helpers.metaClass."static".getMessageSource = { -> TestHelpers.mockMessageSource() }
        service.resultFactory = TestHelpers.getResultFactory(grailsApplication)
    }

    // Handling actions
    // ----------------

    void "test for having media actions"() {
        expect:
        false == service.hasMediaActions(null)
        false == service.hasMediaActions([:])
        true == service.hasMediaActions([doMediaActions:"blah"])
    }

    void "test handling media actions errors"() {
        given:
        String rawData = "I am some data*~~~~|||"
        String encodedData = TestHelpers.encodeBase64String(rawData.bytes)
        String checksum = TestHelpers.getChecksum(encodedData)

        when: "adding - invalid mime type"
        ResultGroup<UploadItem> resGroup = service.handleActions(null, [doMediaActions:[
            [
                action: Constants.MEDIA_ACTION_ADD,
                mimeType: "not a valid mime type",
                data: encodedData,
                checksum: checksum
            ]
        ]])

        then:
        resGroup.failureStatus == ResultStatus.UNPROCESSABLE_ENTITY
        resGroup.failures[0].errorMessages.contains("actionContainer.invalidActions")

        when: "adding - improperly encoded data"
        resGroup = service.handleActions(null, [doMediaActions:[
            [
                action: Constants.MEDIA_ACTION_ADD,
                mimeType: MediaType.IMAGE_JPEG.mimeType,
                data: rawData,
                checksum: checksum
            ]
        ]])

        then:
        resGroup.failureStatus == ResultStatus.UNPROCESSABLE_ENTITY
        resGroup.failures[0].errorMessages.contains("actionContainer.invalidActions")

        when: "adding - missing checksum"
        resGroup = service.handleActions(null, [doMediaActions:[
            [
                action: Constants.MEDIA_ACTION_ADD,
                mimeType: MediaType.IMAGE_JPEG.mimeType,
                data: encodedData
            ]
        ]])

        then:
        resGroup.failureStatus == ResultStatus.UNPROCESSABLE_ENTITY
        resGroup.failures[0].errorMessages.contains("actionContainer.invalidActions")

        when: "removing - missing uid"
        resGroup = service.handleActions(null, [doMediaActions:[
            [action:Constants.MEDIA_ACTION_REMOVE]
        ]])

        then:
        resGroup.failureStatus == ResultStatus.UNPROCESSABLE_ENTITY
        resGroup.failures[0].errorMessages.contains("actionContainer.invalidActions")

        when: "invalid action type"
        resGroup = service.handleActions(null, [doMediaActions:[
            [
                action: "invalid"
            ]
        ]])

        then:
        resGroup.failureStatus == ResultStatus.UNPROCESSABLE_ENTITY
        resGroup.failures[0].errorMessages.contains("actionContainer.invalidActions")

        expect: "invalid for when the contains around the action items are invalid"
        ResultStatus.UNPROCESSABLE_ENTITY == service.handleActions(null, null).failureStatus
        ResultStatus.UNPROCESSABLE_ENTITY == service.handleActions(null, [:]).failureStatus
        ResultStatus.UNPROCESSABLE_ENTITY ==
            service.handleActions(null, [doMediaActions:"not a list containing maps"]).failureStatus
        ResultStatus.UNPROCESSABLE_ENTITY ==
            service.handleActions(null, [doMediaActions:["not a map"]]).failureStatus
    }

    @DirtiesRuntime
    void "test adding and removing via media action"() {
        given:
        MediaPostProcessor.metaClass."static".buildInitialData = { MediaType type, byte[] data ->
            new Result()
        }
        MediaInfo mInfo = Mock(MediaInfo)

        when: "add via media action"
        Map addAction = TestHelpers.buildAddMediaAction(MediaType.IMAGE_PNG)
        ResultGroup<UploadItem> resGroup = service.handleActions(mInfo, [doMediaActions:[addAction]])

        then: "add function is called"
        0 * mInfo.removeMediaElement(*_)
        resGroup.isEmpty == false
        resGroup.successes.size() == 1

        when: "remove via media action"
        resGroup = service.handleActions(mInfo, [doMediaActions:[
            [
                action: Constants.MEDIA_ACTION_REMOVE,
                uid: "a valid uid"
            ]
        ]])

        then:
        1 * mInfo.removeMediaElement(*_)
        resGroup.isEmpty == true
    }

    // Finishing processing media
    // --------------------------

    void "test processing element"() {
        given:
        UploadItem inputItem = TestHelpers.buildUploadItem()
        UploadItem sendItem = TestHelpers.buildUploadItem()
        UploadItem altItem = TestHelpers.buildUploadItem()
        MediaPostProcessor.metaClass."static".process = { MediaType type, byte[] data ->
            new Result(payload: Tuple.create(sendItem, [altItem]))
        }
        MediaElement e1 = new MediaElement()
        e1.save(flush: true, failOnError: true)
        int eBaseline = MediaElement.count()
        int vBaseline = MediaElementVersion.count()

        when:
        Result<Tuple<List<UploadItem>, MediaElement>> res = service.processElement(inputItem, e1)
        MediaElement.withSession { it.flush() }

        then:
        MediaElement.count() == eBaseline
        MediaElementVersion.count() == vBaseline + 2
        res.status == ResultStatus.OK
        res.payload.first.size() == 2
        res.payload.first.every { it == sendItem || it == altItem }
        res.payload.second == e1
        res.payload.second.sendVersion.versionId == sendItem.key
        res.payload.second.alternateVersions.size() == 1
        res.payload.second.alternateVersions[0].versionId == altItem.key
    }

    void "test finishing processing overall"() {
        given:
        service.storageService = Mock(StorageService)
        UploadItem inputItem = TestHelpers.buildUploadItem()
        UploadItem sendItem = TestHelpers.buildUploadItem()
        UploadItem altItem = TestHelpers.buildUploadItem()
        MediaPostProcessor.metaClass."static".process = { MediaType type, byte[] data ->
            new Result(payload: Tuple.create(sendItem, [altItem]))
        }
        MediaInfo mInfo = new MediaInfo()
        MediaElement e1 = new MediaElement()
        mInfo.addToMediaElements(e1)
        mInfo.save(flush: true, failOnError: true)
        int mBaseline = MediaInfo.count()
        int eBaseline = MediaElement.count()
        int vBaseline = MediaElementVersion.count()

        when:
        List<Tuple<UploadItem, MediaElement>> toProcess = [Tuple.create(inputItem, e1)]
        Result<MediaInfo> res = service.tryFinishProcessing(mInfo, toProcess)
        MediaInfo.withSession { it.flush() }

        then: "re-adding the element to the parent does not result in a duplicate association"
        1 * service.storageService.uploadAsync(*_) >> { args ->
            assert args[0].size() == 2
            assert args[0].every { it == sendItem || it == altItem }
            new ResultGroup()
        }
        res.status == ResultStatus.OK
        res.payload instanceof MediaInfo
        res.payload.id == mInfo.id
        res.payload.mediaElements.size() == 1
        res.payload.mediaElements[0].sendVersion.versionId == sendItem.key
        res.payload.mediaElements[0].alternateVersions.size() == 1
        res.payload.mediaElements[0].alternateVersions[0].versionId == altItem.key
        MediaInfo.count() == mBaseline
        MediaElement.count() == eBaseline
        MediaElementVersion.count() == vBaseline + 2
    }

    // Processing media overall
    // ------------------------

    void "test processing with no media actions"() {
        given:
        MediaInfo mInfo = new MediaInfo()
        mInfo.save(flush: true, failOnError: true)
        WithMedia withMedia = Mock(WithMedia)

        int mBaseline = MediaInfo.count()
        int eBaseline = MediaElement.count()
        int vBaseline = MediaElementVersion.count()

        when: "with media info"
        Result<Tuple<MediaInfo, Future<Result<MediaInfo>>>> res1 = service.tryProcess(mInfo, null)
        MediaInfo.withSession { it.flush() }

        then:
        res1.status == ResultStatus.OK
        res1.payload.first == mInfo
        res1.payload.second instanceof Future // this is a no-op future
        MediaInfo.count() == mBaseline
        MediaElement.count() == eBaseline
        MediaElementVersion.count() == vBaseline

        when: "with media owner"
        Result<Tuple<WithMedia, Future<Result<MediaInfo>>>> res2 = service.tryProcess(withMedia, null)
        MediaInfo.withSession { it.flush() }

        then:
        (1.._) * withMedia.getMedia()
        0 * withMedia.setMedia(*_)
        res2.status == ResultStatus.OK
        res2.payload.first == withMedia
        res2.payload.second instanceof Future // this is a no-op future
        MediaInfo.count() == mBaseline
        MediaElement.count() == eBaseline
        MediaElementVersion.count() == vBaseline
    }

    @FreshRuntime
    void "test processing an image given the media object"() {
        given:
        service.threadService = Mock(ThreadService)
        service.storageService = Mock(StorageService)
        MockedMethod trySetOnRequest = TestHelpers.mock(Helpers, "trySetOnRequest") { new Result() }
        MediaInfo mInfo = new MediaInfo()
        mInfo.save(flush: true, failOnError: true)
        int mBaseline = MediaInfo.count()
        int eBaseline = MediaElement.count()
        int vBaseline = MediaElementVersion.count()
        Closure finishProcessing

        when: "valid add and remove actions"
        Map addAction = TestHelpers.buildAddMediaAction(MediaType.IMAGE_JPEG)
        Map removeAction = [action: Constants.MEDIA_ACTION_REMOVE, uid: "a valid uid"]
        Result<Tuple<MediaInfo, Future<Result<MediaInfo>>>> res = service.tryProcess(mInfo,
            [doMediaActions: [addAction, removeAction]])
        MediaInfo.withSession { it.flush() }

        then: "after"
        1 * service.threadService.submit(*_) >> { Closure action ->
            finishProcessing = action
            Helpers.noOpFuture()
        }
        1 * service.storageService.uploadAsync(*_) >> new ResultGroup() // once synchronously
        trySetOnRequest.callCount == 1
        res.status == ResultStatus.OK
        res.payload.first.id == mInfo.id
        res.payload.first.mediaElements.size() == 1
        res.payload.second instanceof Future // this is a no-op future -- see threadService stub
        MediaInfo.count() == mBaseline
        MediaElement.count() == eBaseline + 1
        MediaElementVersion.count() == vBaseline + 1 // initial version

        when: "wait for asynchronous media processing to finish"
        Result<MediaInfo> finishedRes = finishProcessing.call()
        MediaInfo.withSession { it.flush() }

        then:
        1 * service.storageService.uploadAsync(*_) >> new ResultGroup() // again after finishing
        trySetOnRequest.callCount == 1
        finishedRes.status == ResultStatus.OK
        finishedRes.payload.id == mInfo.id
        finishedRes.payload.mediaElements.size() == 1
        finishedRes.payload.mediaElements[0].sendVersion != null
        finishedRes.payload.mediaElements[0].alternateVersions.size() == 4 // FOR IMAGES: initial version + 3 alternates for each screen size
        MediaInfo.count() == mBaseline
        MediaElement.count() == eBaseline + 1
        MediaElementVersion.count() == vBaseline + 5 // initial version + send version + 3 alt versions
    }

    @FreshRuntime
    void "test processing audio clip given the media owner withOUT existing media"() {
        given:
        service.threadService = Mock(ThreadService)
        service.storageService = Mock(StorageService)
        MockedMethod trySetOnRequest = TestHelpers.mock(Helpers, "trySetOnRequest") { new Result() }
        WithMedia withMedia = Mock(WithMedia)
        int mBaseline = MediaInfo.count()
        int eBaseline = MediaElement.count()
        int vBaseline = MediaElementVersion.count()
        int numInTemp = TestHelpers.numInTempDirectory
        Closure finishProcessing

        when: "media owner does NOT have media"
        Map addAction = TestHelpers.buildAddMediaAction(MediaType.AUDIO_WEBM_VORBIS)
        Result<Tuple<WithMedia, Future<Result<MediaInfo>>>> res = service.tryProcess(withMedia,
            [doMediaActions: [addAction]])
        MediaInfo.withSession { it.flush() }

        then: "one is created"
        (1.._) * withMedia.getMedia() >> null
        1 * withMedia.setMedia(*_) >> { MediaInfo mInfo ->
            assert mInfo.mediaElements.size() == 1 // initial version
        }
        1 * service.threadService.submit(*_) >> { Closure action ->
            finishProcessing = action
            Helpers.noOpFuture()
        }
        1 * service.storageService.uploadAsync(*_) >> new ResultGroup() // once synchronously
        trySetOnRequest.callCount == 1
        res.status == ResultStatus.OK
        res.payload.first instanceof WithMedia
        res.payload.second instanceof Future
        MediaInfo.count() == mBaseline + 1
        MediaElement.count() == eBaseline + 1
        MediaElementVersion.count() == vBaseline + 1 // initial version
        TestHelpers.numInTempDirectory == numInTemp

        when: "wait for asynchronous media processing to finish"
        Result<MediaInfo> finishedRes = finishProcessing.call()
        MediaInfo.withSession { it.flush() }

        then:
        1 * service.storageService.uploadAsync(*_) >> new ResultGroup() // again after finishing
        trySetOnRequest.callCount == 1
        finishedRes.status == ResultStatus.OK
        finishedRes.payload instanceof MediaInfo
        finishedRes.payload.mediaElements.size() == 1
        finishedRes.payload.mediaElements[0].sendVersion != null
        finishedRes.payload.mediaElements[0].alternateVersions.size() == 2 // FOR AUDIO: 1 alternate + initial
        MediaInfo.count() == mBaseline + 1
        MediaElement.count() == eBaseline + 1
        MediaElementVersion.count() == vBaseline + 3 // FOR AUDIO: initial + send + 1 alternate
        TestHelpers.numInTempDirectory == numInTemp
    }

    @FreshRuntime
    void "test processing media for media owner WITH existing media"() {
        given:
        service.threadService = Mock(ThreadService)
        service.storageService = Mock(StorageService)
        MockedMethod trySetOnRequest = TestHelpers.mock(Helpers, "trySetOnRequest") { new Result() }
        MediaInfo mInfo = new MediaInfo()
        mInfo.save(flush: true, failOnError: true)
        WithMedia withMedia = Mock(WithMedia)

        int mBaseline = MediaInfo.count()
        int eBaseline = MediaElement.count()
        int vBaseline = MediaElementVersion.count()
        int numInTemp = TestHelpers.numInTempDirectory

        when:
        Map addAction = TestHelpers.buildAddMediaAction(MediaType.AUDIO_WEBM_VORBIS)
        Result<Tuple<WithMedia, Future<Result<MediaInfo>>>> res = service.tryProcess(withMedia,
            [doMediaActions: [addAction]])
        MediaInfo.withSession { it.flush() }

        then: "no new media info object is created"
        (1.._) * withMedia.getMedia() >> mInfo
        1 * withMedia.setMedia(*_) >> { MediaInfo thisMediaInfo ->
            assert thisMediaInfo.mediaElements.size() == 1 // initial version
            assert thisMediaInfo.id == mInfo.id
        }
        1 * service.threadService.submit(*_) >> Helpers.noOpFuture()
        1 * service.storageService.uploadAsync(*_) >> new ResultGroup() // once synchronously
        trySetOnRequest.callCount == 1
        res.status == ResultStatus.OK
        res.payload.first instanceof WithMedia
        res.payload.second instanceof Future
        MediaInfo.count() == mBaseline
        MediaElement.count() == eBaseline + 1
        MediaElementVersion.count() == vBaseline + 1 // initial version
        TestHelpers.numInTempDirectory == numInTemp
    }
}
