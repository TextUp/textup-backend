package org.textup

import grails.test.mixin.*
import grails.test.mixin.gorm.*
import grails.test.mixin.hibernate.*
import grails.test.mixin.support.*
import grails.test.runtime.*
import grails.validation.*
import org.joda.time.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*
import spock.lang.*

@Domain([CustomAccountDetails, MediaInfo, MediaElement, MediaElementVersion])
@TestFor(OutgoingMediaService)
@TestMixin(HibernateTestMixin)
class OutgoingMediaServiceSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    MockedMethod unsignedLink
    MockedMethod signedLink

    def setup() {
        TestUtils.standardMockSetup()
        unsignedLink = MockedMethod.create(LinkUtils, "unsignedLink")
        signedLink = MockedMethod.create(LinkUtils, "signedLink")
    }
    def cleanup() {
        unsignedLink.restore()
        signedLink.restore()
    }

    void "test sending media for text"() {
        given:
        String customAccountId = TestUtils.randString()

        service.textService = GroovyMock(TextService)
        MediaInfo mInfo = TestUtils.buildMediaInfo()
        int numBatches = 2
        (ValidationUtils.MAX_NUM_MEDIA_PER_MESSAGE * numBatches).times {
            MediaElement e1 = TestUtils.buildMediaElement()
            e1.sendVersion.type = MediaType.IMAGE_JPEG
            mInfo.addToMediaElements(e1)
        }

        when: "no media"
        service.trySendWithMediaForText(null, null, customAccountId, null, null, null)

        then: "send without media"
        1 * service.textService.send(_, _, _, customAccountId, []) >> Result.void()

        when: "with media"
        service.trySendWithMediaForText(null, null, customAccountId, null, mInfo, null)

        then: "send with media in batches"
        numBatches * service.textService.send(_, _, _, customAccountId,
            { it.size() == ValidationUtils.MAX_NUM_MEDIA_PER_MESSAGE }) >> Result.void()

        when: "filtering media by certain types"
        service.trySendWithMediaForText(null, null, customAccountId, null, mInfo, MediaType.AUDIO_TYPES)

        then:
        0 * service.textService._
    }

    void "test sending media for call"() {
        given:
        String customAccountId = TestUtils.randString()

        service.callService = GroovyMock(CallService)
        service.textService = GroovyMock(TextService)
        Token callToken = new Token(token: TestUtils.randString())
        MediaInfo onlyAudioMedia = TestUtils.buildMediaInfo()
        MediaInfo onlyImageMedia = TestUtils.buildMediaInfo()
        (ValidationUtils.MAX_NUM_MEDIA_PER_MESSAGE * 2).times {
            MediaElement el1 = TestUtils.buildMediaElement()
            el1.sendVersion.type = MediaType.AUDIO_WEBM_VORBIS
            onlyAudioMedia.addToMediaElements(el1)

            MediaElement el2 = TestUtils.buildMediaElement()
            el2.sendVersion.type = MediaType.IMAGE_JPEG
            onlyImageMedia.addToMediaElements(el2)
        }

        when: "no media"
        service.trySendWithMediaForCall(null, null, customAccountId, callToken, null)

        then: "only call"
        1 * service.callService.start(_, _, _, customAccountId) >> Result.void()
        0 * service.textService._

        when: "with images"
        service.trySendWithMediaForCall(null, null, customAccountId, callToken, onlyImageMedia)

        then: "send images via text message + message via call"
        1 * service.callService.start(_, _, _, customAccountId) >> Result.void()
        (1.._) * service.textService.send(_, _, _, customAccountId, _) >> Result.void()

        when: "with audio"
        service.trySendWithMediaForCall(null, null, customAccountId, callToken, onlyAudioMedia)

        then: "audio and message both send via call, only images sent via text"
        1 * service.callService.start(_, _, _, customAccountId) >> Result.void()
        0 * service.textService._
    }

    void "test sending with media overall"() {
        given:
        String customAccountId = TestUtils.randString()

        Token callToken = new Token(token: TestUtils.randString())
        TempRecordReceipt tempRpt1 = TestUtils.buildTempReceipt()

        service.callService = GroovyMock(CallService)
        service.textService = GroovyMock(TextService)

        when: "without call token"
        Result res = service.trySend(null, null, customAccountId, null, null, null)

        then: "send as text"
        0 * service.callService.start(*_)
        1 * service.textService.send(_, _, _, customAccountId, _) >> Result.createSuccess(tempRpt1)
        res.status == ResultStatus.OK
        res.payload == [tempRpt1]

        when: "with call token"
        res = service.trySend(null, null, customAccountId, null, null, callToken)

        then: "send as call"
        1 * service.callService.start(_, _, _, customAccountId) >> Result.createSuccess(tempRpt1)
        0 * service.textService.send(*_)
        res.status == ResultStatus.OK
        res.payload == [tempRpt1]

        when: "has some errors"
        res = service.trySend(null, null, customAccountId, null, null, callToken)

        then: "return error"
        1 * service.callService.start(_, _, _, customAccountId) >>
            Result.createError([], ResultStatus.BAD_REQUEST)
        0 * service.textService.send(*_)
        res.status == ResultStatus.BAD_REQUEST
    }
}
