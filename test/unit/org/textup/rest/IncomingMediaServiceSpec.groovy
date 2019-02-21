package org.textup.rest

import grails.test.mixin.*
import grails.test.mixin.gorm.*
import grails.test.mixin.hibernate.*
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.springframework.validation.Errors
import org.textup.*
import org.textup.media.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*
import spock.lang.*

@TestFor(IncomingMediaService)
@Domain([CustomAccountDetails, MediaInfo, MediaElement, MediaElementVersion])
@TestMixin(HibernateTestMixin)
class IncomingMediaServiceSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test finish processing uploads"() {
        given:
        UploadItem uItem1 = TestUtils.buildUploadItem()

        IsIncomingMedia im1 = GroovyMock()
        service.threadService = GroovyMock(ThreadService)
        service.storageService = GroovyMock(StorageService)

        when: "no items to upload"
        Result res = service.finishProcessingUploads([im1], null)

        then:
        1 * service.storageService.uploadAsync(null) >> new ResultGroup()
        res.status == ResultStatus.NO_CONTENT

        when: "has items to upload"
        res = service.finishProcessingUploads([im1], [uItem1])

        then:
        1 * service.storageService.uploadAsync([uItem1]) >> new ResultGroup()
        1 * service.threadService.delay(*_) >> { args -> args[2].call(); null; }
        1 * im1.delete()
        res.status == ResultStatus.NO_CONTENT

        when: "has upload errors"
        res = service.finishProcessingUploads([im1], [uItem1])

        then: "if error, media is not deleted"
        1 * service.storageService.uploadAsync([uItem1]) >>
            Result.createError([], ResultStatus.BAD_REQUEST).toGroup()
        res.status == ResultStatus.BAD_REQUEST
    }

    void "test processing element"() {
        given: "override credentials so we're not sending actual credentials to testing endpoint"
        String sid = TestUtils.randString()
        String authToken = TestUtils.randString()
        UploadItem sendVersion = TestUtils.buildUploadItem()
        UploadItem altVersion = TestUtils.buildUploadItem()

        int eBaseline = MediaElement.count()
        int vBaseline = MediaElementVersion.count()

        service.grailsApplication = Stub(GrailsApplication) {
            getFlatConfig() >> [
                "textup.apiKeys.twilio.sid": sid,
                "textup.apiKeys.twilio.authToken": authToken
            ]
        }
        IsIncomingMedia im1 = GroovyMock()
        MockedMethod process = MockedMethod.create(MediaPostProcessor, "process") {
            Result.createSuccess(Tuple.create(sendVersion, [altVersion]))
        }

        when: "is a private asset"
        Result res = service.processElement(im1)
        MediaElement.withSession { it.flush() }

        then:
        1 * im1.mimeType >> MediaType.IMAGE_JPEG.mimeType
        1 * im1.url >> "${TestConstants.TEST_HTTP_ENDPOINT}/basic-auth/${sid}/${authToken}"
        process.latestArgs[0] == MediaType.IMAGE_JPEG
        (1.._) * im1.isPublic >> false
        res.status == ResultStatus.OK
        res.payload.first.size() == 2
        sendVersion in res.payload.first
        altVersion in res.payload.first
        res.payload.second instanceof MediaElement
        res.payload.second.sendVersion.versionId == sendVersion.key
        res.payload.second.sendVersion.isPublic  == false
        res.payload.second.alternateVersions.size() == 1
        res.payload.second.alternateVersions[0].versionId == altVersion.key
        res.payload.second.alternateVersions[0].isPublic  == false
        MediaElement.count() == eBaseline + 1
        MediaElementVersion.count() == vBaseline + 2

        cleanup:
        process?.restore()
    }

    void "test processing overall"() {
        given: "override credentials so we're not sending actual credentials to testing endpoint"
        MediaElement el1 = TestUtils.buildMediaElement()
        UploadItem uItem1 = TestUtils.buildUploadItem()

        IsIncomingMedia im1 = GroovyMock() { asBoolean() >> true }
        MockedMethod processElement = MockedMethod.create(service, "processElement") {
            Result.createSuccess(Tuple.create([uItem1], el1))
        }
        MockedMethod finishProcessingUploads = MockedMethod.create(service, "finishProcessingUploads") {
            Result.void()
        }

        when:
        Result res = service.process([im1])

        then:
        1 * im1.validate() >> true
        processElement.latestArgs == [im1]
        finishProcessingUploads.latestArgs == [[im1], [uItem1]]
        res.status == ResultStatus.OK
        el1 in res.payload

        cleanup:
        processElement?.restore()
        finishProcessingUploads?.restore()
    }
}
