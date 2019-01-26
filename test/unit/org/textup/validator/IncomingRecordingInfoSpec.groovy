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
class IncomingRecordingInfoSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
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
