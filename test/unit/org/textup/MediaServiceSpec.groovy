package org.textup

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import grails.test.runtime.DirtiesRuntime
import grails.test.runtime.FreshRuntime
import grails.validation.ValidationErrors
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger
import org.apache.commons.codec.binary.Base64
import org.apache.commons.codec.digest.DigestUtils
import org.apache.http.HttpStatus as ApacheHttpStatus
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.joda.time.DateTime
import org.textup.rest.TwimlBuilder
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
    }

    def setup() {
        service.resultFactory = TestHelpers.getResultFactory(grailsApplication)
    }

    // Media actions
    // -------------

    void "test for having media actions"() {
        expect:
        false == service.hasMediaActions(null)
        false == service.hasMediaActions([:])
        true == service.hasMediaActions([doMediaActions:"blah"])
    }

    void "test creating send version for #mimeType"() {
        given:
        byte[] data = TestHelpers.getSampleDataForMimeType(mimeType)

        when: "invalid content type"
        Result<UploadItem> res = service.createSendVersion("invalid content type", data)

        then:
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages.contains("invalidType")

        when: "pass in content type and data"
        res = service.createSendVersion(mimeType, data)

        then: "create send version with appropriate width and file size"
        res.status == ResultStatus.OK
        res.payload instanceof UploadItem
        res.payload.mimeType == mimeType
        res.payload.mediaVersion == MediaVersion.SEND

        where:
        mimeType                 | _
        Constants.MIME_TYPE_PNG  | _
        Constants.MIME_TYPE_JPEG | _
        Constants.MIME_TYPE_GIF  | _
    }

    void "test creating display versions for #mimeType"() {
        given:
        byte[] data = TestHelpers.getSampleDataForMimeType(mimeType)

        when: "pass in image that is larger than the `large` max size"
        Result<List<UploadItem>> res = service.createDisplayVersions(mimeType, data)

        then:
        res.status == ResultStatus.OK
        res.payload.size() == 3
        [MediaVersion.LARGE, MediaVersion.MEDIUM, MediaVersion.SMALL].every { mVers ->
            res.payload.find { it.mediaVersion == mVers }
        }
        res.payload.every { it.mimeType == mimeType }

        where:
        mimeType                 | _
        Constants.MIME_TYPE_PNG  | _
        Constants.MIME_TYPE_JPEG | _
        Constants.MIME_TYPE_GIF  | _
    }

    void "test creating versions overall for #mimeType"() {
        given:
        byte[] data = TestHelpers.getSampleDataForMimeType(mimeType)

        when: "pass in data"
        Result<List<UploadItem>> res = service.createUploads(mimeType, data)

        then: "get back both send and display versions"
        res.status == ResultStatus.OK
        res.payload.size() == 4
        MediaVersion.values().every { mVers -> res.payload.find { it.mediaVersion == mVers } }
        res.payload.every { it.mimeType == mimeType }

        where:
        mimeType                 | _
        Constants.MIME_TYPE_PNG  | _
        Constants.MIME_TYPE_JPEG | _
        Constants.MIME_TYPE_GIF  | _
    }

    void "test adding media overall #mimeType"() {
        given:
        MediaInfo mInfo = new MediaInfo()
        mInfo.save(flush: true, failOnError: true)
        List<UploadItem> uItems = []
        byte[] data = TestHelpers.getSampleDataForMimeType(mimeType)
        int iBaseline = MediaInfo.count()
        int eBaseline = MediaElement.count()
        int vBaseline = MediaElementVersion.count()

        when: "pass in data"
        Result<MediaInfo> res = service.doAddMedia(mInfo, uItems.&addAll, mimeType, data)
        MediaInfo.withSession { it.flush() }

        then: "media info has a new media element added to it"
        uItems.size() == 4
        res.status == ResultStatus.OK
        res.payload == mInfo
        MediaInfo.count() == iBaseline
        MediaElement.count() == eBaseline + 1
        MediaElementVersion.count() == vBaseline + 4

        where:
        mimeType                 | _
        Constants.MIME_TYPE_PNG  | _
        Constants.MIME_TYPE_JPEG | _
        Constants.MIME_TYPE_GIF  | _
    }

    void "test handling media actions errors"() {
        given:
        String rawData = "I am some data*~~~~|||"
        String encodedData = Base64.encodeBase64String(rawData.getBytes(StandardCharsets.UTF_8))
        String checksum = DigestUtils.md5Hex(encodedData)

        when: "adding - invalid mime type"
        Result<MediaInfo> res = service.handleActions(null, null, [doMediaActions:[
            [
                action: Constants.MEDIA_ACTION_ADD,
                mimeType: "not a valid mime type",
                data: encodedData,
                checksum: checksum
            ]
        ]])

        then:
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages.contains("actionContainer.invalidActions")

        when: "adding - improperly encoded data"
        res = service.handleActions(null, null, [doMediaActions:[
            [
                action: Constants.MEDIA_ACTION_ADD,
                mimeType: Constants.MIME_TYPE_JPEG,
                data: rawData,
                checksum: checksum
            ]
        ]])

        then:
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages.contains("actionContainer.invalidActions")

        when: "adding - missing checksum"
        res = service.handleActions(null, null, [doMediaActions:[
            [
                action: Constants.MEDIA_ACTION_ADD,
                mimeType: Constants.MIME_TYPE_JPEG,
                data: encodedData
            ]
        ]])

        then:
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages.contains("actionContainer.invalidActions")

        when: "removing - missing uid"
        res = service.handleActions(null, null, [doMediaActions:[
            [action:Constants.MEDIA_ACTION_REMOVE]
        ]])

        then:
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages.contains("actionContainer.invalidActions")

        when: "invalid action type"
        res = service.handleActions(null, null, [doMediaActions:[
            [
                action: "invalid"
            ]
        ]])

        then:
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages.contains("actionContainer.invalidActions")

        expect: "invalid for when the contains around the action items are invalid"
        ResultStatus.UNPROCESSABLE_ENTITY == service.handleActions(null, null, null).status
        ResultStatus.UNPROCESSABLE_ENTITY == service.handleActions(null, null, [:]).status
        ResultStatus.UNPROCESSABLE_ENTITY ==
            service.handleActions(null, null, [doMediaActions:"not a list containing maps"]).status
        ResultStatus.UNPROCESSABLE_ENTITY ==
            service.handleActions(null, null, [doMediaActions:["not a map"]]).status
    }

    @DirtiesRuntime
    void "test adding and removing via media action"() {
        given:
        List<UploadItem> uItems = []
        Boolean hasAdded
        service.metaClass.doAddMedia = { MediaInfo mInfo, Closure<Void> collectUploads,
            String mimeType, byte[] data ->
            hasAdded = true; new Result();
        }
        MediaInfo mInfo = Mock(MediaInfo)

        when: "add via media action"
        String rawData = "I am some data*~~~~|||"
        String encodedData = Base64.encodeBase64String(rawData.getBytes(StandardCharsets.UTF_8))
        String checksum = DigestUtils.md5Hex(encodedData)
        Result<MediaInfo> res = service.handleActions(mInfo, uItems.&addAll, [doMediaActions:[
            [
                action: Constants.MEDIA_ACTION_ADD,
                mimeType: Constants.MIME_TYPE_JPEG,
                data: encodedData,
                checksum: checksum
            ]
        ]])

        then: "add function is called"
        0 * mInfo.removeMediaElement(*_)
        1 * mInfo.save() >> mInfo
        true == hasAdded
        res.status == ResultStatus.OK
        res.payload instanceof MediaInfo

        when: "remove via media action"
        res = service.handleActions(mInfo, uItems.&addAll, [doMediaActions:[
            [
                action: Constants.MEDIA_ACTION_REMOVE,
                uid: "a valid uid"
            ]
        ]])

        then:
        1 * mInfo.removeMediaElement(*_)
        1 * mInfo.save() >> mInfo
        res.status == ResultStatus.OK
        res.payload instanceof MediaInfo
    }

    // Receiving media
    // ---------------

    void "test extracting media id from url"() {
        expect:
        service.extractMediaIdFromUrl("") == ""
        service.extractMediaIdFromUrl(null) == ""
        service.extractMediaIdFromUrl("hellothere/yes") == "yes"
        service.extractMediaIdFromUrl("hello") == "hello"
        service.extractMediaIdFromUrl("/") == ""
        service.extractMediaIdFromUrl("    /") == ""
        service.extractMediaIdFromUrl("  e  /  ") == "  "
        service.extractMediaIdFromUrl(" / e  /  ") == "  "
    }

    @DirtiesRuntime
    void "test downloading + building versions for incoming media"() {
        given:
        int numTimesCalled = 0
        service.metaClass.doAddMedia = { MediaInfo mInfo, Closure<Void> collectUploads,
            String mimeType, byte[] data ->
            numTimesCalled++; new Result();
        }
        int okCode = ApacheHttpStatus.SC_OK
        int failCode = ApacheHttpStatus.SC_REQUEST_TIMEOUT
        String root = Constants.TEST_STATUS_ENDPOINT
        Map<String, String> okInfo = ["${root}/${okCode}": Constants.MIME_TYPE_JPEG]
        Map<String, String> failInfo = ["${root}/${failCode}": Constants.MIME_TYPE_JPEG]

        when: "response has a error status"
        Result<MediaInfo> res = service.buildFromIncomingMedia(failInfo, { u1 -> }, { id -> })

        then:
        0 == numTimesCalled
        res.status == ResultStatus.convert(failCode)
        res.errorMessages.contains("mediaService.buildFromIncomingMedia.couldNotRetrieveMedia")

        when: "response has a non-error status"
        res = service.buildFromIncomingMedia(okInfo, { u1 -> }, { id -> })

        then: "add function is called + media ids are extracted and collected"
        1 == numTimesCalled
        res.status == ResultStatus.convert(okCode)
    }

    @DirtiesRuntime
    void "test deleting media"() {
        given:
        AtomicInteger timesCalled = new AtomicInteger()
        service.metaClass.deleteMediaHelper = { String id1, String id2 ->
            timesCalled.getAndIncrement(); new Result(payload: true);
        }
        Collection<String> mediaIds = []
        50.times { mediaIds << UUID.randomUUID().toString() }

        when: "given media ids to delete"
        Result<Void> res = service.deleteMedia("messageId", mediaIds)

        then: "all provided media is are deleted"
        res.status == ResultStatus.NO_CONTENT
        timesCalled.get() == mediaIds.size()
    }

    // Sending media
    // -------------

    void "test sending media for text"() {
        given:
        service.textService = Mock(TextService)
        MediaInfo mInfo = new MediaInfo()
        (Constants.MAX_NUM_MEDIA_PER_MESSAGE * 2).times {
            mInfo.addToMediaElements(TestHelpers.buildMediaElement())
        }

        when: "no media"
        service.sendWithMediaForText(null, null, null, null)

        then: "send without media"
        1 * service.textService.send(*_) >> new Result()

        when: "with media"
        service.sendWithMediaForText(null, null, null, mInfo)

        then: "send with media in batches"
        (1.._) * service.textService.send(*_) >> new Result()
    }

    void "test sending media for call"() {
        given:
        service.callService = Mock(CallService)
        service.textService = Mock(TextService)
        Token callToken = new Token(token: "valid token value")
        MediaInfo mInfo = new MediaInfo()
        (Constants.MAX_NUM_MEDIA_PER_MESSAGE * 2).times {
            mInfo.addToMediaElements(TestHelpers.buildMediaElement())
        }

        when: "no media"
        service.sendWithMediaForCall(null, null, callToken, null)

        then: "only call"
        1 * service.callService.start(*_) >> new Result()
        0 * service.textService.send(*_)

        when: "with media"
        service.sendWithMediaForCall(null, null, callToken, mInfo)

        then: "send media via text message and also call"
        1 * service.callService.start(*_) >> new Result()
        (1.._) * service.textService.send(*_) >> new Result()
    }

    void "test sending with media overall"() {
        given:
        service.callService = Mock(CallService)
        service.textService = Mock(TextService)
        Token callToken = new Token(token: "valid token value")

        when: "without call token"
        Result<List<TempRecordReceipt>> res = service.sendWithMedia(null, null, null, null, null)

        then: "send as text"
        0 * service.callService.start(*_)
        1 * service.textService.send(*_) >> new Result()
        res.status == ResultStatus.OK

        when: "with call token"
        res = service.sendWithMedia(null, null, null, null, callToken)

        then: "send as call"
        1 * service.callService.start(*_) >> new Result()
        0 * service.textService.send(*_)
        res.status == ResultStatus.OK

        when: "has some errors"
        res = service.sendWithMedia(null, null, null, null, callToken)

        then: "return error"
        1 * service.callService.start(*_) >> new Result(status: ResultStatus.BAD_REQUEST)
        0 * service.textService.send(*_)
        res.status == ResultStatus.BAD_REQUEST
    }
}
