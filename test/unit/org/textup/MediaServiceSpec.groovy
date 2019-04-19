package org.textup

import grails.test.mixin.*
import grails.test.mixin.gorm.*
import grails.test.mixin.hibernate.*
import grails.test.mixin.support.*
import grails.test.runtime.*
import grails.validation.*
import java.lang.reflect.Proxy
import java.util.concurrent.*
import org.joda.time.*
import org.textup.action.*
import org.textup.media.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*
import spock.lang.*

@Domain([CustomAccountDetails, MediaInfo, MediaElement, MediaElementVersion])
@TestFor(MediaService)
@TestMixin(HibernateTestMixin)
@Unroll
class MediaServiceSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test completing upload"() {
        given:
        UploadItem uItem1 = TestUtils.buildUploadItem()
        uItem1.isPublic = true
        UploadItem uItem2 = TestUtils.buildUploadItem()
        UploadItem uItem3 = TestUtils.buildUploadItem()
        UploadItem uItem4 = TestUtils.buildUploadItem()
        MediaElement el1 = TestUtils.buildMediaElement()

        MockedMethod process = MockedMethod.create(MediaPostProcessor, "process") {
            Result.createSuccess(Tuple.create(uItem2, [uItem3, uItem4]))
        }

        when:
        Result res = service.storeUpload(uItem1, el1)

        then:
        process.latestArgs == [uItem1.type, uItem1.data]
        res.status == ResultStatus.OK
        res.payload.size() == 3
        uItem2 in res.payload
        uItem3 in res.payload
        uItem4 in res.payload
        el1.sendVersion.versionId == uItem2.key
        el1.sendVersion.isPublic
        uItem3.key in el1.alternateVersions*.versionId
        uItem4.key in el1.alternateVersions*.versionId
        el1.alternateVersions*.isPublic.every { it == true }

        cleanup:
        process?.restore()
    }

    void "test finishing progressing"() {
        given:
        MediaInfo mInfo1 = TestUtils.buildMediaInfo()
        UploadItem uItem1 = TestUtils.buildUploadItem()
        MediaElement el1 = TestUtils.buildMediaElement()

        DehydratedPartialUploads dpu1 = GroovyMock()
        PartialUploads pu1 = GroovyMock()
        service.storageService = GroovyMock(StorageService)
        MockedMethod storeUpload = MockedMethod.create(service, "storeUpload") { UploadItem arg1 ->
            Result.createSuccess([arg1])
        }

        when:
        Result res = service.tryFinishProcessing(mInfo1.id, dpu1)

        then:
        1 * dpu1.tryRehydrate() >> Result.createSuccess(pu1)
        1 * pu1.eachUpload(_ as Closure) >> { args -> args[0].call(uItem1, el1) }
        storeUpload.callCount == 1
        storeUpload.latestArgs == [uItem1, el1]
        1 * service.storageService.uploadAsync([uItem1]) >> new ResultGroup()
        res.status == ResultStatus.OK
        res.payload == mInfo1

        cleanup:
        storeUpload?.restore()
    }

    void "test starting progressing"() {
        given:
        Map body = [(TestUtils.randString()): TestUtils.randString()]
        String errMsg1 = TestUtils.randString()

        MediaInfo mInfo1 = TestUtils.buildMediaInfo()
        UploadItem uItem1 = TestUtils.buildUploadItem()

        ScheduledFuture fut1 = GroovyMock()
        service.mediaActionService = GroovyMock(MediaActionService)
        service.storageService = GroovyMock(StorageService)
        service.threadService = GroovyMock(ThreadService)
        MockedMethod trySet = MockedMethod.create(RequestUtils, "trySet")
        MockedMethod tryFinishProcessing = MockedMethod.create(service, "tryFinishProcessing") {
            Result.void()
        }

        when:
        Result res = service.tryStartProcessing(mInfo1, body, true)

        then:
        1 * service.mediaActionService.tryHandleActions(mInfo1, body) >> Result.createSuccess([uItem1])
        1 * service.storageService.uploadAsync([uItem1]) >>
            Result.createError([errMsg1], ResultStatus.BAD_REQUEST).toGroup()
        trySet.latestArgs == [RequestUtils.UPLOAD_ERRORS, [errMsg1]]
        1 * service.threadService.delay(*_) >> { args ->
            args[2].call()
            fut1
        }
        tryFinishProcessing.latestArgs[0] == mInfo1.id
        tryFinishProcessing.latestArgs[1] instanceof DehydratedPartialUploads
        uItem1 in tryFinishProcessing.latestArgs[1].tryRehydrate().payload.uploads
        mInfo1.mediaElements.size() == 1
        mInfo1.mediaElements[0] in tryFinishProcessing.latestArgs[1].tryRehydrate().payload.elements
        res.status == ResultStatus.OK
        res.payload == fut1

        cleanup:
        trySet?.restore()
        tryFinishProcessing?.restore()
    }

    void "test trying to create media info"() {
        given:
        Map body = [(TestUtils.randString()): TestUtils.randString()]
        int mBaseline = MediaInfo.count()

        Future fut1 = GroovyMock()
        service.mediaActionService = GroovyMock(MediaActionService)
        MockedMethod tryStartProcessing = MockedMethod.create(service, "tryStartProcessing") {
            Result.createSuccess(fut1)
        }

        when:
        Result res = service.tryCreate(body, true)

        then:
        1 * service.mediaActionService.hasActions(body) >> false
        tryStartProcessing.notCalled
        res.status == ResultStatus.OK
        res.payload instanceof Tuple
        res.payload.first == null
        res.payload.second instanceof Proxy
        res.payload.second instanceof Future
        MediaInfo.count() == mBaseline

        when:
        res = service.tryCreate(body, true)

        then:
        1 * service.mediaActionService.hasActions(body) >> true
        tryStartProcessing.latestArgs[0] instanceof MediaInfo
        tryStartProcessing.latestArgs[1] == body
        tryStartProcessing.latestArgs[2] == true
        res.status == ResultStatus.OK
        res.payload instanceof Tuple
        res.payload.first instanceof MediaInfo
        res.payload.second == fut1
        MediaInfo.count() == mBaseline + 1

        cleanup:
        tryStartProcessing?.restore()
    }

    void "test trying to create or update media for media owner"() {
        given:
        Map body = [(TestUtils.randString()): TestUtils.randString()]

        MediaInfo mInfo1 = TestUtils.buildMediaInfo()
        int mBaseline = MediaInfo.count()

        WithMedia withMedia = GroovyMock()
        Future fut1 = GroovyMock()
        service.mediaActionService = GroovyMock(MediaActionService)
        MockedMethod tryStartProcessing = MockedMethod.create(service, "tryStartProcessing") {
            Result.createSuccess(fut1)
        }

        when:
        Result res = service.tryCreateOrUpdate(withMedia, body, true)

        then:
        1 * service.mediaActionService.hasActions(body) >> false
        tryStartProcessing.notCalled
        res.status == ResultStatus.OK
        res.payload instanceof Proxy
        res.payload instanceof Future
        MediaInfo.count() == mBaseline

        when: "has actions but no media"
        res = service.tryCreateOrUpdate(withMedia, body, true)

        then: "no existing media so new media is created and associated with `withMedia`"
        1 * service.mediaActionService.hasActions(body) >> true
        1 * withMedia.media >> null
        1 * withMedia.setMedia(_ as MediaInfo)
        tryStartProcessing.latestArgs[0] != mInfo1
        tryStartProcessing.latestArgs[1] == body
        tryStartProcessing.latestArgs[2] == true
        res.status == ResultStatus.OK
        res.payload instanceof Proxy
        res.payload instanceof Future
        MediaInfo.count() == mBaseline + 1

        when: "has actions and has existing media"
        res = service.tryCreateOrUpdate(withMedia, body, false)

        then: "existing media object is preserved"
        1 * service.mediaActionService.hasActions(body) >> true
        1 * withMedia.media >> mInfo1
        1 * withMedia.setMedia(mInfo1)
        tryStartProcessing.latestArgs == [mInfo1, body, false]
        res.status == ResultStatus.OK
        res.payload instanceof Proxy
        res.payload instanceof Future
        MediaInfo.count() == mBaseline + 1

        cleanup:
        tryStartProcessing?.restore()
    }
}
