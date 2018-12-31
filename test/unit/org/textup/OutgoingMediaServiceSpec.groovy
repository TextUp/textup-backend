package org.textup

import org.textup.test.*
import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestFor
import grails.test.mixin.TestMixin
import org.textup.util.*
import org.textup.type.*
import org.textup.validator.*
import spock.lang.Specification

@TestFor(OutgoingMediaService)
@Domain([CustomAccountDetails, MediaInfo, MediaElement, MediaElementVersion])
@TestMixin(HibernateTestMixin)
class OutgoingMediaServiceSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    MockedMethod unsignedLink
    MockedMethod signedLink

    def setup() {
        service.resultFactory = TestUtils.getResultFactory(grailsApplication)
        unsignedLink = TestUtils.mock(LinkUtils, 'unsignedLink')
        signedLink = TestUtils.mock(LinkUtils, 'signedLink')
    }
    def cleanup() {
        unsignedLink.restore()
        signedLink.restore()
    }

    void "test sending media for text"() {
        given:
        String customAccountId = TestUtils.randString()

        service.textService = Mock(TextService)
        MediaInfo mInfo = new MediaInfo()
        int numBatches = 2
        (Constants.MAX_NUM_MEDIA_PER_MESSAGE * numBatches).times {
            MediaElement e1 = TestUtils.buildMediaElement()
            e1.sendVersion.type = MediaType.IMAGE_JPEG
            mInfo.addToMediaElements(e1)
        }

        when: "no media"
        service.sendWithMediaForText(null, null, customAccountId, null, null, null)

        then: "send without media"
        1 * service.textService.send(_, _, _, customAccountId, []) >> new Result()

        when: "with media"
        service.sendWithMediaForText(null, null, customAccountId, null, mInfo, null)

        then: "send with media in batches"
        numBatches * service.textService.send(_, _, _, customAccountId,
            { it.size() == Constants.MAX_NUM_MEDIA_PER_MESSAGE }) >> new Result()

        when: "filtering media by certain types"
        service.sendWithMediaForText(null, null, customAccountId, null, mInfo, MediaType.AUDIO_TYPES)

        then:
        0 * service.textService._
    }

    void "test sending media for call"() {
        given:
        String customAccountId = TestUtils.randString()

        service.callService = Mock(CallService)
        service.textService = Mock(TextService)
        Token callToken = new Token(token: "valid token value")
        MediaInfo onlyAudioMedia = new MediaInfo()
        MediaInfo onlyImageMedia = new MediaInfo()
        (Constants.MAX_NUM_MEDIA_PER_MESSAGE * 2).times {
            MediaElement el1 = TestUtils.buildMediaElement()
            el1.sendVersion.type = MediaType.AUDIO_WEBM_VORBIS
            onlyAudioMedia.addToMediaElements(el1)

            MediaElement el2 = TestUtils.buildMediaElement()
            el2.sendVersion.type = MediaType.IMAGE_JPEG
            onlyImageMedia.addToMediaElements(el2)
        }

        when: "no media"
        service.sendWithMediaForCall(null, null, customAccountId, callToken, null)

        then: "only call"
        1 * service.callService.start(_, _, _, customAccountId) >> new Result()
        0 * service.textService._

        when: "with images"
        service.sendWithMediaForCall(null, null, customAccountId, callToken, onlyImageMedia)

        then: "send images via text message + message via call"
        1 * service.callService.start(_, _, _, customAccountId) >> new Result()
        (1.._) * service.textService.send(_, _, _, customAccountId, _) >> new Result()

        when: "with audio"
        service.sendWithMediaForCall(null, null, customAccountId, callToken, onlyAudioMedia)

        then: "audio and message both send via call, only images sent via text"
        1 * service.callService.start(_, _, _, customAccountId) >> new Result()
        0 * service.textService._
    }

    void "test sending with media overall"() {
        given:
        String customAccountId = TestUtils.randString()

        service.callService = Mock(CallService)
        service.textService = Mock(TextService)
        Token callToken = new Token(token: "valid token value")

        when: "without call token"
        Result<List<TempRecordReceipt>> res = service.send(null, null, customAccountId, null, null, null)

        then: "send as text"
        0 * service.callService.start(*_)
        1 * service.textService.send(_, _, _, customAccountId, _) >> new Result()
        res.status == ResultStatus.OK

        when: "with call token"
        res = service.send(null, null, customAccountId, null, null, callToken)

        then: "send as call"
        1 * service.callService.start(_, _, _, customAccountId) >> new Result()
        0 * service.textService.send(*_)
        res.status == ResultStatus.OK

        when: "has some errors"
        res = service.send(null, null, customAccountId, null, null, callToken)

        then: "return error"
        1 * service.callService.start(_, _, _, customAccountId) >>
            new Result(status: ResultStatus.BAD_REQUEST)
        0 * service.textService.send(*_)
        res.status == ResultStatus.BAD_REQUEST
    }
}
