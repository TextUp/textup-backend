package org.textup.rest

import grails.plugins.rest.client.RestResponse
import grails.test.mixin.*
import grails.test.mixin.support.*
import javax.servlet.http.HttpServletRequest
import org.joda.time.*
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import org.textup.*
import org.textup.cache.*
import org.textup.media.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*
import spock.lang.*

@TestMixin(GrailsUnitTestMixin) // enables local use of validator classes
class VoicemailGreetingFunctionalSpec extends FunctionalSpec {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        doSetup()
        remote.exec({ mockedMethodsKey ->
            app.config[mockedMethodsKey] << MockedMethod.create(MediaPostProcessor, "process") {
                UploadItem uItem1 = TestUtils.buildUploadItem(MediaType.AUDIO_MP3)
                UploadItem uItem2 = TestUtils.buildUploadItem(MediaType.AUDIO_WEBM_OPUS)
                ctx.resultFactory.success(uItem1, [uItem2])
            }
            app.config[mockedMethodsKey] << MockedMethod.create(ctx.callService, "interrupt") {
                Result.void()
            }
            return
        }.curry(MOCKED_METHODS_CONFIG_KEY))
    }

    def cleanup() {
        doCleanup()
    }

    void "test recording and reviewing new voicemail greeting over the phone"() {
        given:
        String accountId = TestUtils.randString()
        String callId = TestUtils.randString()
        String fromNum = TestUtils.randPhoneNumberString()
        String phoneNum = remote.exec({ un ->
            Staff s1 = Staff.findByUsername(un)
            Phone p1 = IOCUtils.phoneCache.findPhone(s1.id, PhoneOwnershipType.INDIVIDUAL)
            OwnerPolicies.tryFindOrCreateForOwnerAndStaffId(p1.owner, s1.id)
                .logFail("VoicemailGreetingFunctionalSpec")
                .thenEnd { op1 ->
                    op1.schedule.manual = true
                    op1.schedule.manualIsAvailable = false
                }
            return p1.number.e164PhoneNumber
        }.curry(loggedInUsername))

        when: "recording a voicemail greeting over the phone"
        MultiValueMap<String,String> form = new LinkedMultiValueMap<>()
        form.set(TwilioUtils.ID_ACCOUNT, accountId)
        form.set(TwilioUtils.ID_CALL, callId)
        form.set(TwilioUtils.FROM, phoneNum)
        form.set(TwilioUtils.TO, fromNum)
        RestResponse response = rest.post("${baseUrl}/v1/public/records?${CallbackUtils.PARAM_HANDLE}=${CallResponse.VOICEMAIL_GREETING_RECORD}") {
            contentType("application/x-www-form-urlencoded")
            body(form)
        }

        then:
        response.status == ResultStatus.OK.intStatus
        response.xml != null
        response.xml.Record.@action.toString().contains(CallResponse.VOICEMAIL_GREETING_PROCESSING.toString())
        response.xml.Record.@recordingStatusCallback.toString().contains(CallResponse.VOICEMAIL_GREETING_PROCESSED.toString())

        when: "processing"
        // for VOICEMAIL_GREETING_PROCESSING is an anonymous call
        response = rest.post("${baseUrl}/v1/public/records?${CallbackUtils.PARAM_HANDLE}=${CallResponse.VOICEMAIL_GREETING_PROCESSING}") {
            contentType("application/x-www-form-urlencoded")
            body(form)
        }

        then:
        response.status == ResultStatus.OK.intStatus
        response.xml != null
        response.xml.Play[0].text() == CallTwiml.HOLD_MUSIC_URL
        response.xml.Play[1].@loop.toString().isInteger() // hold music loops

        when: "done processing"
        form.set(TwilioUtils.RECORDING_DURATION, "1234")
        form.set(TwilioUtils.RECORDING_URL, "http://www.example.com")
        form.set(TwilioUtils.ID_RECORDING, TestUtils.randString())
        // for VOICEMAIL_GREETING_PROCESSED from = phone and to = session
        response = rest.post("${baseUrl}/v1/public/records?${CallbackUtils.PARAM_HANDLE}=${CallResponse.VOICEMAIL_GREETING_PROCESSED}") {
            contentType("application/x-www-form-urlencoded")
            body(form)
        }

        then: "attempt to interrupt ongoing call"
        response.status == ResultStatus.OK.intStatus
        response.xml != null
        response.xml.text() == ""

        when: "playing just processed voicemail greeting"
        // for VOICEMAIL_GREETING_PLAY from = phone and to = session
        response = rest.post("${baseUrl}/v1/public/records?${CallbackUtils.PARAM_HANDLE}=${CallResponse.VOICEMAIL_GREETING_PLAY}") {
            contentType("application/x-www-form-urlencoded")
            body(form)
        }

        then:
        response.status == ResultStatus.OK.intStatus
        response.xml != null
        response.xml.Gather.@action.toString().contains(CallResponse.VOICEMAIL_GREETING_RECORD.toString())
        response.xml.Gather.Play[0].text() == remote.exec({ un ->
            Staff s1 = Staff.findByUsername(un)
            Phone p1 = IOCUtils.phoneCache.findPhone(s1.id, PhoneOwnershipType.INDIVIDUAL)
            return p1.voicemailGreetingUrl.toString()
        }.curry(loggedInUsername))
    }
}
