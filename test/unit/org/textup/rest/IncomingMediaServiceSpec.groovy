package org.textup.rest

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestFor
import grails.test.mixin.TestMixin
import grails.test.runtime.*
import java.util.concurrent.atomic.AtomicInteger
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.springframework.validation.Errors
import org.textup.*
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
        IOCUtils.metaClass."static".getMessageSource = { -> TestUtils.mockMessageSource() }
        service.resultFactory = TestUtils.getResultFactory(grailsApplication)
    }

    void "test finish processing uploads"() {
        given:
        service.storageService = Mock(StorageService)
        IsIncomingMedia iMedia = Mock(IsIncomingMedia)
        UploadItem uItem = TestUtils.buildUploadItem()

        when: "no items to upload"
        Result<Void> res = service.finishProcessingUploads([iMedia], null)

        then:
        1 * service.storageService.uploadAsync(*_) >> new ResultGroup()
        0 * iMedia._
        res.status == ResultStatus.NO_CONTENT

        when: "has items to upload"
        res = service.finishProcessingUploads([iMedia], [uItem])

        then:
        1 * service.storageService.uploadAsync(*_) >> new ResultGroup()
        1 * iMedia.delete()
        res.status == ResultStatus.NO_CONTENT

        when: "has upload errors"
        res = service.finishProcessingUploads([iMedia], [uItem])

        then: "if error, media is not deleted"
        1 * service.storageService.uploadAsync(*_) >>
            Result.createError(["hi"], ResultStatus.BAD_REQUEST).toGroup()
        0 * iMedia._
        res.status == ResultStatus.BAD_REQUEST
    }

    void "test processing element invalid inputs"() {
        given:
        IsIncomingMedia im1 = Mock(IsIncomingMedia)

        when: "incoming media is missing information"
        Result<Tuple<List<UploadItem>, MediaElement>> res = service.processElement(im1)

        then:
        1 * im1.validate() >> false
        (1.._) * im1.errors >> Mock(Errors)
        res.status == ResultStatus.UNPROCESSABLE_ENTITY

        when: "incoming media has invalid mime type"
        res = service.processElement(im1)

        then:
        1 * im1.validate() >> true
        (1.._) * im1.mimeType >> "invalid mime type"
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages[0] == "incomingMediaService.processElement.invalidMimeType"
    }

    void "test basic auth request error conditions"() {
        given: "override credentials so we're not sending actual credentials to testing endpoint"
        IsIncomingMedia im1 = Mock(IsIncomingMedia) {
            validate() >> true
            getMimeType() >> MediaType.IMAGE_JPEG.mimeType
        }
        String sid = TestUtils.randString()
        String authToken = TestUtils.randString()

        when: "basic auth is missing credentials"
        service.grailsApplication = Stub(GrailsApplication) {
            getFlatConfig() >> [
                "textup.apiKeys.twilio.sid": null,
                "textup.apiKeys.twilio.authToken": null
            ]
        }
        Result<Tuple<List<UploadItem>, MediaElement>> res = service.processElement(im1)

        then:
        1 * im1.url >> TestConstants.TEST_HTTP_ENDPOINT
        res.status == ResultStatus.INTERNAL_SERVER_ERROR

        when: "basic auth returns an error response code"
        service.grailsApplication.getFlatConfig() >> [
            "textup.apiKeys.twilio.sid": sid,
            "textup.apiKeys.twilio.authToken": authToken
        ]
        res = service.processElement(im1)

        then:
        1 * im1.url >> "${TestConstants.TEST_HTTP_ENDPOINT}/basic-auth/${sid}/invalid-password"
        res.status == ResultStatus.UNAUTHORIZED
        res.errorMessages[0] == "incomingMediaService.processElement.couldNotRetrieveMedia"
    }

    void "test processing element"() {
        given: "override credentials so we're not sending actual credentials to testing endpoint"
        String sid = TestUtils.randString()
        String authToken = TestUtils.randString()
        IsIncomingMedia im1 = Mock(IsIncomingMedia) {
            validate() >> true
            getMimeType() >> MediaType.IMAGE_JPEG.mimeType
            getUrl() >> "${TestConstants.TEST_HTTP_ENDPOINT}/basic-auth/${sid}/${authToken}"
        }
        service.grailsApplication = Stub(GrailsApplication) {
            getFlatConfig() >> [
                "textup.apiKeys.twilio.sid": sid,
                "textup.apiKeys.twilio.authToken": authToken
            ]
        }
        UploadItem sendVersion = TestUtils.buildUploadItem()
        UploadItem altVersion = TestUtils.buildUploadItem()
        MediaPostProcessor.metaClass."static".process = { MediaType type, byte[] data ->
            new Result(payload: Tuple.create(sendVersion, [altVersion]))
        }
        int eBaseline = MediaElement.count()
        int vBaseline = MediaElementVersion.count()

        when: "is a private asset"
        Result<Tuple<List<UploadItem>, MediaElement>> res = service.processElement(im1)
        MediaElement.withSession { it.flush() }

        then:
        (1.._) * im1.isPublic >> false
        res.status == ResultStatus.OK
        res.payload.first.size() == 2
        res.payload.first.every { it == sendVersion || it == altVersion }
        res.payload.second instanceof MediaElement
        res.payload.second.sendVersion.versionId == sendVersion.key
        res.payload.second.sendVersion.isPublic  == false
        res.payload.second.alternateVersions.size()       == 1
        res.payload.second.alternateVersions[0].versionId == altVersion.key
        res.payload.second.alternateVersions[0].isPublic  == false
        MediaElement.count() == eBaseline + 1
        MediaElementVersion.count() == vBaseline + 2
    }

    @DirtiesRuntime
    void "test processing overall"() {
        given: "override credentials so we're not sending actual credentials to testing endpoint"
        MediaElement e1 = TestUtils.buildMediaElement()
        UploadItem uItem = TestUtils.buildUploadItem()
        Collection<IsIncomingMedia> incomingMedia = []
        5.times { incomingMedia << Mock(IsIncomingMedia) }
        MockedMethod processElement = TestUtils.mock(service, "processElement")
            { new Result(payload: Tuple.create([uItem], e1)) }
        MockedMethod finishProcessingUploads = TestUtils.mock(service, "finishProcessingUploads")
            { new Result() }

        when: "is a public asset"
        ResultGroup<MediaElement> resGroup = service.process(incomingMedia)

        then:
        processElement.callCount == incomingMedia.size()
        finishProcessingUploads.callCount == 1
        finishProcessingUploads.callArguments.every { it[0] == incomingMedia }
        // in the processElement mock we return 1 UploadItem per call
        finishProcessingUploads.callArguments.every { it[1].size() == incomingMedia.size() }
        resGroup.successes.size() == incomingMedia.size()
        resGroup.anyFailures == false
        resGroup.payload.every { it == e1 }
    }
}
