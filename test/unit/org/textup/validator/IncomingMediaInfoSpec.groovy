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
}
