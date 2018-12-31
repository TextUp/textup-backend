package org.textup.validator

import org.textup.test.*
import grails.test.mixin.support.GrailsUnitTestMixin
import grails.test.mixin.TestMixin
import org.textup.*
import org.textup.type.*
import org.textup.util.*
import spock.lang.*

@TestMixin(GrailsUnitTestMixin)
@Unroll
class IncomingMediaValidatorsSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    void "test validation for incoming media"() {
        when: "empty"
        IncomingMediaInfo media1 = new IncomingMediaInfo()

        then:
        media1.validate() == false
        media1.errors.errorCount > 0

        when: "invalid"
        media1.accountId = TestUtils.randString()
        media1.mimeType = MediaType.AUDIO_MP3.mimeType
        media1.url = "not a url"
        media1.messageId = TestUtils.randString()
        media1.mediaId = TestUtils.randString()
        media1.isPublic = true

        then:
        media1.validate() == false
        media1.errors.errorCount > 0

        when: "properly formatted"
        media1.url = "https://www.example.com"

        then:
        media1.validate() == true
        media1.errors.errorCount == 0
    }

    void "test deleting for incoming media"() {
        given: "valid incoming media"
        ByteArrayOutputStream stdErr = TestUtils.captureAllStreamsReturnStdErr()
        IncomingMediaInfo media1 = new IncomingMediaInfo(accountId: TestUtils.randString(),
            mimeType: MediaType.AUDIO_MP3.mimeType,
            url: "https://www.example.com",
            messageId: TestUtils.randString(),
            mediaId: TestUtils.randString(),
            isPublic: true)
        assert media1.validate()

        when: "throws an exception"
        Result<Boolean> res = media1.delete()

        then: "gracefully handled"
        res.status == ResultStatus.INTERNAL_SERVER_ERROR
        stdErr.toString().contains("com.twilio.exception.AuthenticationException")
        stdErr.toString().contains("SmsMediaDeleter.delete")

        cleanup:
        TestUtils.restoreAllStreams()
    }

    void "test validation for incoming recording"() {
        when: "empty"
        IncomingRecordingInfo recording1 = new IncomingRecordingInfo()

        then:
        recording1.validate() == false
        recording1.errors.errorCount > 0

        when: "invalid"
        recording1.accountId = TestUtils.randString()
        recording1.mimeType = MediaType.AUDIO_MP3.mimeType
        recording1.url = "not a url"
        recording1.mediaId = TestUtils.randString()
        recording1.isPublic = true

        then:
        recording1.validate() == false
        recording1.errors.errorCount > 0

        when: "properly formatted"
        recording1.url = "https://www.example.com"

        then:
        recording1.validate() == true
        recording1.errors.errorCount == 0
    }

    void "test deleting for incoming recording"() {
        given: "valid incoming media"
        ByteArrayOutputStream stdErr = TestUtils.captureAllStreamsReturnStdErr()
        IncomingRecordingInfo recording1 = new IncomingRecordingInfo(accountId: TestUtils.randString(),
            mimeType: MediaType.AUDIO_MP3.mimeType,
            url: "https://www.example.com",
            mediaId: TestUtils.randString(),
            isPublic: true)
        assert recording1.validate()

        when: "throws an exception"
        Result<Boolean> res = recording1.delete()

        then: "gracefully handled"
        res.status == ResultStatus.INTERNAL_SERVER_ERROR
        stdErr.toString().contains("com.twilio.exception.AuthenticationException")
        stdErr.toString().contains("CallRecordingDeleter.delete")

        cleanup:
        TestUtils.restoreAllStreams()
    }
}
