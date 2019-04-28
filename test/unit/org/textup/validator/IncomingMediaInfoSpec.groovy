package org.textup.validator

import grails.test.mixin.*
import grails.test.mixin.gorm.*
import grails.test.mixin.hibernate.*
import grails.test.mixin.support.*
import grails.test.runtime.*
import grails.validation.*
import org.joda.time.*
import org.textup.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import spock.lang.*

@TestMixin(GrailsUnitTestMixin)
@Unroll
class IncomingMediaInfoSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test validation for incoming media"() {
        given:
        String mediaId = TestUtils.randString()
        MockedMethod extractMediaIdFromUrl = MockedMethod.create(TwilioUtils, "extractMediaIdFromUrl") {
            mediaId
        }
        int index = TestUtils.randIntegerUpTo(88, true)
        String messageId = TestUtils.randString()
        TypeMap params = new TypeMap((TwilioUtils.ID_ACCOUNT): TestUtils.randString(),
            ("${TwilioUtils.MEDIA_CONTENT_TYPE_PREFIX}${index}".toString()): TestUtils.randString(),
            ("${TwilioUtils.MEDIA_URL_PREFIX}${index}".toString()): TestUtils.randUri())

        when: "empty"
        Result res = IncomingMediaInfo.tryCreate(null, null, 0)

        then:
        extractMediaIdFromUrl.callCount == 1
        res.status == ResultStatus.UNPROCESSABLE_ENTITY

        when: "invalid"
        res = IncomingMediaInfo.tryCreate(messageId, params, index)

        then:
        extractMediaIdFromUrl.callCount == 2
        extractMediaIdFromUrl.allArgs[1] == [params.string("${TwilioUtils.MEDIA_URL_PREFIX}${index}")]
        res.status == ResultStatus.CREATED
        res.payload.accountId == params.string(TwilioUtils.ID_ACCOUNT)
        res.payload.mediaId == mediaId
        res.payload.messageId == messageId
        res.payload.mimeType == params.string("${TwilioUtils.MEDIA_CONTENT_TYPE_PREFIX}${index}")
        res.payload.url == params.string("${TwilioUtils.MEDIA_URL_PREFIX}${index}")
        res.payload.isPublic == false // default is false

        cleanup:
        extractMediaIdFromUrl.restore()
    }

    void "test deleting for incoming media"() {
        given: "valid incoming media"
        ByteArrayOutputStream stdErr = TestUtils.captureAllStreamsReturnStdErr()
        MockedMethod extractMediaIdFromUrl = MockedMethod.create(TwilioUtils, "extractMediaIdFromUrl") {
            TestUtils.randString()
        }
        int index = TestUtils.randIntegerUpTo(88, true)
        String messageId = TestUtils.randString()
        TypeMap params = new TypeMap((TwilioUtils.ID_ACCOUNT): TestUtils.randString(),
            ("${TwilioUtils.MEDIA_CONTENT_TYPE_PREFIX}${index}".toString()): TestUtils.randString(),
            ("${TwilioUtils.MEDIA_URL_PREFIX}${index}".toString()): TestUtils.randUri())
        IncomingMediaInfo im1 = IncomingMediaInfo.tryCreate(messageId, params, index).payload
        assert im1.validate()

        when: "throws an exception"
        Result<Boolean> res = im1.delete()

        then: "gracefully handled"
        res.status == ResultStatus.INTERNAL_SERVER_ERROR
        stdErr.toString().contains("com.twilio.exception.AuthenticationException")

        cleanup:
        extractMediaIdFromUrl.restore()
        TestUtils.restoreAllStreams()
    }
}
