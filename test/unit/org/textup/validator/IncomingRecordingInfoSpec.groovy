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

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test validation for incoming recording"() {
        given:
        TypeMap params = new TypeMap((TwilioUtils.ID_ACCOUNT): TestUtils.randString(),
            (TwilioUtils.ID_RECORDING): TestUtils.randString(),
            (TwilioUtils.RECORDING_URL): TestUtils.randUrl())

        when: "empty"
        Result res = IncomingRecordingInfo.tryCreate(null)

        then:
        res.status == ResultStatus.UNPROCESSABLE_ENTITY

        when:
        res = IncomingRecordingInfo.tryCreate(params)

        then:
        res.status == ResultStatus.CREATED
        res.payload.accountId == params.string(TwilioUtils.ID_ACCOUNT)
        res.payload.mediaId == params.string(TwilioUtils.ID_RECORDING)
        res.payload.url == params.string(TwilioUtils.RECORDING_URL)
        res.payload.mimeType == MediaType.AUDIO_MP3.mimeType
    }

    void "test deleting for incoming recording"() {
        given: "valid incoming media"
        ByteArrayOutputStream stdErr = TestUtils.captureAllStreamsReturnStdErr()
        TypeMap params = new TypeMap((TwilioUtils.ID_ACCOUNT): TestUtils.randString(),
            (TwilioUtils.ID_RECORDING): TestUtils.randString(),
            (TwilioUtils.RECORDING_URL): TestUtils.randUrl())
        IncomingRecordingInfo ir1 = IncomingRecordingInfo.tryCreate(params).payload
        assert ir1.validate()

        when: "throws an exception"
        Result res = ir1.delete()

        then: "gracefully handled"
        res.status == ResultStatus.INTERNAL_SERVER_ERROR
        stdErr.toString().contains("com.twilio.exception.AuthenticationException")

        cleanup:
        TestUtils.restoreAllStreams()
    }
}
