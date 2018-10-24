package org.textup

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestFor
import grails.test.mixin.TestMixin
import org.textup.util.*
import org.textup.validator.*
import spock.lang.Specification

@TestFor(OutgoingMediaService)
@Domain([MediaInfo, MediaElement, MediaElementVersion])
@TestMixin(HibernateTestMixin)
class OutgoingMediaServiceSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    MockedMethod unsignedLink
    MockedMethod signedLink

    def setup() {
        service.resultFactory = TestHelpers.getResultFactory(grailsApplication)
        unsignedLink = TestHelpers.mock(LinkUtils, 'unsignedLink')
        signedLink = TestHelpers.mock(LinkUtils, 'signedLink')
    }
    def cleanup() {
        unsignedLink.restore()
        signedLink.restore()
    }

    void "test sending media for text"() {
        given:
        service.textService = Mock(TextService)
        MediaInfo mInfo = new MediaInfo()
        (Constants.MAX_NUM_MEDIA_PER_MESSAGE * 2).times {
            MediaElement e1 = TestHelpers.buildMediaElement()
            mInfo.addToMediaElements(e1)
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
            MediaElement e1 = TestHelpers.buildMediaElement()
            mInfo.addToMediaElements(e1)
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
        Result<List<TempRecordReceipt>> res = service.send(null, null, null, null, null)

        then: "send as text"
        0 * service.callService.start(*_)
        1 * service.textService.send(*_) >> new Result()
        res.status == ResultStatus.OK

        when: "with call token"
        res = service.send(null, null, null, null, callToken)

        then: "send as call"
        1 * service.callService.start(*_) >> new Result()
        0 * service.textService.send(*_)
        res.status == ResultStatus.OK

        when: "has some errors"
        res = service.send(null, null, null, null, callToken)

        then: "return error"
        1 * service.callService.start(*_) >> new Result(status: ResultStatus.BAD_REQUEST)
        0 * service.textService.send(*_)
        res.status == ResultStatus.BAD_REQUEST
    }
}
